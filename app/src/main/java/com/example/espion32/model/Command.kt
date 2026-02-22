package com.example.espion32.model

import com.example.espion32.ui.screens.wifi.AttackMode
import com.example.espion32.ui.screens.wifi.HarvestMethod

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
        val channel: Int,
        val attackMode: AttackMode
    ) : Command() {
        override fun toPayload(): String {
            return "DEAUTH|START|TARGET=$targetMac|AP=$apMac|CHANNEL=$channel|ATTACKMODE=${idForAttackMode(attackMode)}"
        }
    }

    data class SendStartTestDeauth(
        val targetMac: String,
        val apMac: String,
        val channel: Int
    ) : Command() {
        override fun toPayload(): String {
            return "DEAUTH|TEST|TARGET=$targetMac|AP=$apMac|CHANNEL=$channel"
        }
    }

    object SendStopDeauth : Command() {
        override fun toPayload(): String {
            return "DEAUTH|STOP"
        }
    }

    fun idForAttackMode(attackMode: AttackMode): String =
        when(attackMode) { // Transform AttackMode class to id
            AttackMode.DEAUTH -> "1"
            AttackMode.AUTH_STEALER -> "2"
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

    // EVIL TWIN
    data class SendStartEvilTwin(
        val harvestMethod: HarvestMethod,
        val nameEvilTwin: String
    ) : Command() {
        override fun toPayload(): String {
            return "EVILTWIN|START|METHOD=${idForHarvestMethod(harvestMethod)}|NAME=$nameEvilTwin"
        }
    }

    object SendStopEvilTwin : Command() {
        override fun toPayload(): String {
            return "EVILTWIN|STOP"
        }
    }

    fun idForHarvestMethod(harvestMethod: HarvestMethod): String =
        when(harvestMethod) { // Transform HarvestMethod class to id
            HarvestMethod.LOGIN_KEY -> "1"
            HarvestMethod.CAPTIVE_PORTAL -> "2"
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