package com.itm.servicedesk.domain.repository

import com.itm.servicedesk.domain.builder.MyRequestsQuery
import com.itm.servicedesk.domain.builder.ServiceRequestBuilder
import com.itm.servicedesk.domain.model.PagedResult
import com.itm.servicedesk.domain.model.ServiceRequest
import com.itm.servicedesk.domain.model.ServiceRequestDetail
import com.itm.servicedesk.domain.model.ServiceRequestSummary
import com.itm.servicedesk.domain.state.StateTransition
import java.util.UUID

interface ServiceRequestRepository {
    fun createWithHistory(
        builder: ServiceRequestBuilder,
        historyEntryId: UUID,
        historyComment: String,
    ): ServiceRequestDetail

    fun findRequestByCode(code: String): ServiceRequest?

    fun findByCode(code: String): ServiceRequestDetail?

    fun findPagedByRequester(query: MyRequestsQuery): PagedResult<ServiceRequestSummary>

    fun cancelWithHistory(
        requestId: UUID,
        transition: StateTransition,
        historyEntryId: UUID,
    ): ServiceRequestDetail?
}
