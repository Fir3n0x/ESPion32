package com.example.espion32

import com.example.espion32.ble.BleMessageParser
import com.example.espion32.model.BleEvent
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BleMessageParserTest {

    // Décodeur base64 JVM (android.util.Base64 n'existe pas en test unitaire)
    private val b64 = { s: String -> java.util.Base64.getDecoder().decode(s) }

    private fun parse(raw: String) = BleMessageParser.parse(raw, b64)

    @Test
    fun log_isParsed() {
        val e = parse("LOG|STEAL|msg=Hello world")
        assertTrue(e is BleEvent.Log)
        e as BleEvent.Log
        assertEquals("STEAL", e.subtype)
        assertEquals("Hello world", e.message)
    }

    @Test
    fun mac_isParsed() {
        val e = parse("MAC|SNIFF|mac=AA:BB:CC:DD:EE:FF|rssi=-42|ch=6")
        assertTrue(e is BleEvent.MacFound)
        e as BleEvent.MacFound
        assertEquals("AA:BB:CC:DD:EE:FF", e.mac)
        assertEquals(-42, e.rssi)
        assertEquals(6, e.channel)
    }

    @Test
    fun mac_missingFields_isUnknown() {
        assertTrue(parse("MAC|SNIFF|mac=AA:BB:CC:DD:EE:FF") is BleEvent.Unknown)
    }

    @Test
    fun status_isParsed() {
        val e = parse("STATUS|DEAUTH|value=STARTED")
        assertTrue(e is BleEvent.Status)
        e as BleEvent.Status
        assertEquals("DEAUTH", e.subtype)
        assertEquals("STARTED", e.value)
    }

    @Test
    fun pcapStart_isParsed() {
        val e = parse("PCAP|START|size=1024|frames=12")
        assertTrue(e is BleEvent.PcapStart)
        e as BleEvent.PcapStart
        assertEquals(1024, e.totalSize)
        assertEquals(12, e.frameCount)
    }

    @Test
    fun pcapChunk_isDecoded() {
        // "AAAA" en base64 => 3 octets 0x00
        val e = parse("PCAP|CHUNK|3|AAAA")
        assertTrue(e is BleEvent.PcapChunk)
        e as BleEvent.PcapChunk
        assertEquals(3, e.index)
        assertArrayEquals(byteArrayOf(0, 0, 0), e.data)
    }

    /**
     * Régression du bug CRC : un CRC > 0x7FFFFFFF doit être parsé correctement.
     * Avec l'ancien `toIntOrNull(16)`, "FFFFFFFF" débordait -> null -> 0.
     */
    @Test
    fun pcapEnd_highCrc_doesNotOverflow() {
        val e = parse("PCAP|END|crc=0xFFFFFFFF")
        assertTrue(e is BleEvent.PcapEnd)
        e as BleEvent.PcapEnd
        assertEquals(0xFFFFFFFFL, e.crc)
    }

    @Test
    fun pcapEnd_lowCrc_isParsed() {
        val e = parse("PCAP|END|crc=0x1A2B3C4D")
        e as BleEvent.PcapEnd
        assertEquals(0x1A2B3C4DL, e.crc)
    }

    @Test
    fun malformed_isUnknown() {
        assertTrue(parse("GARBAGE") is BleEvent.Unknown)
        assertTrue(parse("") is BleEvent.Unknown)
    }
}
