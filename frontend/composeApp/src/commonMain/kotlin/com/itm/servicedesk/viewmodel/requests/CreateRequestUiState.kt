package com.itm.servicedesk.viewmodel.requests

import com.itm.servicedesk.domain.model.RequestCategory
import com.itm.servicedesk.domain.model.ServiceRequestDetail

data class CreateRequestUiState(
    val category: RequestCategory? = null,
    val subject: String = "",
    val description: String = "",
    val previousRequestCode: String = "",
    val fieldErrors: Map<String, String> = emptyMap(),
    val generalError: String? = null,
    val isSubmitting: Boolean = false,
    val createdCode: String? = null,
)

sealed interface CreateRequestEvent {
    data class Created(
        val detail: ServiceRequestDetail,
    ) : CreateRequestEvent
}
