package com.itm.servicedesk

import com.itm.servicedesk.config.CaddyBootstrapper
import com.itm.servicedesk.config.DatabaseFactory
import com.itm.servicedesk.config.EnvConfig
import com.itm.servicedesk.config.configureIpAllowList
import com.itm.servicedesk.config.configureSecurityPlugins
import com.itm.servicedesk.config.configureStatusPages
import com.itm.servicedesk.config.security.configureJwtAuth
import com.itm.servicedesk.domain.builder.AppConfig
import com.itm.servicedesk.domain.time.BogotaTimeProvider
import com.itm.servicedesk.domain.time.TimeProvider
import com.itm.servicedesk.repository.ExposedAttentionRepository
import com.itm.servicedesk.repository.ExposedHealthRepository
import com.itm.servicedesk.repository.ExposedReportRepository
import com.itm.servicedesk.repository.ExposedRevokedTokenRepository
import com.itm.servicedesk.repository.ExposedServiceRequestRepository
import com.itm.servicedesk.repository.ExposedUserRepository
import com.itm.servicedesk.repository.ExposedUserSeedRepository
import com.itm.servicedesk.routing.attentionRouting
import com.itm.servicedesk.routing.authRouting
import com.itm.servicedesk.routing.healthRouting
import com.itm.servicedesk.routing.reportRouting
import com.itm.servicedesk.routing.serviceRequestRouting
import com.itm.servicedesk.routing.staffRouting
import com.itm.servicedesk.service.AttentionService
import com.itm.servicedesk.service.AuthService
import com.itm.servicedesk.service.AutoCloseScheduler
import com.itm.servicedesk.service.AutoCloseService
import com.itm.servicedesk.service.BcryptPasswordHasher
import com.itm.servicedesk.service.CoordinatorSeedService
import com.itm.servicedesk.service.HealthService
import com.itm.servicedesk.service.JwtTokenService
import com.itm.servicedesk.service.ReportService
import com.itm.servicedesk.service.ServiceRequestService
import com.itm.servicedesk.service.StaffService
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import java.util.TimeZone

fun main() {
    val env = EnvConfig.load()
    val appConfig = EnvConfig.toAppConfig(systemEnv = env)
    TimeZone.setDefault(TimeZone.getTimeZone(appConfig.server.timezone))
    CaddyBootstrapper().ensureRunning(env = env)

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
    timeProviderOverride: TimeProvider? = null,
) {
    TimeZone.setDefault(TimeZone.getTimeZone(appConfig.server.timezone))

    configureSecurityPlugins(appConfig.server)
    configureStatusPages(appConfig.server.timezone)
    configureIpAllowList(appConfig.server.allowedClientIps)

    if (healthServiceOverride != null) {
        routing {
            healthRouting(healthServiceOverride)
        }
        return
    }

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

    val timeProvider = timeProviderOverride ?: BogotaTimeProvider(appConfig.server.timezone)

    val healthRepository = ExposedHealthRepository(dbConnection.database)
    val healthService =
        HealthService(
            healthRepository = healthRepository,
            timezone = appConfig.server.timezone,
        )

    val userRepository = ExposedUserRepository(dbConnection.database, appConfig.server.timezone)
    val revokedTokenRepository = ExposedRevokedTokenRepository(dbConnection.database)
    val serviceRequestRepository =
        ExposedServiceRequestRepository(dbConnection.database, appConfig.server.timezone)
    val attentionRepository =
        ExposedAttentionRepository(dbConnection.database, appConfig.server.timezone)
    val reportRepository =
        ExposedReportRepository(dbConnection.database, appConfig.server.timezone)
    val passwordHasher = BcryptPasswordHasher()
    val tokenProvider = JwtTokenService(appConfig.jwt, appConfig.server.timezone)

    val authService =
        AuthService(
            userRepository = userRepository,
            revokedTokenRepository = revokedTokenRepository,
            passwordHasher = passwordHasher,
            tokenProvider = tokenProvider,
            timezone = appConfig.server.timezone,
        )
    val staffService =
        StaffService(
            userRepository = userRepository,
            passwordHasher = passwordHasher,
            timezone = appConfig.server.timezone,
        )
    val serviceRequestService =
        ServiceRequestService(
            serviceRequestRepository = serviceRequestRepository,
            timezone = appConfig.server.timezone,
        )
    val attentionService =
        AttentionService(
            attentionRepository = attentionRepository,
            userRepository = userRepository,
            timeProvider = timeProvider,
        )
    val reportService =
        ReportService(
            reportRepository = reportRepository,
            timeProvider = timeProvider,
            timezone = appConfig.server.timezone,
        )
    val autoCloseService =
        AutoCloseService(
            attentionRepository = attentionRepository,
            timeProvider = timeProvider,
        )
    AutoCloseScheduler(
        autoCloseService = autoCloseService,
        intervalSeconds = appConfig.server.autoCloseIntervalSeconds,
    ).registerLifecycle(this)

    configureJwtAuth(
        jwtConfig = appConfig.jwt,
        timezone = appConfig.server.timezone,
        userRepository = userRepository,
        revokedTokenRepository = revokedTokenRepository,
    )

    routing {
        healthRouting(healthService)
        authRouting(authService, appConfig.server.timezone)
        staffRouting(staffService, appConfig.server.timezone)
        serviceRequestRouting(serviceRequestService, appConfig.server.timezone)
        attentionRouting(attentionService, appConfig.server.timezone)
        reportRouting(reportService, appConfig.server.timezone)
    }
}
