package com.itm.servicedesk.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateStaffRequestDto(
    val fullName: String,
    val documentNumber: String,
    val email: String,
    val phone: String,
    val password: String,
    val role: String,
) {
    override fun toString(): String =
        "CreateStaffRequestDto(fullName=$fullName, documentNumber=$documentNumber, " +
            "email=$email, phone=$phone, password=***, role=$role)"
}

@Serializable
data class StaffSummaryDto(
    val id: String,
    val fullName: String,
    val email: String,
    val role: String,
    val createdAt: String,
)

@Serializable
data class PagedStaffResponseDto(
    val items: List<StaffSummaryDto>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
    val totalPages: Int,
)
