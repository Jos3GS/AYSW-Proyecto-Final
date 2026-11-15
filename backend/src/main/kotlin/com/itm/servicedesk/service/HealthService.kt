package com.itm.servicedesk.service

import com.itm.servicedesk.domain.model.DomainException
import com.itm.servicedesk.domain.model.HealthStatus
import com.itm.servicedesk.domain.model.SystemHealth
import com.itm.servicedesk.domain.repository.HealthRepository
import java.sql.SQLException
import java.time.OffsetDateTime
import java.time.ZoneId

class HealthService(
    private val healthRepository: HealthRepository,
    private val timezone: String = "America/Bogota",
) {
    fun checkHealth(): SystemHealth {
        val dbHealthy =
            try {
                healthRepository.checkDatabaseHealth()
            } catch (_: SQLException) {
                throw DomainException.ServiceUnavailableException()
            }

        if (!dbHealthy) {
            throw DomainException.ServiceUnavailableException()
        }

        val now = OffsetDateTime.now(ZoneId.of(timezone))
        return SystemHealth(
            status = HealthStatus.UP,
            serviceName = SERVICE_NAME,
            databaseStatus = HealthStatus.UP,
            checkedAt = now,
        )
    }

    companion object {
        const val SERVICE_NAME = "itm-service-desk-backend"
    }
}
