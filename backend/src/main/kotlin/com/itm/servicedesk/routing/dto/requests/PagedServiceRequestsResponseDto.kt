package com.itm.servicedesk.routing.dto.requests

import com.itm.servicedesk.domain.model.PagedResult
import com.itm.servicedesk.domain.model.ServiceRequestSummary
import kotlinx.serialization.Serializable
import java.time.ZoneId

@Serializable
data class PagedServiceRequestsResponseDto(
    val items: List<ServiceRequestSummaryDto>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
    val totalPages: Int,
)

fun PagedResult<ServiceRequestSummary>.toDto(zoneId: ZoneId): PagedServiceRequestsResponseDto =
    PagedServiceRequestsResponseDto(
        items = items.map { it.toDto(zoneId) },
        page = page,
        size = size,
        totalItems = totalItems,
        totalPages = totalPages,
    )
