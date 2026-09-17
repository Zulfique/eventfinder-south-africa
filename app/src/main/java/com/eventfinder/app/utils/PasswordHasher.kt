package com.eventfinder.app.utils

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Password hashing using PBKDF2WithHmacSHA256 (NIST approved KDF).
 *
 * The prototype stores users in the on-device Room database and never transmits
 * credentials anywhere. Password text is salted and hashed before persistence.
 *
 * References:
 *  - OWASP, "Password Storage Cheat Sheet":
 *    https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html
 *  - OWASP PBKDF2 Java example:
 *    https://owasp.org/www-community/developers/Password_Storage_Cheat_Sheet#pbkdf2
 */
object PasswordHasher {

    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"

    private fun randomSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }

    /** Returns a "salt:hash" string suitable for storage. */
    fun hash(password: String): String {
        val salt = randomSalt()
        val hash = derive(password, salt)
        return "$salt:$hash"
    }

    /** Verifies a plaintext password against a stored "salt:hash" value. */
    fun verify(password: String, stored: String): Boolean {
        val parts = stored.split(":")
        if (parts.size != 2) return false
        val salt = parts[0]
        val expected = parts[1]
        val actual = derive(password, salt)
        return MessageDigest.isEqual(
            actual.toByteArray(Charsets.UTF_8),
            expected.toByteArray(Charsets.UTF_8)
        )
    }

    private fun derive(password: String, salt: String): String {
        val spec = PBEKeySpec(password.toCharArray(), salt.toByteArray(Charsets.UTF_8), ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance(ALGORITHM)
        val bytes = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return Base64.getEncoder().encodeToString(bytes)
    }
}