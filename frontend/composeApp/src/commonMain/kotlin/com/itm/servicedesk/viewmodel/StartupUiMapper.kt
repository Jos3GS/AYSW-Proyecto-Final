package com.itm.servicedesk.viewmodel

import com.itm.servicedesk.core.time.DateFormatter
import com.itm.servicedesk.domain.model.DomainError
import com.itm.servicedesk.domain.model.ServiceHealth

class StartupUiMapper {
    fun mapReady(health: ServiceHealth): StartupUiState.Ready =
        StartupUiState.Ready(
            serviceLabel = if (health.isServiceUp) "Activo" else "Con degradación",
            databaseLabel = if (health.isDatabaseUp) "Conectada" else "Sin conexión",
            formattedCheckedAt = DateFormatter.formatBogotaDateTime(health.checkedAt),
        )

    fun mapError(error: DomainError): StartupUiState.Error {
        val title =
            when (error) {
                is DomainError.ServiceUnavailable -> "Servicio temporalmente no disponible"
                is DomainError.NetworkUnavailable -> "Sin conexión con el servidor"
                else -> "Error al verificar el servicio"
            }
        return StartupUiState.Error(
            title = title,
            message = error.userMessage,
            canRetry = true,
        )
    }
}
