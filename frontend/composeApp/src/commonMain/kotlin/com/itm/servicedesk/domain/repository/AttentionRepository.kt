package com.itm.servicedesk.domain.repository

import com.itm.servicedesk.domain.model.AddInterventionCommand
import com.itm.servicedesk.domain.model.AppResult
import com.itm.servicedesk.domain.model.AssignRequestCommand
import com.itm.servicedesk.domain.model.AttentionRequestDetail
import com.itm.servicedesk.domain.model.AttentionRequestsPage
import com.itm.servicedesk.domain.model.EscalateRequestCommand
import com.itm.servicedesk.domain.model.ReassignRequestCommand
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.domain.model.RespondRequestCommand

interface AttentionRepository {
    suspend fun getCoordinationPending(
        page: Int,
        size: Int = DEFAULT_PAGE_SIZE,
        status: RequestStatus? = null,
    ): AppResult<AttentionRequestsPage>

    suspend fun getMyAssigned(
        page: Int,
        size: Int = DEFAULT_PAGE_SIZE,
        status: RequestStatus? = null,
    ): AppResult<AttentionRequestsPage>

    suspend fun getInternalDetail(code: String): AppResult<AttentionRequestDetail>

    suspend fun assignRequest(
        code: String,
        command: AssignRequestCommand,
    ): AppResult<AttentionRequestDetail>

    suspend fun reassignRequest(
        code: String,
        command: ReassignRequestCommand,
    ): AppResult<AttentionRequestDetail>

    suspend fun addIntervention(
        code: String,
        command: AddInterventionCommand,
    ): AppResult<AttentionRequestDetail>

    suspend fun escalateRequest(
        code: String,
        command: EscalateRequestCommand,
    ): AppResult<AttentionRequestDetail>

    suspend fun respondRequest(
        code: String,
        command: RespondRequestCommand,
    ): AppResult<AttentionRequestDetail>

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }
}
