package com.itm.servicedesk.repository

import com.itm.servicedesk.domain.model.UserRole
import com.itm.servicedesk.domain.repository.SeedUserData
import com.itm.servicedesk.domain.repository.UserSeedRepository
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class ExposedUserSeedRepository(
    private val database: Database,
) : UserSeedRepository {
    override fun existsCoordinator(): Boolean =
        transaction(database) {
            UsersTable
                .select(UsersTable.id)
                .where { UsersTable.role eq UserRole.COORDINADOR.name }
                .limit(1)
                .any()
        }

    override fun createCoordinatorIfAbsent(user: SeedUserData): Boolean =
        transaction(database) {
            val alreadyExists =
                UsersTable
                    .select(UsersTable.id)
                    .where { UsersTable.role eq UserRole.COORDINADOR.name }
                    .limit(1)
                    .any()
            if (alreadyExists) {
                return@transaction false
            }

            val statement =
                UsersTable.insertIgnore {
                    it[id] = user.id
                    it[fullName] = user.fullName
                    it[documentNumber] = user.documentNumber
                    it[email] = user.email
                    it[phone] = user.phone
                    it[passwordHash] = user.passwordHash
                    it[role] = user.role.name
                    it[active] = user.active
                    it[createdAt] = user.createdAt
                    it[updatedAt] = user.updatedAt
                }
            statement.insertedCount > 0
        }
}
