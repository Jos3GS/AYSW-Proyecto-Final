package com.itm.servicedesk.testing

import com.itm.servicedesk.core.storage.SessionStorage
import com.itm.servicedesk.domain.model.AppResult
import com.itm.servicedesk.domain.model.AuthSession
import com.itm.servicedesk.domain.model.CreateStaffCommand
import com.itm.servicedesk.domain.model.LoginCommand
import com.itm.servicedesk.domain.model.RegisterCommand
import com.itm.servicedesk.domain.model.StaffMember
import com.itm.servicedesk.domain.model.StaffPage
import com.itm.servicedesk.domain.model.UserProfile
import com.itm.servicedesk.domain.model.UserRole
import com.itm.servicedesk.domain.repository.AuthRepository
import com.itm.servicedesk.domain.repository.StaffRepository
import kotlin.time.Instant

class InMemoryTestSessionStorage(
    initialToken: String? = null,
) : SessionStorage {
    private var token: String? = initialToken

    override suspend fun saveToken(token: String) {
        this.token = token
    }

    override suspend fun getToken(): String? = token

    override suspend fun clearToken() {
        token = null
    }
}

class FakeAuthRepository(
    var registerHandler: suspend (RegisterCommand) -> AppResult<AuthSession> = {
        AppResult.Success(AuthTestFixtures.authSession())
    },
    var loginHandler: suspend (LoginCommand) -> AppResult<AuthSession> = {
        AppResult.Success(AuthTestFixtures.authSession())
    },
    var getCurrentUserHandler: suspend () -> AppResult<UserProfile> = {
        AppResult.Success(AuthTestFixtures.userProfile())
    },
    var logoutHandler: suspend () -> AppResult<Unit> = { AppResult.Success(Unit) },
) : AuthRepository {
    var registerCalls: Int = 0
        private set
    var loginCalls: Int = 0
        private set
    var getCurrentUserCalls: Int = 0
        private set
    var logoutCalls: Int = 0
        private set

    override suspend fun register(command: RegisterCommand): AppResult<AuthSession> {
        registerCalls += 1
        return registerHandler(command)
    }

    override suspend fun login(command: LoginCommand): AppResult<AuthSession> {
        loginCalls += 1
        return loginHandler(command)
    }

    override suspend fun getCurrentUser(): AppResult<UserProfile> {
        getCurrentUserCalls += 1
        return getCurrentUserHandler()
    }

    override suspend fun logout(): AppResult<Unit> {
        logoutCalls += 1
        return logoutHandler()
    }
}

class FakeStaffRepository(
    var listStaffHandler: suspend (Int, Int, UserRole?) -> AppResult<StaffPage> = { page, size, _ ->
        AppResult.Success(AuthTestFixtures.staffPage(page = page, size = size))
    },
    var createStaffHandler: suspend (CreateStaffCommand) -> AppResult<UserProfile> = {
        AppResult.Success(AuthTestFixtures.userProfile(role = it.role, fullName = it.fullName))
    },
) : StaffRepository {
    val listCalls = mutableListOf<Triple<Int, Int, UserRole?>>()
    val createCalls = mutableListOf<CreateStaffCommand>()

    override suspend fun listStaff(
        page: Int,
        size: Int,
        roleFilter: UserRole?,
    ): AppResult<StaffPage> {
        listCalls.add(Triple(page, size, roleFilter))
        return listStaffHandler(page, size, roleFilter)
    }

    override suspend fun createStaff(command: CreateStaffCommand): AppResult<UserProfile> {
        createCalls.add(command)
        return createStaffHandler(command)
    }
}

object AuthTestFixtures {
    val sampleInstant: Instant = Instant.parse("2026-11-14T22:15:00Z")
    val sampleExpiresAt: Instant = Instant.parse("2026-11-14T23:15:00Z")

    fun userProfile(
        id: String = "550e8400-e29b-41d4-a716-446655440000",
        fullName: String = "Laura Gómez Pérez",
        documentNumber: String = "1020304050",
        email: String = "laura.gomez@correo.itm.edu.co",
        phone: String = "3001234567",
        role: UserRole = UserRole.SOLICITANTE,
        createdAt: Instant = sampleInstant,
    ): UserProfile =
        UserProfile(
            id = id,
            fullName = fullName,
            documentNumber = documentNumber,
            email = email,
            phone = phone,
            role = role,
            createdAt = createdAt,
        )

    fun authSession(
        token: String = "jwt-token-secret-123",
        user: UserProfile = userProfile(),
    ): AuthSession =
        AuthSession(
            accessToken = token,
            expiresInSeconds = 3600L,
            expiresAt = sampleExpiresAt,
            user = user,
        )

    fun staffMember(
        id: String = "a1b2c3d4-e5f6-47a8-b9c0-112233445566",
        fullName: String = "Carlos Andrés Mejía",
        email: String = "carlos.mejia@itm.edu.co",
        role: UserRole = UserRole.PERSONAL_ATENCION,
        createdAt: Instant = sampleInstant,
    ): StaffMember =
        StaffMember(
            id = id,
            fullName = fullName,
            email = email,
            role = role,
            createdAt = createdAt,
        )

    fun staffPage(
        items: List<StaffMember> = listOf(staffMember()),
        page: Int = 1,
        size: Int = 20,
        totalItems: Long = items.size.toLong(),
        totalPages: Int = 1,
    ): StaffPage =
        StaffPage(
            items = items,
            page = page,
            size = size,
            totalItems = totalItems,
            totalPages = totalPages,
        )

    fun authResponseJson(
        token: String = "jwt-token-secret-123",
        role: String = "SOLICITANTE",
    ): String =
        """
        {
          "accessToken": "$token",
          "tokenType": "Bearer",
          "expiresInSeconds": 3600,
          "expiresAt": "2026-11-14T18:15:00-05:00",
          "user": ${userProfileJson(role = role)}
        }
        """.trimIndent()

    fun userProfileJson(
        id: String = "550e8400-e29b-41d4-a716-446655440000",
        fullName: String = "Laura Gómez Pérez",
        role: String = "SOLICITANTE",
    ): String =
        """
        {
          "id": "$id",
          "fullName": "$fullName",
          "documentNumber": "1020304050",
          "email": "laura.gomez@correo.itm.edu.co",
          "phone": "3001234567",
          "role": "$role",
          "createdAt": "2026-11-14T17:15:00-05:00"
        }
        """.trimIndent()

    fun pagedStaffJson(
        page: Int = 1,
        size: Int = 20,
        totalItems: Long = 1,
        totalPages: Int = 1,
    ): String =
        """
        {
          "items": [
            {
              "id": "a1b2c3d4-e5f6-47a8-b9c0-112233445566",
              "fullName": "Carlos Andrés Mejía",
              "email": "carlos.mejia@itm.edu.co",
              "role": "PERSONAL_ATENCION",
              "createdAt": "2026-11-14T17:15:00-05:00"
            }
          ],
          "page": $page,
          "size": $size,
          "totalItems": $totalItems,
          "totalPages": $totalPages
        }
        """.trimIndent()

    fun apiErrorJson(
        status: Int,
        error: String,
        message: String,
        path: String = "/api/v1/auth/login",
        detailsJson: String = "[]",
    ): String =
        """
        {
          "timestamp": "2026-11-14T17:15:00-05:00",
          "status": $status,
          "error": "$error",
          "message": "$message",
          "path": "$path",
          "details": $detailsJson
        }
        """.trimIndent()
}
