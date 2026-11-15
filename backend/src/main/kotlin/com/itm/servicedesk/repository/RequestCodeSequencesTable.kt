package com.itm.servicedesk.repository

import org.jetbrains.exposed.v1.core.Table

object RequestCodeSequencesTable : Table("request_code_sequences") {
    val year = integer("year")
    val lastNumber = integer("last_number")

    override val primaryKey = PrimaryKey(year)
}
