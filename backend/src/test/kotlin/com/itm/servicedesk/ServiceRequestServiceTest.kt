package com.itm.servicedesk

import com.itm.servicedesk.domain.builder.MyRequestsQuery
import com.itm.servicedesk.domain.builder.MyRequestsQueryBuilder
import com.itm.servicedesk.domain.builder.ServiceRequestBuilder
import com.itm.servicedesk.domain.model.AuthenticatedIdentity
import com.itm.servicedesk.domain.model.DomainException
import com.itm.servicedesk.domain.model.PagedResult
import com.itm.servicedesk.domain.model.RequestCategory
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.domain.model.ServiceRequest
import com.itm.servicedesk.domain.model.ServiceRequestDetail
import com.itm.servicedesk.domain.model.ServiceRequestSummary
import com.itm.servicedesk.domain.model.UserRole
import com.itm.servicedesk.domain.repository.ServiceRequestRepository
import com.itm.servicedesk.domain.state.StateTransition
import com.itm.servicedesk.service.ServiceRequestService
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ServiceRequestServiceTest {
    private val now = OffsetDateTime.now(ZoneOffset.of("-05:00"))
    private val ownerId = UUID.randomUUID()
    private val otherId = UUID.randomUUID()

    private val ownerIdentity =
        AuthenticatedIdentity(
            userId = ownerId,
            email = "owner@itm.edu.co",
            role = UserRole.SOLICITANTE,
            jti = UUID.randomUUID(),
            expiresAt = now.plusHours(1),
        )

    private val coordinatorIdentity =
        AuthenticatedIdentity(
            userId = UUID.randomUUID(),
            email = "coord@itm.edu.co",
            role = UserRole.COORDINADOR,
            jti = UUID.randomUUID(),
            expiresAt = now.plusHours(1),
        )

    private fun fakeRepository(
        foundRequest: ServiceRequest? = null,
        cancelReturnsNull: Boolean = false,
    ): ServiceRequestRepository =
        object : ServiceRequestRepository {
            override fun createWithHistory(
                builder: ServiceRequestBuilder,
                historyEntryId: UUID,
                historyComment: String,
            ): ServiceRequestDetail {
                val built = builder.code("SOL-2026-00001").createdAt(now).build()
                return ServiceRequestDetail(request = built, history = emptyList())
            }

            override fun findRequestByCode(code: String): ServiceRequest? = foundRequest

            override fun findByCode(code: String): ServiceRequestDetail? =
                foundRequest?.let { ServiceRequestDetail(request = it, history = emptyList()) }

            override fun findPagedByRequester(query: MyRequestsQuery): PagedResult<ServiceRequestSummary> =
                PagedResult(
                    items = emptyList(),
                    page = query.page,
                    size = query.size,
                    totalItems = 0L,
                    totalPages = 0,
                )

            override fun cancelWithHistory(
                requestId: UUID,
                transition: StateTransition,
                historyEntryId: UUID,
            ): ServiceRequestDetail? =
                if (cancelReturnsNull) {
                    null
                } else {
                    foundRequest?.let {
                        ServiceRequestDetail(
                            request = it.copy(status = RequestStatus.CANCELADA, updatedAt = transition.occurredAt),
                            history = emptyList(),
                        )
                    }
                }
        }

    @Test
    fun `ServiceRequestService rejects non-SOLICITANTE actors and mismatched requesterId queries`() {
        val service = ServiceRequestService(fakeRepository())

        assertFailsWith<DomainException.ForbiddenException> {
            service.create(
                actor = coordinatorIdentity,
                rawCategory = RequestCategory.TRAMITE.name,
                rawSubject = "Asunto válido",
                rawDescription = "Descripción suficientemente larga para prueba.",
                rawPreviousRequestCode = null,
            )
        }

        val otherQuery = MyRequestsQueryBuilder().requesterId(otherId).build()
        assertFailsWith<DomainException.ForbiddenException> {
            service.listMine(actor = ownerIdentity, query = otherQuery)
        }

        assertFailsWith<DomainException.ForbiddenException> {
            service.getDetail(actor = coordinatorIdentity, code = "SOL-2026-00001")
        }

        assertFailsWith<DomainException.ForbiddenException> {
            service.cancel(actor = coordinatorIdentity, code = "SOL-2026-00001", rawReason = null)
        }
    }

    @Test
    fun `ServiceRequestService throws ConflictException when cancelWithHistory detects concurrent race`() {
        val openRequest =
            ServiceRequest(
                id = UUID.randomUUID(),
                code = "SOL-2026-00001",
                requesterId = ownerId,
                category = RequestCategory.TRAMITE,
                subject = "Solicitud abierta",
                description = "Descripción suficientemente larga para cancelación concurrente.",
                status = RequestStatus.ABIERTA,
                createdAt = now,
                updatedAt = now,
            )
        val service = ServiceRequestService(fakeRepository(foundRequest = openRequest, cancelReturnsNull = true))

        val ex =
            assertFailsWith<DomainException.ConflictException> {
                service.cancel(actor = ownerIdentity, code = "SOL-2026-00001", rawReason = "Cancelo")
            }
        assertEquals(409, ex.httpStatus)
    }
}
