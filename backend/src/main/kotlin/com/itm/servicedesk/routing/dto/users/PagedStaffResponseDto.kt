package com.itm.servicedesk.routing.dto.users

import com.itm.servicedesk.domain.model.PagedResult
import com.itm.servicedesk.domain.model.StaffSummary
import kotlinx.serialization.Serializable
import java.time.ZoneId

@Serializable
data class PagedStaffResponseDto(
    val items: List<StaffSummaryDto>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
    val totalPages: Int,
)

fun PagedResult<StaffSummary>.toDto(zoneId: ZoneId): PagedStaffResponseDto =
    PagedStaffResponseDto(
        items = items.map { it.toDto(zoneId) },
        page = page,
        size = size,
        totalItems = totalItems,
        totalPages = totalPages,
    )
