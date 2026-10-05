package com.dlink.app.exchange

import android.util.Base64
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Offline authenticated encryption for QR/NFC payloads.
 * The exchange code is intentionally high-entropy and must be shown by the sender
 * and entered by the receiver. No network or backend is required.
 */
object DlinkSecureEnvelope {
    private const val VERSION = 2
    private const val ITERATIONS = 210_000
    private const val KEY_BITS = 256
    private const val IV_BYTES = 12
    private const val SALT_BYTES = 16
    private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    data class Packet(val raw: String, val code: String, val expiresAt: Long)

    fun newCode(): String {
        val random = SecureRandom()
        return buildString(10) { repeat(10) { append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)]) } }
    }

    fun encode(profile: DlinkShareEnvelope, code: String): String {
        require(code.length >= 8) { "Invalid exchange code" }
        val plain = JSONObject().apply {
            put("sessionId", profile.sessionId)
            put("expiresAt", profile.expiresAt)
            put("profileType", profile.profileType)
            put("name", profile.name)
            put("title", profile.title)
            put("company", profile.company)
            put("bio", profile.bio)
            profile.phone?.let { put("phone", it) }
            profile.email?.let { put("email", it) }
            profile.website?.let { put("website", it) }
        }.toString().toByteArray(StandardCharsets.UTF_8)

        val salt = randomBytes(SALT_BYTES)
        val iv = randomBytes(IV_BYTES)
        val key = deriveKey(code, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val aad = "Dlink|$VERSION|${profile.sessionId}|${profile.expiresAt}".toByteArray(StandardCharsets.UTF_8)
        cipher.updateAAD(aad)
        val ciphertext = cipher.doFinal(plain)

        val packet = JSONObject().apply {
            put("v", VERSION)
            put("app", "Dlink")
            put("type", "encrypted_profile")
            put("sessionId", profile.sessionId)
            put("expiresAt", profile.expiresAt)
            put("salt", b64(salt))
            put("iv", b64(iv))
            put("data", b64(ciphertext))
        }
        val outer = b64(packet.toString().toByteArray(StandardCharsets.UTF_8))
        return "dlink://secure?payload=$outer"
    }

    fun decode(raw: String, code: String): DlinkShareEnvelope {
        require(raw.startsWith("dlink://secure?payload=")) { "Not a secure Dlink payload" }
        val packet = JSONObject(String(Base64.decode(raw.substringAfter("payload="), Base64.URL_SAFE or Base64.NO_WRAP), StandardCharsets.UTF_8))
        require(packet.optInt("v") == VERSION && packet.optString("app") == "Dlink" && packet.optString("type") == "encrypted_profile") { "Unsupported secure Dlink payload" }
        val sessionId = packet.getString("sessionId")
        val expiresAt = packet.getLong("expiresAt")
        require(expiresAt >= System.currentTimeMillis()) { "Dlink share has expired" }
        val salt = Base64.decode(packet.getString("salt"), Base64.NO_WRAP)
        val iv = Base64.decode(packet.getString("iv"), Base64.NO_WRAP)
        val data = Base64.decode(packet.getString("data"), Base64.NO_WRAP)
        val key = deriveKey(code, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        val aad = "Dlink|$VERSION|$sessionId|$expiresAt".toByteArray(StandardCharsets.UTF_8)
        cipher.updateAAD(aad)
        val plain = cipher.doFinal(data)
        val j = JSONObject(String(plain, StandardCharsets.UTF_8))
        require(j.getString("sessionId") == sessionId && j.getLong("expiresAt") == expiresAt) { "Invalid Dlink session" }
        return DlinkShareEnvelope(
            sessionId, expiresAt, j.getString("profileType"), j.getString("name"), j.optString("title"),
            j.optString("company"), j.optString("bio"), j.optString("phone").ifBlank { null },
            j.optString("email").ifBlank { null }, j.optString("website").ifBlank { null }
        )
    }

    private fun deriveKey(code: String, salt: ByteArray): SecretKeySpec {
        val normalized = code.trim().uppercase().toCharArray()
        val spec = PBEKeySpec(normalized, salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally { spec.clearPassword() }
    }

    private fun randomBytes(size: Int) = ByteArray(size).also { SecureRandom().nextBytes(it) }
    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE)
}
