package com.example.espion32

import com.example.espion32.model.Command
import com.example.espion32.ui.screens.wifi.AttackMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Vérifie le protocole texte envoyé à l'ESP32 (toPayload). Ces tests figent le
 * format que le firmware parse : toute dérive casse la compat app <-> firmware.
 */
class CommandTest {

    @Test
    fun sniffStart_payload() {
        assertEquals(
            "SNIFF|START|SSID=MyNet|BSSID=AA:BB:CC:DD:EE:FF|CHANNEL=6",
            Command.SendSniffStart("MyNet", "AA:BB:CC:DD:EE:FF", 6).toPayload()
        )
    }

    @Test
    fun sniffStop_payload() {
        assertEquals("SNIFF|STOP", Command.SendSniffStop.toPayload())
    }

    @Test
    fun deauth_attackModeIds() {
        val target = "11:22:33:44:55:66"
        val ap = "AA:BB:CC:DD:EE:FF"
        fun payload(mode: AttackMode) =
            Command.SendStartDeauth(target, ap, 11, mode).toPayload()

        assertEquals(
            "DEAUTH|START|TARGET=$target|AP=$ap|CHANNEL=11|ATTACKMODE=1",
            payload(AttackMode.DEAUTH)
        )
        assertEquals(
            "DEAUTH|START|TARGET=$target|AP=$ap|CHANNEL=11|ATTACKMODE=2",
            payload(AttackMode.AUTH_STEALER)
        )
        assertEquals(
            "DEAUTH|START|TARGET=$target|AP=$ap|CHANNEL=11|ATTACKMODE=3",
            payload(AttackMode.PASSIVE_CAPTURE)
        )
        assertEquals(
            "DEAUTH|START|TARGET=$target|AP=$ap|CHANNEL=11|ATTACKMODE=4",
            payload(AttackMode.PMKID)
        )
    }

    @Test
    fun deauthTest_payload() {
        assertEquals(
            "DEAUTH|TEST|TARGET=11:22:33:44:55:66|AP=AA:BB:CC:DD:EE:FF|CHANNEL=1",
            Command.SendStartTestDeauth("11:22:33:44:55:66", "AA:BB:CC:DD:EE:FF", 1).toPayload()
        )
    }

    @Test
    fun deauthStop_payload() {
        assertEquals("DEAUTH|STOP", Command.SendStopDeauth.toPayload())
    }

    @Test
    fun beaconStart_joinsSsidsWithTilde() {
        assertEquals(
            "BEACON|START|CHANNEL=3|SSIDS=Net_A~Net_B~Net_C",
            Command.SendStartBeacon(3, listOf("Net_A", "Net_B", "Net_C")).toPayload()
        )
    }

    @Test
    fun clearCommands_payload() {
        assertEquals("MAC|CLEAR", Command.SendClearMac.toPayload())
        assertEquals("WIFI|CLEAR", Command.SendClearWifi.toPayload())
    }
}
