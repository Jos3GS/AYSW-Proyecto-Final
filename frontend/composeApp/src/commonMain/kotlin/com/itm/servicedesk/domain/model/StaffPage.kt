package com.itm.servicedesk.domain.model

data class StaffPage(
    val items: List<StaffMember>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
    val totalPages: Int,
)
