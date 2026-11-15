package com.itm.servicedesk

import com.itm.servicedesk.service.BcryptPasswordHasher
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class BcryptPasswordHasherTest {
    @Test
    fun `hash generates cost 12 BCrypt hashes with random salt and verifies accurately`() {
        val hasher = BcryptPasswordHasher()
        val raw = "ClaveSegura2026"

        val hash1 = hasher.hash(raw)
        val hash2 = hasher.hash(raw)

        assertNotEquals(raw, hash1)
        assertNotEquals(hash1, hash2)
        assertTrue(hash1.startsWith("\$2a\$12\$"))
        assertTrue(hash2.startsWith("\$2a\$12\$"))

        assertTrue(hasher.verify(raw, hash1))
        assertTrue(hasher.verify(raw, hash2))
        assertFalse(hasher.verify("ClaveIncorrecta2026", hash1))
    }

    @Test
    fun `dummyHash is valid BCrypt cost 12 and fails verification safely`() {
        val hasher = BcryptPasswordHasher()

        assertTrue(hasher.dummyHash.startsWith("\$2a\$12\$"))
        assertFalse(hasher.verify("CualquierClave2026", hasher.dummyHash))
    }
}
