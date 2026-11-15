package com.itm.servicedesk.domain.repository

interface HealthRepository {
    fun checkDatabaseHealth(): Boolean
}
