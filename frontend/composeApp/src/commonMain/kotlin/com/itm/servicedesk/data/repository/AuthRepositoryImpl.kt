package com.itm.servicedesk.data.repository

import com.itm.servicedesk.core.storage.SessionEvent
import com.itm.servicedesk.core.storage.SessionEventBus
import com.itm.servicedesk.core.storage.SessionStorage
import com.itm.servicedesk.data.mapper.AuthMapper
import com.itm.servicedesk.data.mapper.ErrorMapper
import com.itm.servicedesk.data.remote.ApiHttpException
import com.itm.servicedesk.data.remote.AuthRemoteDataSource
import com.itm.servicedesk.domain.model.AppResult
import com.itm.servicedesk.domain.model.AuthSession
import com.itm.servicedesk.domain.model.DomainError
import com.itm.servicedesk.domain.model.LoginCommand
import com.itm.servicedesk.domain.model.RegisterCommand
import com.itm.servicedesk.domain.model.UserProfile
import com.itm.servicedesk.domain.repository.AuthRepository
import kotlinx.coroutines.CancellationException

class AuthRepositoryImpl(
    private val remoteDataSource: AuthRemoteDataSource,
    private val sessionStorage: SessionStorage,
    private val sessionEventBus: SessionEventBus,
    private val authMapper: AuthMapper = AuthMapper(),
    private val errorMapper: ErrorMapper = ErrorMapper(),
) : AuthRepository {
    override suspend fun register(command: RegisterCommand): AppResult<AuthSession> =
        try {
            val dto = remoteDataSource.register(authMapper.toRegisterRequestDto(command))
            val session = authMapper.toDomain(dto)
            sessionStorage.saveToken(session.accessToken)
            AppResult.Success(session)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiHttpException) {
            AppResult.Failure(errorMapper.fromHttpResponse(e.statusCode, e.responseBody))
        } catch (e: Throwable) {
            AppResult.Failure(errorMapper.fromThrowable(e))
        }

    override suspend fun login(command: LoginCommand): AppResult<AuthSession> =
        try {
            val dto = remoteDataSource.login(authMapper.toLoginRequestDto(command))
            val session = authMapper.toDomain(dto)
            sessionStorage.saveToken(session.accessToken)
            AppResult.Success(session)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiHttpException) {
            val mapped = errorMapper.fromHttpResponse(e.statusCode, e.responseBody)
            val loginError =
                if (mapped is DomainError.Unauthorized && e.responseBody.isNullOrBlank()) {
                    DomainError.Unauthorized("Credenciales inválidas.")
                } else {
                    mapped
                }
            AppResult.Failure(loginError)
        } catch (e: Throwable) {
            AppResult.Failure(errorMapper.fromThrowable(e))
        }

    override suspend fun getCurrentUser(): AppResult<UserProfile> =
        try {
            val dto = remoteDataSource.getCurrentUser()
            AppResult.Success(authMapper.toDomain(dto))
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiHttpException) {
            AppResult.Failure(errorMapper.fromHttpResponse(e.statusCode, e.responseBody))
        } catch (e: Throwable) {
            AppResult.Failure(errorMapper.fromThrowable(e))
        }

    override suspend fun logout(): AppResult<Unit> =
        try {
            remoteDataSource.logout()
            clearLocalSessionAndNotify()
            AppResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiHttpException) {
            if (e.statusCode == HTTP_UNAUTHORIZED) {
                clearLocalSessionAndNotify()
                AppResult.Success(Unit)
            } else {
                AppResult.Failure(errorMapper.fromHttpResponse(e.statusCode, e.responseBody))
            }
        } catch (e: Throwable) {
            AppResult.Failure(errorMapper.fromThrowable(e))
        }

    private suspend fun clearLocalSessionAndNotify() {
        sessionStorage.clearToken()
        sessionEventBus.emit(SessionEvent.LoggedOut)
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
    }
}
