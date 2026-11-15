package com.itm.servicedesk

import at.favre.lib.crypto.bcrypt.BCrypt
import com.itm.servicedesk.domain.model.UserRole
import com.itm.servicedesk.repository.ExposedUserSeedRepository
import com.itm.servicedesk.repository.UsersTable
import com.itm.servicedesk.service.CoordinatorSeedService
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CoordinatorSeedServiceTest : IntegrationTestBase() {
    @Test
    fun `seedIfAbsent creates single COORDINADOR with BCrypt cost 12 and is idempotent`() {
        val dbConnection = initTestDatabase()
        dbConnection.use { conn ->
            cleanUsersTable(conn)
            val appConfig = buildTestAppConfig()
            val seedRepo = ExposedUserSeedRepository(conn.database)
            val seedService = CoordinatorSeedService(seedRepo, appConfig.server.timezone)

            val firstCreated = seedService.seedIfAbsent(appConfig.seedCoordinator)
            assertTrue(firstCreated)

            val secondCreated = seedService.seedIfAbsent(appConfig.seedCoordinator)
            assertFalse(secondCreated)

            val thirdCreated = seedService.seedIfAbsent(appConfig.seedCoordinator)
            assertFalse(thirdCreated)

            val rows =
                transaction(conn.database) {
                    UsersTable
                        .select(
                            UsersTable.id,
                            UsersTable.fullName,
                            UsersTable.documentNumber,
                            UsersTable.email,
                            UsersTable.phone,
                            UsersTable.passwordHash,
                            UsersTable.role,
                            UsersTable.active,
                        ).where { UsersTable.role eq UserRole.COORDINADOR.name }
                        .toList()
                }

            assertEquals(1, rows.size)
            val row = rows.single()
            assertEquals("Coordinador General ITM", row[UsersTable.fullName])
            assertEquals("1000000001", row[UsersTable.documentNumber])
            assertEquals("coordinador@itm.edu.co", row[UsersTable.email])
            assertEquals("3000000001", row[UsersTable.phone])
            assertEquals(UserRole.COORDINADOR.name, row[UsersTable.role])
            assertTrue(row[UsersTable.active])

            val storedHash = row[UsersTable.passwordHash]
            assertNotEquals(appConfig.seedCoordinator.rawPassword, storedHash)
            assertTrue(storedHash.startsWith("$2a$12$"))

            val verifyResult =
                BCrypt.verifyer().verify(
                    appConfig.seedCoordinator.rawPassword.toCharArray(),
                    storedHash,
                )
            assertTrue(verifyResult.verified)
        }
    }
}
