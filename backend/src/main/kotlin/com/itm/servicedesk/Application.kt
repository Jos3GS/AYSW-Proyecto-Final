package com.itm.servicedesk

import com.itm.servicedesk.config.DatabaseFactory
import com.itm.servicedesk.config.EnvConfig
import com.itm.servicedesk.config.configureIpAllowList
import com.itm.servicedesk.config.configureSecurityPlugins
import com.itm.servicedesk.config.configureStatusPages
import com.itm.servicedesk.domain.builder.AppConfig
import com.itm.servicedesk.repository.ExposedHealthRepository
import com.itm.servicedesk.repository.ExposedUserSeedRepository
import com.itm.servicedesk.routing.healthRouting
import com.itm.servicedesk.service.CoordinatorSeedService
import com.itm.servicedesk.service.HealthService
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import java.util.TimeZone

fun main() {
    val appConfig = EnvConfig.toAppConfig()
    TimeZone.setDefault(TimeZone.getTimeZone(appConfig.server.timezone))

    embeddedServer(
        factory = Netty,
        port = appConfig.server.port,
        host = "0.0.0.0",
        module = {
            configureModule(appConfig)
        },
    ).start(wait = true)
}

fun Application.configureModule(
    appConfig: AppConfig,
    healthServiceOverride: HealthService? = null,
    seedOnStartup: Boolean = true,
) {
    TimeZone.setDefault(TimeZone.getTimeZone(appConfig.server.timezone))

    configureSecurityPlugins(appConfig.server)
    configureStatusPages(appConfig.server.timezone)
    configureIpAllowList(appConfig.server.allowedClientIps)

    val healthService =
        if (healthServiceOverride != null) {
            healthServiceOverride
        } else {
            val dbConnection = DatabaseFactory.init(appConfig.database, runMigrations = true)
            monitor.subscribe(ApplicationStopped) {
                dbConnection.close()
            }
            if (seedOnStartup) {
                val seedRepository = ExposedUserSeedRepository(dbConnection.database)
                CoordinatorSeedService(
                    userSeedRepository = seedRepository,
                    timezone = appConfig.server.timezone,
                ).seedIfAbsent(appConfig.seedCoordinator)
            }
            val healthRepository = ExposedHealthRepository(dbConnection.database)
            HealthService(
                healthRepository = healthRepository,
                timezone = appConfig.server.timezone,
            )
        }

    routing {
        healthRouting(healthService)
    }
}
