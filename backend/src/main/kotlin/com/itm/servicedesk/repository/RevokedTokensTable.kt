package com.itm.servicedesk.repository

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

object RevokedTokensTable : Table("revoked_tokens") {
    val jti = javaUUID("jti")
    val expiresAt = timestampWithTimeZone("expires_at")
    val revokedAt = timestampWithTimeZone("revoked_at")

    override val primaryKey = PrimaryKey(jti)
}
