package com.example.espion32.ble

import com.example.espion32.model.BleEvent

/**
 * Parse les notifications texte envoyées par l'ESP32.
 *
 * Format : `TYPE|SUBTYPE|key=value|key=value|...`
 * (sauf `PCAP|CHUNK|<index>|<base64>` qui est positionnel).
 *
 * Objet pur (aucune dépendance Android) pour être testable en JVM. Le décodage
 * base64 est injecté via [b64decode] afin de ne pas dépendre d'`android.util.Base64`
 * dans les tests.
 */
object BleMessageParser {

    fun parse(raw: String, b64decode: (String) -> ByteArray): BleEvent {
        val parts = raw.trim().split("|")
        if (parts.size < 2) return BleEvent.Unknown(raw)

        val type = parts[0]
        val subtype = parts[1]

        val kv = parts.drop(2)
            .mapNotNull {
                val idx = it.indexOf("=")
                if (idx == -1) null
                else it.substring(0, idx) to it.substring(idx + 1)
            }
            .toMap()

        return when (type) {
            "LOG" -> BleEvent.Log(
                subtype = subtype,
                message = kv["msg"] ?: raw
            )

            "MAC" -> {
                val mac = kv["mac"] ?: return BleEvent.Unknown(raw)
                val rssi = kv["rssi"]?.toIntOrNull() ?: return BleEvent.Unknown(raw)
                val ch = kv["ch"]?.toIntOrNull() ?: return BleEvent.Unknown(raw)
                BleEvent.MacFound(subtype = subtype, mac = mac, rssi = rssi, channel = ch)
            }

            "STATUS" -> BleEvent.Status(
                subtype = subtype,
                value = kv["value"] ?: raw
            )

            "ERROR" -> BleEvent.Error(
                subtype = subtype,
                message = kv["msg"] ?: raw
            )

            "PCAP" -> when (subtype) {
                "START" -> BleEvent.PcapStart(
                    totalSize = kv["size"]?.toIntOrNull() ?: 0,
                    frameCount = kv["frames"]?.toIntOrNull() ?: 0
                )
                "CHUNK" -> {
                    // Format positionnel : PCAP|CHUNK|<index>|<base64>
                    val index = parts.getOrNull(2)?.toIntOrNull() ?: return BleEvent.Unknown(raw)
                    val b64 = parts.getOrNull(3) ?: return BleEvent.Unknown(raw)
                    BleEvent.PcapChunk(index = index, data = b64decode(b64))
                }
                "END" -> BleEvent.PcapEnd(
                    // CRC32 = 32 bits NON SIGNÉS -> Long
                    crc = kv["crc"]?.removePrefix("0x")?.toLongOrNull(16) ?: 0L
                )
                else -> BleEvent.Unknown(raw)
            }

            else -> BleEvent.Unknown(raw)
        }
    }
}
