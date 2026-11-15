package com.itm.servicedesk.config

import com.itm.servicedesk.domain.builder.DatabaseConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database

data class DatabaseConnection(
    val dataSource: HikariDataSource,
    val database: Database,
) : AutoCloseable {
    override fun close() {
        dataSource.close()
    }
}

object DatabaseFactory {
    fun init(
        config: DatabaseConfig,
        runMigrations: Boolean = true,
    ): DatabaseConnection {
        val hikariConfig =
            HikariConfig().apply {
                jdbcUrl = config.jdbcUrl
                username = config.user
                password = config.password
                driverClassName = "org.postgresql.Driver"
                maximumPoolSize = config.maxPoolSize
                connectionTimeout = config.connectionTimeoutMs
                validationTimeout = config.validationTimeoutMs
                initializationFailTimeout = 1L
                isAutoCommit = false
                transactionIsolation = "TRANSACTION_READ_COMMITTED"
                poolName = "itm-service-desk-hikari"
            }

        val dataSource = HikariDataSource(hikariConfig)
        try {
            if (runMigrations) {
                Flyway
                    .configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .load()
                    .migrate()
            }
            val database = Database.connect(dataSource)
            return DatabaseConnection(dataSource = dataSource, database = database)
        } catch (ex: Exception) {
            dataSource.close()
            throw ex
        }
    }
}
