package com.itm.servicedesk.domain.model

data class ServiceRequestDetail(
    val request: ServiceRequest,
    val history: List<ApplicantVisibleHistoryEntry>,
)
