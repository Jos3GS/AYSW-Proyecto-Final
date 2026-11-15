package com.itm.servicedesk.repository

import com.itm.servicedesk.domain.repository.RevokedTokenRepository
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.OffsetDateTime
import java.util.UUID

class ExposedRevokedTokenRepository(
    private val database: Database,
) : RevokedTokenRepository {
    override fun revokeAndPurgeExpired(
        jti: UUID,
        expiresAt: OffsetDateTime,
        now: OffsetDateTime,
    ) {
        transaction(database) {
            RevokedTokensTable.insertIgnore {
                it[RevokedTokensTable.jti] = jti
                it[RevokedTokensTable.expiresAt] = expiresAt
                it[RevokedTokensTable.revokedAt] = now
            }
            RevokedTokensTable.deleteWhere {
                RevokedTokensTable.expiresAt less now
            }
        }
    }

    override fun isRevoked(jti: UUID): Boolean =
        transaction(database) {
            RevokedTokensTable
                .select(RevokedTokensTable.jti)
                .where { RevokedTokensTable.jti eq jti }
                .limit(1)
                .any()
        }
}
