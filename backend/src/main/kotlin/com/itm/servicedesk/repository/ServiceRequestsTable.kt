package com.itm.servicedesk.repository

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

object ServiceRequestsTable : Table("service_requests") {
    val id = javaUUID("id")
    val code = varchar("code", 20)
    val requesterId = javaUUID("requester_id").references(UsersTable.id, onDelete = ReferenceOption.RESTRICT)
    val category = varchar("category", 40)
    val subject = varchar("subject", 100)
    val description = varchar("description", 2000)
    val status = varchar("status", 20)
    val previousRequestId = javaUUID("previous_request_id").nullable()
    val previousRequestCode = varchar("previous_request_code", 20).nullable()
    val assignedTo = javaUUID("assigned_to").references(UsersTable.id, onDelete = ReferenceOption.RESTRICT).nullable()
    val priority = varchar("priority", 10).nullable()
    val firstAssignedAt = timestampWithTimeZone("first_assigned_at").nullable()
    val assignedAt = timestampWithTimeZone("assigned_at").nullable()
    val dueAt = timestampWithTimeZone("due_at").nullable()
    val responseText = varchar("response_text", 2000).nullable()
    val respondedAt = timestampWithTimeZone("responded_at").nullable()
    val respondedWithinSla = bool("responded_within_sla").nullable()
    val closedAt = timestampWithTimeZone("closed_at").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}
