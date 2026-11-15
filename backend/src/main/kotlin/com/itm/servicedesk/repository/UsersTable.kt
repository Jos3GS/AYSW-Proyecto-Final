package com.itm.servicedesk.repository

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

object UsersTable : Table("users") {
    val id = javaUUID("id")
    val fullName = varchar("full_name", 100)
    val documentNumber = varchar("document_number", 15)
    val email = varchar("email", 100)
    val phone = varchar("phone", 15)
    val passwordHash = varchar("password_hash", 100)
    val role = varchar("role", 30)
    val active = bool("active")
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}
