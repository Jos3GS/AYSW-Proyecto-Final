package com.itm.servicedesk.domain.repository

import com.itm.servicedesk.domain.model.AppResult
import com.itm.servicedesk.domain.model.ServiceHealth

interface SystemHealthRepository {
    suspend fun checkHealth(): AppResult<ServiceHealth>
}
