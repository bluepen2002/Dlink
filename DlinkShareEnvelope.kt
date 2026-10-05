package com.dlink.app.exchange

import android.util.Base64
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

/** Offline transport envelope shared by QR and NFC. No network is required. */
data class DlinkShareEnvelope(
    val sessionId: String,
    val expiresAt: Long,
    val profileType: String,
    val name: String,
    val title: String,
    val company: String,
    val bio: String,
    val phone: String? = null,
    val email: String? = null,
    val website: String? = null
) {
    fun encode(): String {
        val body = JSONObject().apply {
            put("v", 1)
            put("app", "Dlink")
            put("type", "profile")
            put("sessionId", sessionId)
            put("expiresAt", expiresAt)
            put("profileType", profileType)
            put("name", name)
            put("title", title)
            put("company", company)
            put("bio", bio)
            phone?.let { put("phone", it) }
            email?.let { put("email", it) }
            website?.let { put("website", it) }
        }.toString()
        val checksum = sha256(body).take(16)
        val packet = JSONObject().apply {
            put("body", Base64.encodeToString(body.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP or Base64.URL_SAFE))
            put("checksum", checksum)
        }
        return "dlink://share?payload=" + Base64.encodeToString(packet.toString().toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP or Base64.URL_SAFE)
    }

    companion object {
        fun create(name: String, title: String, company: String, bio: String, profileType: String = "professional", phone: String? = null, email: String? = null, website: String? = null, ttlMs: Long = 30_000): DlinkShareEnvelope =
            DlinkShareEnvelope(UUID.randomUUID().toString(), System.currentTimeMillis() + ttlMs, profileType, name, title, company, bio, phone, email, website)

        fun decode(raw: String): DlinkShareEnvelope {
            require(raw.startsWith("dlink://share?payload=")) { "Not a Dlink QR/NFC payload" }
            val encoded = raw.substringAfter("payload=")
            val packetJson = String(Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP), StandardCharsets.UTF_8)
            val packet = JSONObject(packetJson)
            val body = String(Base64.decode(packet.getString("body"), Base64.URL_SAFE or Base64.NO_WRAP), StandardCharsets.UTF_8)
            require(sha256(body).take(16) == packet.getString("checksum")) { "Invalid Dlink checksum" }
            val j = JSONObject(body)
            require(j.optInt("v") == 1 && j.optString("app") == "Dlink" && j.optString("type") == "profile") { "Unsupported Dlink payload" }
            val expires = j.getLong("expiresAt")
            require(expires >= System.currentTimeMillis()) { "Dlink share has expired" }
            return DlinkShareEnvelope(j.getString("sessionId"), expires, j.getString("profileType"), j.getString("name"), j.optString("title"), j.optString("company"), j.optString("bio"), j.optString("phone").ifBlank { null }, j.optString("email").ifBlank { null }, j.optString("website").ifBlank { null })
        }

        private fun sha256(s: String): String = MessageDigest.getInstance("SHA-256").digest(s.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
