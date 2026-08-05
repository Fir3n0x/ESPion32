package com.example.espion32.pcap

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.CRC32

/**
 * Résultat d'un transfert PCAP.
 * - [file] : fichier écrit (peut être une capture PARTIELLE si des chunks manquent).
 * - [crcOk] : vrai si le CRC32 reçu correspond aux octets assemblés.
 * - [missingChunks] : indices de chunks manquants (trous détectés).
 */
data class PcapResult(
    val file: File?,
    val crcOk: Boolean,
    val missingChunks: List<Int>
)

class PcapManager(private val context: Context) {

    private val TAG = "PcapManager"

    // Reconstruction buffer
    private var expectedSize: Int = 0
    private var expectedFrames: Int = 0
    private var chunks: MutableMap<Int, ByteArray> = mutableMapOf()
    private var isReceiving = false
    private var maxIndex = -1

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
        maxIndex = -1
        expectedSize = totalSize
        expectedFrames = frameCount
        isReceiving = true
    }

    fun onPcapChunk(index: Int, data: ByteArray) {
        if (!isReceiving) return
        chunks[index] = data
        if (index > maxIndex) maxIndex = index
        Log.d(TAG, "Chunk $index received (${data.size} bytes)")
    }

    fun onPcapEnd(crc: Long, ssid: String = "unknown"): PcapResult {
        if (!isReceiving) return PcapResult(null, false, emptyList())
        isReceiving = false

        // Détection de trous : tout index de 0..maxIndex absent est manquant
        val missing = if (maxIndex >= 0) {
            (0..maxIndex).filter { !chunks.containsKey(it) }
        } else emptyList()

        // Réassemblage dans l'ordre des index
        val sortedChunks = chunks.toSortedMap()
        val totalBytes = sortedChunks.values.sumOf { it.size }
        val buffer = ByteArray(totalBytes)
        var offset = 0
        for ((_, chunk) in sortedChunks) {
            chunk.copyInto(buffer, offset)
            offset += chunk.size
        }

        // CRC32 en 32 bits NON SIGNÉS (crc32.value est déjà 0..2^32-1)
        val crc32 = CRC32()
        crc32.update(buffer)
        val computedCrc = crc32.value  // Long
        val crcOk = missing.isEmpty() && computedCrc == crc

        if (!crcOk) {
            Log.w(
                TAG,
                "Transfert imparfait: crcOk=$crcOk " +
                    "(reçu=0x${crc.toString(16)} calc=0x${computedCrc.toString(16)}), " +
                    "manquants=${missing.size}, taille=${buffer.size}/${expectedSize}"
            )
        }

        // On sauvegarde MÊME en cas de trous / CRC KO : l'opérateur garde ce qui
        // est arrivé (souvent exploitable partiellement). Le nom marque le doute.
        val partial = !crcOk
        val file = saveToFile(buffer, ssid, partial)
        return PcapResult(file, crcOk, missing)
    }

    private fun saveToFile(data: ByteArray, ssid: String, partial: Boolean): File? {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(Date())
            val cleanSsid = ssid.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(32)
            val suffix = if (partial) "_PARTIAL" else ""
            val file = File(pcapDir, "${cleanSsid}_$timestamp$suffix.pcap")

            FileOutputStream(file).use { it.write(data) }
            Log.d(TAG, "PCAP saved: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save PCAP: ${e.message}")
            null
        }
    }
}
