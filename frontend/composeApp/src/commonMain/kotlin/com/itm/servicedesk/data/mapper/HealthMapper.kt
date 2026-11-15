package com.itm.servicedesk.data.mapper

import com.itm.servicedesk.data.dto.HealthResponseDto
import com.itm.servicedesk.domain.model.ServiceHealth
import kotlin.time.Instant

class HealthMapper {
    fun toDomain(dto: HealthResponseDto): ServiceHealth =
        ServiceHealth(
            isServiceUp = dto.status.equals("UP", ignoreCase = true),
            isDatabaseUp = dto.database.equals("UP", ignoreCase = true),
            serviceName = dto.service,
            checkedAt = Instant.parse(dto.timestamp),
        )
}
