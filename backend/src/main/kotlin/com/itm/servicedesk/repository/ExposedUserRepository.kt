package com.itm.servicedesk.repository

import com.itm.servicedesk.domain.builder.StaffPageQuery
import com.itm.servicedesk.domain.model.DomainException
import com.itm.servicedesk.domain.model.PagedResult
import com.itm.servicedesk.domain.model.StaffSummary
import com.itm.servicedesk.domain.model.User
import com.itm.servicedesk.domain.model.UserRole
import com.itm.servicedesk.domain.repository.CreateUserCommand
import com.itm.servicedesk.domain.repository.UserRepository
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.sql.SQLException
import java.time.ZoneId
import java.util.UUID

class ExposedUserRepository(
    private val database: Database,
    private val timezone: String = "America/Bogota",
) : UserRepository {
    private val zoneId: ZoneId = ZoneId.of(timezone)

    override fun create(command: CreateUserCommand): User =
        try {
            transaction(database) {
                UsersTable.insert {
                    it[id] = command.id
                    it[fullName] = command.fullName
                    it[documentNumber] = command.documentNumber
                    it[email] = command.email
                    it[phone] = command.phone
                    it[passwordHash] = command.passwordHash
                    it[role] = command.role.name
                    it[active] = command.active
                    it[createdAt] = command.createdAt
                    it[updatedAt] = command.updatedAt
                }
            }
            User(
                id = command.id,
                fullName = command.fullName,
                documentNumber = command.documentNumber,
                email = command.email,
                phone = command.phone,
                passwordHash = command.passwordHash,
                role = command.role,
                active = command.active,
                createdAt = command.createdAt.atZoneSameInstant(zoneId).toOffsetDateTime(),
                updatedAt = command.updatedAt.atZoneSameInstant(zoneId).toOffsetDateTime(),
            )
        } catch (ex: ExposedSQLException) {
            if (isUniqueViolation(ex)) {
                throw DomainException.ConflictException(CONFLICT_MESSAGE)
            }
            throw ex
        } catch (ex: SQLException) {
            if (isUniqueViolation(ex)) {
                throw DomainException.ConflictException(CONFLICT_MESSAGE)
            }
            throw ex
        }

    override fun findById(id: UUID): User? =
        transaction(database) {
            UsersTable
                .select(
                    UsersTable.id,
                    UsersTable.fullName,
                    UsersTable.documentNumber,
                    UsersTable.email,
                    UsersTable.phone,
                    UsersTable.passwordHash,
                    UsersTable.role,
                    UsersTable.active,
                    UsersTable.createdAt,
                    UsersTable.updatedAt,
                ).where { UsersTable.id eq id }
                .limit(1)
                .singleOrNull()
                ?.toUser()
        }

    override fun findByEmail(email: String): User? {
        val normalizedEmail = email.trim().lowercase()
        return transaction(database) {
            UsersTable
                .select(
                    UsersTable.id,
                    UsersTable.fullName,
                    UsersTable.documentNumber,
                    UsersTable.email,
                    UsersTable.phone,
                    UsersTable.passwordHash,
                    UsersTable.role,
                    UsersTable.active,
                    UsersTable.createdAt,
                    UsersTable.updatedAt,
                ).where { UsersTable.email eq normalizedEmail }
                .limit(1)
                .singleOrNull()
                ?.toUser()
        }
    }

    override fun existsByEmailOrDocument(
        email: String,
        documentNumber: String,
    ): Boolean {
        val normalizedEmail = email.trim().lowercase()
        val normalizedDocument = documentNumber.trim()
        return transaction(database) {
            UsersTable
                .select(UsersTable.id)
                .where {
                    (UsersTable.email eq normalizedEmail) or (UsersTable.documentNumber eq normalizedDocument)
                }.limit(1)
                .any()
        }
    }

    override fun findStaffPaged(query: StaffPageQuery): PagedResult<StaffSummary> =
        transaction(database) {
            val staffRoles = listOf(UserRole.PERSONAL_ATENCION.name, UserRole.COORDINADOR.name)
            val filterRole = query.roleFilter
            val whereClause: Op<Boolean> =
                if (filterRole != null && filterRole != UserRole.SOLICITANTE) {
                    UsersTable.role eq filterRole.name
                } else {
                    UsersTable.role inList staffRoles
                }

            val totalItems =
                UsersTable
                    .select(UsersTable.id)
                    .where { whereClause }
                    .count()

            val totalPages =
                if (totalItems == 0L) {
                    0
                } else {
                    ((totalItems + query.size - 1L) / query.size).toInt()
                }

            val items =
                UsersTable
                    .select(
                        UsersTable.id,
                        UsersTable.fullName,
                        UsersTable.email,
                        UsersTable.role,
                        UsersTable.createdAt,
                    ).where { whereClause }
                    .orderBy(UsersTable.createdAt to SortOrder.DESC, UsersTable.id to SortOrder.ASC)
                    .limit(query.size)
                    .offset(query.offset)
                    .map { row ->
                        StaffSummary(
                            id = row[UsersTable.id],
                            fullName = row[UsersTable.fullName],
                            email = row[UsersTable.email],
                            role = UserRole.valueOf(row[UsersTable.role]),
                            createdAt = row[UsersTable.createdAt].atZoneSameInstant(zoneId).toOffsetDateTime(),
                        )
                    }

            PagedResult(
                items = items,
                page = query.page,
                size = query.size,
                totalItems = totalItems,
                totalPages = totalPages,
            )
        }

    private fun ResultRow.toUser(): User =
        User(
            id = this[UsersTable.id],
            fullName = this[UsersTable.fullName],
            documentNumber = this[UsersTable.documentNumber],
            email = this[UsersTable.email],
            phone = this[UsersTable.phone],
            passwordHash = this[UsersTable.passwordHash],
            role = UserRole.valueOf(this[UsersTable.role]),
            active = this[UsersTable.active],
            createdAt = this[UsersTable.createdAt].atZoneSameInstant(zoneId).toOffsetDateTime(),
            updatedAt = this[UsersTable.updatedAt].atZoneSameInstant(zoneId).toOffsetDateTime(),
        )

    private fun isUniqueViolation(throwable: Throwable): Boolean =
        generateSequence(throwable) { it.cause }.any { cause ->
            (cause as? SQLException)?.sqlState == UNIQUE_VIOLATION_SQL_STATE ||
                (cause as? ExposedSQLException)?.sqlState == UNIQUE_VIOLATION_SQL_STATE
        }

    companion object {
        private const val UNIQUE_VIOLATION_SQL_STATE = "23505"
        private const val CONFLICT_MESSAGE =
            "Ya existe una cuenta registrada con el correo electrónico o número de documento indicado."
    }
}
