package com.itm.servicedesk

import com.itm.servicedesk.domain.builder.StaffPageQueryBuilder
import com.itm.servicedesk.domain.model.DomainException
import com.itm.servicedesk.domain.model.UserRole
import com.itm.servicedesk.domain.repository.CreateUserCommand
import com.itm.servicedesk.repository.ExposedRevokedTokenRepository
import com.itm.servicedesk.repository.ExposedUserRepository
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExposedRepositoriesTest : IntegrationTestBase() {
    @Test
    fun `ExposedUserRepository handles create findById findByEmail SQLState 23505 and staff pagination`() {
        val dbConnection = initTestDatabase()
        dbConnection.use { conn ->
            cleanAuthTables(conn)
            val repo = ExposedUserRepository(conn.database, "America/Bogota")
            val now = OffsetDateTime.now(ZoneId.of("America/Bogota"))

            val solicitante =
                repo.create(
                    CreateUserCommand(
                        id = UUID.randomUUID(),
                        fullName = "Laura Solicitante",
                        documentNumber = "1020304050",
                        email = "laura@itm.edu.co",
                        phone = "3001234567",
                        passwordHash = "\$2a\$12\$hash1",
                        role = UserRole.SOLICITANTE,
                        createdAt = now.minusMinutes(10),
                        updatedAt = now.minusMinutes(10),
                    ),
                )

            val agent =
                repo.create(
                    CreateUserCommand(
                        id = UUID.randomUUID(),
                        fullName = "Carlos Agente",
                        documentNumber = "71829304",
                        email = "carlos@itm.edu.co",
                        phone = "3119876543",
                        passwordHash = "\$2a\$12\$hash2",
                        role = UserRole.PERSONAL_ATENCION,
                        createdAt = now.minusMinutes(5),
                        updatedAt = now.minusMinutes(5),
                    ),
                )

            val coordinator =
                repo.create(
                    CreateUserCommand(
                        id = UUID.randomUUID(),
                        fullName = "Ana Coordinadora",
                        documentNumber = "71829305",
                        email = "ana@itm.edu.co",
                        phone = "3119876544",
                        passwordHash = "\$2a\$12\$hash3",
                        role = UserRole.COORDINADOR,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )

            assertEquals(solicitante.id, repo.findById(solicitante.id)?.id)
            assertNull(repo.findById(UUID.randomUUID()))
            assertEquals(agent.id, repo.findByEmail("  CARLOS@ITM.EDU.CO ")?.id)
            assertTrue(repo.existsByEmailOrDocument("laura@itm.edu.co", "00000"))
            assertTrue(repo.existsByEmailOrDocument("other@itm.edu.co", "71829304"))
            assertFalse(repo.existsByEmailOrDocument("missing@itm.edu.co", "9999999"))

            assertFailsWith<DomainException.ConflictException> {
                repo.create(
                    CreateUserCommand(
                        id = UUID.randomUUID(),
                        fullName = "Duplicado Email",
                        documentNumber = "99887766",
                        email = "laura@itm.edu.co",
                        phone = "3001112233",
                        passwordHash = "\$2a\$12\$hash4",
                        role = UserRole.SOLICITANTE,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }

            assertFailsWith<DomainException.ConflictException> {
                repo.create(
                    CreateUserCommand(
                        id = UUID.randomUUID(),
                        fullName = "Duplicado Doc",
                        documentNumber = "71829304",
                        email = "nuevo@itm.edu.co",
                        phone = "3001112244",
                        passwordHash = "\$2a\$12\$hash5",
                        role = UserRole.PERSONAL_ATENCION,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }

            val allStaff = repo.findStaffPaged(StaffPageQueryBuilder().build())
            assertEquals(2L, allStaff.totalItems)
            assertEquals(1, allStaff.totalPages)
            assertEquals(listOf(coordinator.id, agent.id), allStaff.items.map { it.id })

            val onlyAgents =
                repo.findStaffPaged(
                    StaffPageQueryBuilder().role(UserRole.PERSONAL_ATENCION).build(),
                )
            assertEquals(1L, onlyAgents.totalItems)
            assertEquals(agent.id, onlyAgents.items.single().id)
        }
    }

    @Test
    fun `ExposedRevokedTokenRepository revokes idempotently and purges expired tokens`() {
        val dbConnection = initTestDatabase()
        dbConnection.use { conn ->
            cleanAuthTables(conn)
            val repo = ExposedRevokedTokenRepository(conn.database)
            val now = OffsetDateTime.now(ZoneId.of("America/Bogota"))

            val expiredJti = UUID.randomUUID()
            val activeJti = UUID.randomUUID()

            repo.revokeAndPurgeExpired(expiredJti, now.minusMinutes(5), now.minusMinutes(10))
            assertTrue(repo.isRevoked(expiredJti))

            repo.revokeAndPurgeExpired(activeJti, now.plusMinutes(50), now)
            repo.revokeAndPurgeExpired(activeJti, now.plusMinutes(50), now)

            assertFalse(repo.isRevoked(expiredJti))
            assertTrue(repo.isRevoked(activeJti))
            assertNotNull(conn.database)
        }
    }
}
