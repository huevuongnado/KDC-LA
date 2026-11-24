package com.example.util

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class HashedPassword(
    val saltHex: String,
    val hashHex: String,
)

object PasswordHashHelper {
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 60_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16

    private val secureRandom = SecureRandom()

    fun normalizeIdentifier(raw: String): String {
        return raw.trim()
            .replace(Regex("[\\s.-]+"), "")
            .lowercase()
    }

    fun buildLoginKeys(phone: String, cccd: String): List<String> {
        return listOf(phone, cccd)
            .map(::normalizeIdentifier)
            .filter { it.isNotBlank() }
            .distinct()
            .take(4)
    }

    fun validateNewPassword(password: String): String? {
        val trimmed = password.trim()
        return when {
            trimmed.length < 6 -> "Mật khẩu mới phải có ít nhất 6 ký tự."
            trimmed.length > 64 -> "Mật khẩu tối đa 64 ký tự."
            else -> null
        }
    }

    fun hashPassword(password: String): HashedPassword {
        val salt = ByteArray(SALT_BYTES)
        secureRandom.nextBytes(salt)
        val hash = pbkdf2(password.toCharArray(), salt)
        return HashedPassword(
            saltHex = salt.toHex(),
            hashHex = hash.toHex(),
        )
    }

    fun verifyPassword(password: String, saltHex: String, expectedHashHex: String): Boolean {
        val salt = hexToBytes(saltHex) ?: return false
        val expected = hexToBytes(expectedHashHex) ?: return false
        if (salt.isEmpty() || expected.isEmpty()) return false
        val actual = pbkdf2(password.toCharArray(), salt)
        return MessageDigest.isEqual(actual, expected)
    }

    private fun pbkdf2(chars: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(chars, salt, ITERATIONS, KEY_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun ByteArray.toHex(): String {
        val sb = StringBuilder(size * 2)
        for (b in this) {
            val v = b.toInt() and 0xFF
            sb.append(HEX_CHARS[v ushr 4])
            sb.append(HEX_CHARS[v and 0x0F])
        }
        return sb.toString()
    }

    private fun hexToBytes(hex: String): ByteArray? {
        val clean = hex.trim()
        if (clean.length % 2 != 0 || clean.isEmpty()) return null
        val result = ByteArray(clean.length / 2)
        for (i in result.indices) {
            val hi = Character.digit(clean[i * 2], 16)
            val lo = Character.digit(clean[i * 2 + 1], 16)
            if (hi < 0 || lo < 0) return null
            result[i] = ((hi shl 4) or lo).toByte()
        }
        return result
    }

    private val HEX_CHARS = "0123456789abcdef".toCharArray()
}
