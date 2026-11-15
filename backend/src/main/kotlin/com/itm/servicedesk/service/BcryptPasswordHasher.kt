package com.itm.servicedesk.service

import at.favre.lib.crypto.bcrypt.BCrypt
import com.itm.servicedesk.domain.security.PasswordHasher

class BcryptPasswordHasher : PasswordHasher {
    override val dummyHash: String
        get() = DUMMY_BCRYPT_HASH

    override fun hash(rawPassword: String): String =
        BCrypt.withDefaults().hashToString(BCRYPT_COST, rawPassword.toCharArray())

    override fun verify(
        rawPassword: String,
        passwordHash: String,
    ): Boolean = BCrypt.verifyer().verify(rawPassword.toCharArray(), passwordHash).verified

    companion object {
        private const val BCRYPT_COST = 12
        private val DUMMY_BCRYPT_HASH: String by lazy {
            BCrypt.withDefaults().hashToString(BCRYPT_COST, "itm-dummy-anti-timing-2026".toCharArray())
        }
    }
}
