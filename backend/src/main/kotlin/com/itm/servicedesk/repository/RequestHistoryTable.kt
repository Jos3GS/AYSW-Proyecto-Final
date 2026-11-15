package com.itm.servicedesk.repository

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

object RequestHistoryTable : Table("request_history") {
    val id = javaUUID("id")
    val requestId =
        javaUUID("request_id").references(ServiceRequestsTable.id, onDelete = ReferenceOption.RESTRICT)
    val actorId = javaUUID("actor_id").references(UsersTable.id, onDelete = ReferenceOption.RESTRICT).nullable()
    val actorRole = varchar("actor_role", 30)
    val action = varchar("action", 30)
    val previousState = varchar("previous_state", 20).nullable()
    val newState = varchar("new_state", 20)
    val comment = varchar("comment", 2000).nullable()
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}
