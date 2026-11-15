package com.itm.servicedesk.domain.security

interface PasswordHasher {
    val dummyHash: String

    fun hash(rawPassword: String): String

    fun verify(
        rawPassword: String,
        passwordHash: String,
    ): Boolean
}
