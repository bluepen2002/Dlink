package com.dlink.app.exchange

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import java.nio.charset.StandardCharsets

class NfcShareManager(private val activity: Activity) {
    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)
    private var onReceive: ((String) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    val supported: Boolean get() = adapter != null

    fun setReceiver(receiver: (String) -> Unit) { onReceive = receiver }
    fun setErrorReceiver(receiver: (String) -> Unit) { onError = receiver }

    fun handleExternalNdef(extras: android.os.Bundle?) {
        val arr = if (android.os.Build.VERSION.SDK_INT >= 33) extras?.getParcelableArray(NfcAdapter.EXTRA_NDEF_MESSAGES, android.os.Parcelable::class.java) else @Suppress("DEPRECATION") extras?.getParcelableArray(NfcAdapter.EXTRA_NDEF_MESSAGES)
        val msg = arr?.firstOrNull() as? android.nfc.NdefMessage ?: return
        val value = msg.records.firstOrNull()?.payload?.toString(StandardCharsets.UTF_8) ?: return
        try { DlinkShareEnvelope.decode(value); onReceive?.invoke(value) } catch (_: Exception) { }
    }

    fun publish(payload: String?) { DlinkNfcService.setPayload(activity, payload) }
    fun clearPublishedPayload() { DlinkNfcService.setPayload(activity, null) }

    fun enableReader() {
        val a = adapter ?: return
        if (!a.isEnabled) { onError?.invoke("NFC is turned off") ; return }
        a.enableReaderMode(activity, { tag -> readDlinkTag(tag) }, NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, Bundle())
    }
    fun disableReader() { adapter?.disableReaderMode(activity) }

    private fun readDlinkTag(tag: Tag) {
        try {
            val isoDep = android.nfc.tech.IsoDep.get(tag) ?: throw IllegalStateException("Dlink NFC card not found")
            isoDep.connect()
            isoDep.timeout = 1500
            val aid = byteArrayOf(0xF0.toByte(),0x44,0x4C,0x49,0x4E,0x4B,0x01)
            val select = byteArrayOf(0x00,0xA4.toByte(),0x04,0x00,aid.size.toByte()) + aid
            check(isoDep.transceive(select).takeLast(2).toByteArray().contentEquals(byteArrayOf(0x90.toByte(),0x00))) { "Not a Dlink NFC device" }
            val out = java.io.ByteArrayOutputStream()
            var offset = 0
            while (true) {
                val command = byteArrayOf(0x00,0x10,0x00,0x00,0x00, (offset shr 8).toByte(), offset.toByte())
                val response = isoDep.transceive(command)
                if (response.size < 2) break
                val status = response.takeLast(2).toByteArray()
                if (!status.contentEquals(byteArrayOf(0x90.toByte(),0x00))) break
                val chunk = response.copyOf(response.size - 2)
                if (chunk.isEmpty()) break
                out.write(chunk)
                offset += chunk.size
                if (chunk.size < 240) break
            }
            isoDep.close()
            val payload = out.toString(StandardCharsets.UTF_8.name())
            DlinkShareEnvelope.decode(payload)
            activity.runOnUiThread { onReceive?.invoke(payload) }
        } catch (e: Exception) {
            try { (tag.techList.mapNotNull { try { android.nfc.tech.IsoDep.get(tag) } catch (_: Exception) { null } }.firstOrNull())?.close() } catch (_: Exception) {}
            activity.runOnUiThread { onError?.invoke(e.message ?: "NFC exchange failed") }
        }
    }
}
