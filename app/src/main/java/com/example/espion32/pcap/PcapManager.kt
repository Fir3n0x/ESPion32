package com.example.espion32.pcap

import android.content.Context
import android.os.Environment
import android.util.Base64
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.CRC32

class PcapManager(private val context: Context) {

    private val TAG = "PcapManager"

    // Reconstruction buffer
    private var expectedSize: Int = 0
    private var chunks: MutableMap<Int, ByteArray> = mutableMapOf()
    private var isReceiving = false
    private var expectedCrc: Int = 0

    // Storage folder
    private val pcapDir: File by lazy {
        File(context.getExternalFilesDir(null), "captures").also {
            if (!it.exists()) it.mkdirs()
        }
    }

    // List captured files
    fun listCaptures(): List<File> {
        return pcapDir.listFiles { f -> f.extension == "pcap" }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    fun onPcapStart(totalSize: Int, frameCount: Int) {
        Log.d(TAG, "PCAP transfer started: $totalSize bytes, $frameCount frames")
        chunks.clear()
        expectedSize = totalSize
        isReceiving = true
    }

    fun onPcapChunk(index: Int, base64Data: String) {
        if (!isReceiving) return
        val bytes = Base64.decode(base64Data, Base64.NO_WRAP)
        chunks[index] = bytes
        Log.d(TAG, "Chunk $index received (${bytes.size} bytes)")
    }

    fun onPcapEnd(crc: Int): File? {
        if (!isReceiving) return null
        isReceiving = false
        expectedCrc = crc

        // Rebuild in order
        val sortedChunks = chunks.toSortedMap()
        val totalBytes = sortedChunks.values.sumOf { it.size }
        val buffer = ByteArray(totalBytes)
        var offset = 0
        for ((_, chunk) in sortedChunks) {
            chunk.copyInto(buffer, offset)
            offset += chunk.size
        }

        // Check CRC
        val crc32 = CRC32()
        crc32.update(buffer)
        val computedCrc = crc32.value.toInt()

        if (computedCrc != crc) {
            Log.e(TAG, "CRC mismatch! expected=0x${crc.toString(16)} got=0x${computedCrc.toString(16)}")
            return null
        }

        // Save
        return saveToFile(buffer)
    }

    private fun saveToFile(data: ByteArray): File? {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(Date())
            val file = File(pcapDir, "handshake_$timestamp.pcap")

            FileOutputStream(file).use { it.write(data) }
            Log.d(TAG, "PCAP saved: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save PCAP: ${e.message}")
            null
        }
    }
}