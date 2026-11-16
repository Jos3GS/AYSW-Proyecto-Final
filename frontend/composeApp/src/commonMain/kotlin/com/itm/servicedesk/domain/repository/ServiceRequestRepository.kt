package com.itm.servicedesk.domain.repository

import com.itm.servicedesk.domain.model.AppResult
import com.itm.servicedesk.domain.model.CancelServiceRequestCommand
import com.itm.servicedesk.domain.model.CreateServiceRequestCommand
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.domain.model.ServiceRequestDetail
import com.itm.servicedesk.domain.model.ServiceRequestsPage

interface ServiceRequestRepository {
    suspend fun create(command: CreateServiceRequestCommand): AppResult<ServiceRequestDetail>

    suspend fun getMyRequests(
        page: Int,
        size: Int = DEFAULT_PAGE_SIZE,
        statusFilter: RequestStatus? = null,
    ): AppResult<ServiceRequestsPage>

    suspend fun getDetail(code: String): AppResult<ServiceRequestDetail>

    suspend fun cancel(command: CancelServiceRequestCommand): AppResult<ServiceRequestDetail>

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }
}
