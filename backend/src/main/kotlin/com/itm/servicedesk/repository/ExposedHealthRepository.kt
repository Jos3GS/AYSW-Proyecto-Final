package com.itm.servicedesk.repository

import com.itm.servicedesk.domain.repository.HealthRepository
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class ExposedHealthRepository(
    private val database: Database,
) : HealthRepository {
    override fun checkDatabaseHealth(): Boolean =
        transaction(database) {
            exec("SELECT 1") { rs ->
                rs.next() && rs.getInt(1) == 1
            } ?: false
        }
}
