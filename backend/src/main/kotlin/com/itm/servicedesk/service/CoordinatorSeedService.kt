package com.itm.servicedesk.service

import at.favre.lib.crypto.bcrypt.BCrypt
import com.itm.servicedesk.domain.builder.SeedCoordinatorConfig
import com.itm.servicedesk.domain.model.UserRole
import com.itm.servicedesk.domain.repository.SeedUserData
import com.itm.servicedesk.domain.repository.UserSeedRepository
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID

class CoordinatorSeedService(
    private val userSeedRepository: UserSeedRepository,
    private val timezone: String = "America/Bogota",
) {
    private val logger = LoggerFactory.getLogger(CoordinatorSeedService::class.java)

    fun seedIfAbsent(config: SeedCoordinatorConfig): Boolean {
        if (userSeedRepository.existsCoordinator()) {
            logger.info("Initial COORDINADOR user already exists; skipping seed.")
            return false
        }

        val now = OffsetDateTime.now(ZoneId.of(timezone))
        val passwordHash = BCrypt.withDefaults().hashToString(BCRYPT_COST, config.rawPassword.toCharArray())
        val seedUser =
            SeedUserData(
                id = UUID.randomUUID(),
                fullName = config.fullName,
                documentNumber = config.documentNumber,
                email = config.email,
                phone = config.phone,
                passwordHash = passwordHash,
                role = UserRole.COORDINADOR,
                active = true,
                createdAt = now,
                updatedAt = now,
            )

        val created = userSeedRepository.createCoordinatorIfAbsent(seedUser)
        if (created) {
            logger.info("Initial COORDINADOR user created successfully for email={}", config.email)
        } else {
            logger.info("Initial COORDINADOR user already exists; skipping seed.")
        }
        return created
    }

    companion object {
        private const val BCRYPT_COST = 12
    }
}
