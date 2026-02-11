package com.example.espion32.model

sealed class Command {

    abstract fun toPayload(): String

    // SNIFFER
    data class SendSniffStart(
        val ssid: String,
        val bssid: String,
        val channel: Int
    ) : Command() {
        override fun toPayload(): String {
            return "SNIFF|START|SSID=$ssid|BSSID=$bssid|CHANNEL=$channel"
        }
    }

    object SendSniffStop : Command() {
        override fun toPayload(): String {
            return "SNIFF|STOP"
        }
    }



    // DEAUTH
    data class SendStartDeauth(
        val targetMac: String,
        val apMac: String,
        val channel: Int
    ) : Command() {
        override fun toPayload(): String {
            return "DEAUTH|START|TARGET=$targetMac|AP=$apMac|CHANNEL=$channel"
        }
    }

    object SendStopDeauth : Command() {
        override fun toPayload(): String {
            return "DEAUTH|STOP"
        }
    }

    // BEACON FRAME SPAM
    data class SendStartBeacon(
        val channel: Int,
        val ssids: List<String>
    ) : Command() {
        override fun toPayload(): String {
            // Serialize SSIDs with a separator that won't appear in SSIDs
            val ssidString = ssids.joinToString(separator = "~")
            return "BEACON|START|CHANNEL=$channel|SSIDS=$ssidString"
        }
    }

    object SendStopBeacon : Command() {
        override fun toPayload(): String {
            return "BEACON|STOP"
        }
    }

    // MAC
    object SendClearMac : Command() {
        override fun toPayload(): String {
            return "MAC|CLEAR"
        }
    }

    // WIFI
    object SendClearWifi : Command() {
        override fun toPayload(): String {
            return "WIFI|CLEAR"
        }
    }
}