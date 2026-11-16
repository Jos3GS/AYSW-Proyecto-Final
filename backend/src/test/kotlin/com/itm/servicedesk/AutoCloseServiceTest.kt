package com.itm.servicedesk

import com.itm.servicedesk.domain.model.HistoryAction
import com.itm.servicedesk.domain.model.HistoryActorRole
import com.itm.servicedesk.domain.model.RequestCategory
import com.itm.servicedesk.domain.model.RequestPriority
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.domain.model.UserRole
import com.itm.servicedesk.repository.ExposedAttentionRepository
import com.itm.servicedesk.repository.RequestHistoryTable
import com.itm.servicedesk.repository.ServiceRequestsTable
import com.itm.servicedesk.repository.UsersTable
import com.itm.servicedesk.service.AutoCloseService
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AutoCloseServiceTest : IntegrationTestBase() {
    private val offset = ZoneOffset.of("-05:00")

    @Test
    fun `closeEligibleRequests closes only RESUELTA requests at or past 24h deterministically with SISTEMA history`() {
        val dbConn = initTestDatabase()
        cleanAuthTables(dbConn)

        try {
            val baseTime = OffsetDateTime.of(2026, 11, 15, 8, 0, 0, 0, offset)
            val fakeClock = FakeTimeProvider(baseTime)

            val requesterId = UUID.randomUUID()
            val agentId = UUID.randomUUID()
            val exact24hId = UUID.randomUUID()
            val under24hId = UUID.randomUUID()
            val inReviewId = UUID.randomUUID()

            transaction(dbConn.database) {
                UsersTable.insert { row ->
                    row[id] = requesterId
                    row[fullName] = "Solicitante AutoClose"
                    row[documentNumber] = "1001001001"
                    row[email] = "sol.autoclose@itm.edu.co"
                    row[phone] = "3001112233"
                    row[passwordHash] = "hash"
                    row[role] = UserRole.SOLICITANTE.name
                    row[active] = true
                    row[createdAt] = baseTime
                    row[updatedAt] = baseTime
                }
                UsersTable.insert { row ->
                    row[id] = agentId
                    row[fullName] = "Agente AutoClose"
                    row[documentNumber] = "1001001002"
                    row[email] = "agente.autoclose@itm.edu.co"
                    row[phone] = "3001112244"
                    row[passwordHash] = "hash"
                    row[role] = UserRole.PERSONAL_ATENCION.name
                    row[active] = true
                    row[createdAt] = baseTime
                    row[updatedAt] = baseTime
                }

                ServiceRequestsTable.insert { row ->
                    row[id] = exact24hId
                    row[code] = "SOL-2026-00101"
                    row[ServiceRequestsTable.requesterId] = requesterId
                    row[category] = RequestCategory.TRAMITE.name
                    row[subject] = "Resuelta lista para cierre"
                    row[description] = "Solicitud resuelta exactamente en baseTime para prueba de 24h."
                    row[status] = RequestStatus.RESUELTA.name
                    row[assignedTo] = agentId
                    row[priority] = RequestPriority.ALTA.name
                    row[firstAssignedAt] = baseTime.minusHours(2)
                    row[assignedAt] = baseTime.minusHours(2)
                    row[dueAt] = baseTime.plusHours(22)
                    row[responseText] = "Respuesta definitiva emitida correctamente."
                    row[respondedAt] = baseTime
                    row[respondedWithinSla] = true
                    row[createdAt] = baseTime.minusHours(3)
                    row[updatedAt] = baseTime
                }

                ServiceRequestsTable.insert { row ->
                    row[id] = under24hId
                    row[code] = "SOL-2026-00102"
                    row[ServiceRequestsTable.requesterId] = requesterId
                    row[category] = RequestCategory.ORIENTACION.name
                    row[subject] = "Resuelta reciente"
                    row[description] = "Solicitud resuelta 2 horas después que aún no cumple 24h."
                    row[status] = RequestStatus.RESUELTA.name
                    row[assignedTo] = agentId
                    row[priority] = RequestPriority.MEDIA.name
                    row[firstAssignedAt] = baseTime
                    row[assignedAt] = baseTime
                    row[dueAt] = baseTime.plusHours(72)
                    row[responseText] = "Respuesta reciente emitida para seguimiento."
                    row[respondedAt] = baseTime.plusHours(2)
                    row[respondedWithinSla] = true
                    row[createdAt] = baseTime.minusHours(1)
                    row[updatedAt] = baseTime.plusHours(2)
                }

                ServiceRequestsTable.insert { row ->
                    row[id] = inReviewId
                    row[code] = "SOL-2026-00103"
                    row[ServiceRequestsTable.requesterId] = requesterId
                    row[category] = RequestCategory.ATENCION_ADMINISTRATIVA.name
                    row[subject] = "En revisión todavía"
                    row[description] = "Solicitud en revisión que nunca debe cerrarse automáticamente."
                    row[status] = RequestStatus.EN_REVISION.name
                    row[assignedTo] = agentId
                    row[priority] = RequestPriority.BAJA.name
                    row[firstAssignedAt] = baseTime
                    row[assignedAt] = baseTime
                    row[dueAt] = baseTime.plusHours(120)
                    row[createdAt] = baseTime.minusHours(1)
                    row[updatedAt] = baseTime
                }
            }

            val repo = ExposedAttentionRepository(dbConn.database)
            val service = AutoCloseService(attentionRepository = repo, timeProvider = fakeClock)

            // 23h 59m 59s after baseTime -> 0 closed
            fakeClock.setNow(baseTime.plusHours(24).minusSeconds(1))
            assertEquals(0, service.closeEligibleRequests())

            // Exactly +24h -> closes only exact24hId
            fakeClock.setNow(baseTime.plusHours(24))
            assertEquals(1, service.closeEligibleRequests())

            // Idempotent re-run at same timestamp -> 0 closed
            assertEquals(0, service.closeEligibleRequests())

            transaction(dbConn.database) {
                val closedRow =
                    ServiceRequestsTable
                        .select(ServiceRequestsTable.status, ServiceRequestsTable.closedAt)
                        .where { ServiceRequestsTable.id eq exact24hId }
                        .single()
                assertEquals(RequestStatus.CERRADA.name, closedRow[ServiceRequestsTable.status])
                assertNotNull(closedRow[ServiceRequestsTable.closedAt])

                val underRow =
                    ServiceRequestsTable
                        .select(ServiceRequestsTable.status, ServiceRequestsTable.closedAt)
                        .where { ServiceRequestsTable.id eq under24hId }
                        .single()
                assertEquals(RequestStatus.RESUELTA.name, underRow[ServiceRequestsTable.status])
                assertNull(underRow[ServiceRequestsTable.closedAt])

                val reviewRow =
                    ServiceRequestsTable
                        .select(ServiceRequestsTable.status)
                        .where { ServiceRequestsTable.id eq inReviewId }
                        .single()
                assertEquals(RequestStatus.EN_REVISION.name, reviewRow[ServiceRequestsTable.status])

                val historyRows =
                    RequestHistoryTable
                        .select(
                            RequestHistoryTable.actorId,
                            RequestHistoryTable.actorRole,
                            RequestHistoryTable.action,
                            RequestHistoryTable.previousState,
                            RequestHistoryTable.newState,
                            RequestHistoryTable.comment,
                        ).where {
                            (RequestHistoryTable.requestId eq exact24hId) and
                                (RequestHistoryTable.action eq HistoryAction.CIERRE_AUTOMATICO.name)
                        }.toList()

                assertEquals(1, historyRows.size)
                val entry = historyRows.single()
                assertNull(entry[RequestHistoryTable.actorId])
                assertEquals(HistoryActorRole.SISTEMA.name, entry[RequestHistoryTable.actorRole])
                assertEquals(RequestStatus.RESUELTA.name, entry[RequestHistoryTable.previousState])
                assertEquals(RequestStatus.CERRADA.name, entry[RequestHistoryTable.newState])
                assertEquals("Cierre automático", entry[RequestHistoryTable.comment])
            }
        } finally {
            dbConn.close()
        }
    }
}
