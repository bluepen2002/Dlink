package com.dlink.app.exchange

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import java.nio.charset.StandardCharsets

class DlinkNfcService : HostApduService() {
    companion object {
        private val AID = byteArrayOf(0xF0.toByte(),0x44,0x4C,0x49,0x4E,0x4B,0x01)
        private const val INS_GET_DATA: Byte = 0x10
        private const val SW_OK = 0x9000
        private const val SW_INS_NOT_SUPPORTED = 0x6D00
        private const val SW_WRONG_LENGTH = 0x6700
        private const val PREFS = "dlink_nfc"
        private const val KEY_PAYLOAD = "payload"
        fun setPayload(serviceContext: android.content.Context, payload: String?) { serviceContext.getSharedPreferences(PREFS, 0).edit().putString(KEY_PAYLOAD, payload).apply() }
    }
    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {
        if (commandApdu.size < 4) return sw(SW_WRONG_LENGTH)
        val ins = commandApdu[1]
        if (ins != INS_GET_DATA) return sw(SW_INS_NOT_SUPPORTED)
        val payload = getSharedPreferences(PREFS, 0).getString(KEY_PAYLOAD, null) ?: return sw(SW_WRONG_LENGTH)
        val bytes = payload.toByteArray(StandardCharsets.UTF_8)
        val offset = if (commandApdu.size >= 7) ((commandApdu[5].toInt() and 0xFF) shl 8) or (commandApdu[6].toInt() and 0xFF) else 0
        if (offset >= bytes.size) return sw(SW_OK)
        val end = minOf(offset + 240, bytes.size)
        return bytes.copyOfRange(offset, end) + sw(SW_OK)
    }
    override fun onDeactivated(reason: Int) {}
    private fun sw(value: Int) = byteArrayOf((value shr 8).toByte(), value.toByte())
}
