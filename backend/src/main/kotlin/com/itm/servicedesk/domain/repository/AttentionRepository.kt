package com.itm.servicedesk.domain.repository

import com.itm.servicedesk.domain.builder.AttentionPageQuery
import com.itm.servicedesk.domain.model.AttentionRequestDetail
import com.itm.servicedesk.domain.model.AttentionRequestSummary
import com.itm.servicedesk.domain.model.AttentionTransition
import com.itm.servicedesk.domain.model.PagedResult
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.domain.model.ServiceRequest
import java.time.OffsetDateTime
import java.util.UUID

interface AttentionRepository {
    fun findPendingForCoordination(
        query: AttentionPageQuery,
        now: OffsetDateTime,
    ): PagedResult<AttentionRequestSummary>

    fun findAssignedToAgent(
        query: AttentionPageQuery,
        now: OffsetDateTime,
    ): PagedResult<AttentionRequestSummary>

    fun findRequestByCode(code: String): ServiceRequest?

    fun findInternalDetailByCode(
        code: String,
        now: OffsetDateTime,
    ): AttentionRequestDetail?

    fun applyTransitionWithHistory(
        expectedCurrentStatus: RequestStatus,
        transition: AttentionTransition,
        historyEntryId: UUID,
    ): ServiceRequest?

    fun autoCloseEligibleRequests(
        cutoff: OffsetDateTime,
        now: OffsetDateTime,
        batchSize: Int = 100,
    ): Int
}
