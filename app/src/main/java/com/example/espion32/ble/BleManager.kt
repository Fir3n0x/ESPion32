package com.example.espion32.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import com.example.espion32.model.BleEvent
import com.example.espion32.model.Command
import com.example.espion32.model.MAC
import com.example.espion32.model.PcapTransferState
import com.example.espion32.pcap.PcapManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

@SuppressLint("MissingPermission")
class BleManager(
    private val context: Context
) {

    // Un SEUL PcapManager, partagé entre la réception BLE et la librairie UI.
    val pcapManager = PcapManager(context)

    private val bluetoothAdapter =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private var gatt: BluetoothGatt? = null
    private var isConnected = false

    private var currentTargetSsid: String = "unknown"

    // Reconnexion ciblée (status 133)
    private var lastDevice: BluetoothDevice? = null
    private var connectRetries = 0
    private val maxConnectRetries = 3

    private val mainHandler = Handler(Looper.getMainLooper())

    private val serviceUUID =
        UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")

    // Android -> ESP32 (WRITE)
    private val cmdUUID =
        UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")

    // ESP32 -> Android (NOTIFY)
    private val statusUUID =
        UUID.fromString("9d8c2d3a-7a12-4d3f-8f58-bc6b4f9c1123")

    // Descriptor
    private val cccdUUID =
        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // Handle mac event
    private val _macEvents = MutableStateFlow<List<MAC>>(emptyList())
    val macEvents: StateFlow<List<MAC>> = _macEvents

    // Handle attack logs sniffer
    private val _attackLogsSniffer = MutableStateFlow<List<String>>(emptyList())
    val attackLogsSniffer: StateFlow<List<String>> = _attackLogsSniffer.asStateFlow()

    // Handle attack logs deauth
    private val _attackLogsDeauth = MutableStateFlow<List<String>>(emptyList())
    val attackLogsDeauth: StateFlow<List<String>> = _attackLogsDeauth.asStateFlow()

    // Handle attack logs evil twin
    private val _attackLogsEvilTwin = MutableStateFlow<List<String>>(emptyList())
    val attackLogsEvilTwin: StateFlow<List<String>> = _attackLogsEvilTwin.asStateFlow()

    // Handle status (STARTED, STOPPED, ERROR, etc.)
    private val _statusEvents = MutableStateFlow<String?>(null)
    val statusEvents: StateFlow<String?> = _statusEvents.asStateFlow()

    // État "attaque deauth/capture en cours" piloté par les ACK STATUS de l'ESP32
    private val _deauthRunning = MutableStateFlow(false)
    val deauthRunning: StateFlow<Boolean> = _deauthRunning.asStateFlow()

    // Handle PCAP transfer
    private val _pcapEvents = MutableStateFlow<PcapTransferState>(PcapTransferState.Idle)
    val pcapEvents: StateFlow<PcapTransferState> = _pcapEvents.asStateFlow()

    private val _savedCaptures = MutableStateFlow<List<File>>(emptyList())
    val savedCaptures: StateFlow<List<File>> = _savedCaptures.asStateFlow()

    // Handle Discovered BLE devices
    val devices = MutableStateFlow<List<BluetoothDevice>>(emptyList())

    // Handle BLE connection state
    val connectionEvents = MutableStateFlow<BleConnectionState>(BleConnectionState.Idle)

    fun setCurrentTargetSsid(ssid: String) {
        currentTargetSsid = ssid
    }

    // ==========================================================================
    //  FILE D'ATTENTE GATT (une opération à la fois)
    // ==========================================================================
    // Android n'autorise qu'UNE opération GATT en vol. On sérialise write /
    // writeDescriptor : la suivante n'est lancée qu'au callback de complétion
    // (ou après timeout de sécurité).
    private class GattOp(val desc: String, val run: () -> Boolean)

    private val opQueue = ArrayDeque<GattOp>()
    private var opInProgress = false
    private val opLock = Any()
    private var opTimeout: Runnable? = null

    private fun enqueueOp(desc: String, run: () -> Boolean) {
        synchronized(opLock) {
            opQueue.addLast(GattOp(desc, run))
            if (!opInProgress) executeNextLocked()
        }
    }

    // Doit être appelé en tenant opLock
    private fun executeNextLocked() {
        val op = if (opQueue.isEmpty()) null else opQueue.removeFirst()
        if (op == null) {
            opInProgress = false
            return
        }
        opInProgress = true

        val timeout = Runnable {
            Log.w("BLE", "[W] GATT op timeout: ${op.desc}")
            onOpComplete()
        }
        opTimeout = timeout
        mainHandler.postDelayed(timeout, 3000)

        val started = try {
            op.run()
        } catch (e: Exception) {
            Log.e("BLE", "[F] GATT op exception (${op.desc}): ${e.message}")
            false
        }

        if (!started) {
            Log.e("BLE", "[F] GATT op failed to start: ${op.desc}")
            mainHandler.removeCallbacks(timeout)
            opTimeout = null
            executeNextLocked() // passe à la suivante
        }
    }

    private fun onOpComplete() {
        synchronized(opLock) {
            opTimeout?.let { mainHandler.removeCallbacks(it) }
            opTimeout = null
            opInProgress = false
            executeNextLocked()
        }
    }

    private fun clearOpQueue() {
        synchronized(opLock) {
            opTimeout?.let { mainHandler.removeCallbacks(it) }
            opTimeout = null
            opQueue.clear()
            opInProgress = false
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScan() {
        // Check permissions based on Android version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ requires BLUETOOTH_SCAN
            if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
                Log.e("BLE", "[W] Permission BLUETOOTH_SCAN missing!")
                return
            }
        } else {
            // Android < 12 requires LOCATION permission for BLE scan
            if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                Log.e("BLE", "[W] Permission ACCESS_FINE_LOCATION missing!")
                return
            }
        }

        bluetoothAdapter.bluetoothLeScanner.startScan(scanCallback)
        Log.d("BLE", "[I] Scan started")
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScan() {
        bluetoothAdapter.bluetoothLeScanner.stopScan(scanCallback)
        Log.d("BLE", "[I] Scan stopped")
    }

    private val scanCallback = object : ScanCallback() {
        @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
        override fun onScanResult(type: Int, result: ScanResult) {
            // Check CONNECT permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return
            }

            val device = result.device
            val deviceName = device.name ?: "Unknown"

            Log.d("BLE", "Found device: $deviceName (RSSI: ${result.rssi})")

            if (devices.value.none { it.address == device.address }) {
                devices.value += device
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("BLE", "[F] Scan failed: $errorCode")
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                Log.e("BLE", "[W] Permission BLUETOOTH_CONNECT missing!")
                return
            }
        }

        lastDevice = device
        connectRetries = 0
        doConnect(device)
    }

    @SuppressLint("MissingPermission")
    private fun doConnect(device: BluetoothDevice) {
        // Close any existing connection + reset the op queue
        clearOpQueue()
        gatt?.close()
        gatt = null

        // Small delay to let the BLE stack reset
        mainHandler.postDelayed({
            Log.d("BLE", "[I] Connecting to device... (try ${connectRetries + 1})")

            // autoConnect = false : connexion initiale rapide et déterministe.
            // La reconnexion est gérée explicitement (voir status 133).
            gatt = device.connectGatt(
                context,
                false,
                gattCallback,
                BluetoothDevice.TRANSPORT_LE
            )
        }, 500)
    }

    private val gattCallback = object : BluetoothGattCallback() {

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return
            }

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e("BLE", "[F] Connection failed: status=$status")
                isConnected = false
                clearOpQueue()
                gatt.close()
                this@BleManager.gatt = null

                // status 133 (GATT_ERROR) : erreur transitoire fréquente -> retry
                val dev = lastDevice
                if (status == 133 && dev != null && connectRetries < maxConnectRetries) {
                    connectRetries++
                    Log.w("BLE", "[W] status 133 -> retry $connectRetries/$maxConnectRetries")
                    connectionEvents.value = BleConnectionState.Connecting
                    mainHandler.postDelayed({ doConnect(dev) }, 600L * connectRetries)
                } else {
                    connectionEvents.value =
                        BleConnectionState.Error("Connection failed (status=$status)")
                }
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectionEvents.value = BleConnectionState.Connected
                    Log.d("BLE", "[I] Connected, requesting MTU...")
                    isConnected = true
                    gatt.requestMtu(384)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connectionEvents.value = BleConnectionState.Disconnected
                    Log.d("BLE", "[I] Disconnected")
                    isConnected = false
                    _deauthRunning.value = false
                    clearOpQueue()
                    gatt.close()
                    this@BleManager.gatt = null
                }
                else -> {
                    Log.d("BLE", "[I] Connection state=$newState")
                }
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                connectionEvents.value = BleConnectionState.MtuRequested
                Log.d("BLE", "[I] MTU negotiated: $mtu")
                gatt.discoverServices()
            } else {
                connectionEvents.value = BleConnectionState.Error("MTU failed")
                Log.e("BLE", "[F] MTU request failed")
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return
            }

            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(serviceUUID)
                if (service == null) {
                    connectionEvents.value = BleConnectionState.Error("Service not found.")
                    Log.e("BLE", "[F] Service not found!")
                    return
                }

                connectionEvents.value = BleConnectionState.ServicesDiscovered
                Log.d("BLE", "[I] Services discovered!")

                val statusChar = service.getCharacteristic(statusUUID)
                if (statusChar == null) {
                    connectionEvents.value = BleConnectionState.Error("Status not found.")
                    Log.e("BLE", "[F] STATUS characteristic not found!")
                    return
                }

                gatt.setCharacteristicNotification(statusChar, true)

                val cccd = statusChar.getDescriptor(cccdUUID)
                if (cccd == null) {
                    connectionEvents.value = BleConnectionState.Error("CCCD descriptor not found.")
                    Log.e("BLE", "[F] CCCD descriptor not found!")
                    return
                }

                // Écriture du CCCD via la file GATT
                enqueueOp("enable-notifications") {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ==
                            BluetoothStatusCodes.SUCCESS
                    } else {
                        @Suppress("DEPRECATION")
                        cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        @Suppress("DEPRECATION")
                        gatt.writeDescriptor(cccd)
                    }
                }
                Log.d("BLE", "[I] Notifications enable queued")
            } else {
                connectionEvents.value = BleConnectionState.Error("Service discovery failed")
                Log.e("BLE", "[F] Service discovery failed: $status")
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BLE", "[I] Descriptor written successfully")
                connectionEvents.value = BleConnectionState.Ready
                connectRetries = 0
            } else {
                Log.e("BLE", "[F] Descriptor write failed: $status")
                connectionEvents.value = BleConnectionState.Error("Descriptor write failed")
            }
            onOpComplete()
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BLE", "[I] Characteristic written successfully")
            } else {
                Log.e("BLE", "[F] Characteristic write failed: $status")
            }
            onOpComplete()
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid != statusUUID) return

            val raw = value.toString(Charsets.UTF_8)
            Log.d("BLE", "[I] NOTIFY: $raw")

            when (val event = parseBleMessage(raw)) {

                is BleEvent.Log -> {
                    when (event.subtype) {
                        "SNIFF" -> _attackLogsSniffer.value += event.message
                        "DEAUTH", "STEAL" -> _attackLogsDeauth.value += event.message
                        "EVILTWIN" -> _attackLogsEvilTwin.value += event.message
                    }
                }

                is BleEvent.PcapStart -> {
                    pcapManager.onPcapStart(event.totalSize, event.frameCount)
                    _pcapEvents.value = PcapTransferState.Receiving(0, event.totalSize)
                }

                is BleEvent.PcapChunk -> {
                    // Pas de double base64 : on passe le ByteArray tel quel
                    pcapManager.onPcapChunk(event.index, event.data)
                    _pcapEvents.value = PcapTransferState.Receiving(event.index, 0)
                }

                is BleEvent.PcapEnd -> {
                    val result = pcapManager.onPcapEnd(event.crc, currentTargetSsid)
                    val file = result.file
                    if (file != null) {
                        _pcapEvents.value = PcapTransferState.Done(file.absolutePath)
                        _savedCaptures.value = pcapManager.listCaptures()
                        if (result.missingChunks.isEmpty() && result.crcOk) {
                            _attackLogsDeauth.value += "[i] PCAP saved: ${file.name}"
                        } else {
                            _attackLogsDeauth.value +=
                                "[!] PCAP PARTIEL saved: ${file.name} (crcOk=${result.crcOk}, manquants=${result.missingChunks.size})"
                        }
                    } else {
                        _pcapEvents.value = PcapTransferState.Error("Transfert échoué")
                        _attackLogsDeauth.value += "❌ PCAP transfer failed"
                    }
                }

                is BleEvent.MacFound -> {
                    if (event.subtype == "SNIFF") {
                        val macEvent = MAC(
                            mac = event.mac,
                            rssi = event.rssi,
                            channel = event.channel
                        )
                        _macEvents.value =
                            (_macEvents.value + macEvent).distinctBy { it.mac }
                    }
                }

                is BleEvent.Status -> {
                    _statusEvents.value = "${event.subtype}:${event.value}"
                    // Pilote l'état d'attaque à partir des ACK de l'ESP32
                    if (event.subtype == "DEAUTH") {
                        when (event.value.uppercase()) {
                            "STARTED" -> _deauthRunning.value = true
                            "STOPPED" -> _deauthRunning.value = false
                        }
                    }
                }

                is BleEvent.Error -> {
                    when (event.subtype) {
                        "SNIFF" -> _attackLogsSniffer.value += "[ERROR] ${event.message}"
                        "DEAUTH", "STEAL" -> _attackLogsDeauth.value += "[ERROR] ${event.message}"
                        "EVILTWIN" -> _attackLogsEvilTwin.value += "[ERROR] ${event.message}"
                    }
                }

                is BleEvent.Unknown -> {
                    // ignore
                }
            }
        }

        // Deprecated but still called on older devices
        @Deprecated("Deprecated in Android 13")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            @Suppress("DEPRECATION")
            onCharacteristicChanged(gatt, characteristic, characteristic.value)
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun sendCommand(cmd: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                Log.e("BLE", "[W] Permission BLUETOOTH_CONNECT missing!")
                return
            }
        }

        if (!isConnected) {
            Log.e("BLE", "[F] Not connected!")
            return
        }

        val service = gatt?.getService(serviceUUID)
        if (service == null) {
            Log.e("BLE", "[F] Service not found!")
            return
        }

        val cmdChar = service.getCharacteristic(cmdUUID)
        if (cmdChar == null) {
            Log.e("BLE", "[F] CMD characteristic not found!")
            return
        }

        val data = cmd.toByteArray()
        val writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

        // Écriture mise en file (une opération GATT à la fois)
        enqueueOp("cmd:$cmd") {
            val g = gatt ?: return@enqueueOp false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeCharacteristic(cmdChar, data, writeType) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                cmdChar.value = data
                @Suppress("DEPRECATION")
                cmdChar.writeType = writeType
                @Suppress("DEPRECATION")
                g.writeCharacteristic(cmdChar)
            }
        }

        Log.d("BLE", "[I] CMD queued: $cmd")
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun sendCommand(cmd: Command) {
        sendCommand(cmd.toPayload())
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnect() {
        clearOpQueue()
        // On demande la déconnexion ; le close() sera fait dans le callback
        // onConnectionStateChange(DISCONNECTED) pour éviter la course qui
        // masquait ce callback.
        val g = gatt
        if (g != null) {
            g.disconnect()
            // Filet de sécurité : si aucun callback n'arrive, on ferme.
            mainHandler.postDelayed({
                if (!isConnected) {
                    try { g.close() } catch (_: Exception) {}
                    if (gatt === g) gatt = null
                }
            }, 1500)
        }
        isConnected = false
        _deauthRunning.value = false
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun resetSession() {
        Log.d("BLE", "[I] Reset BLE session")
        disconnect()
        connectionEvents.value = BleConnectionState.Idle
        _macEvents.value = emptyList()
        devices.value = emptyList()
    }

    fun refreshCaptures() {
        _savedCaptures.value = pcapManager.listCaptures()
    }

    fun clearMacDisplayed() {
        _macEvents.value = emptyList()
    }

    fun pushLocalLogSniffer(msg: String) {
        _attackLogsSniffer.value += msg
    }

    fun pushLocalLogDeauth(msg: String) {
        _attackLogsDeauth.value += msg
    }

    fun pushLocalLogEvilTwin(msg: String) {
        _attackLogsEvilTwin.value += msg
    }

    fun clearSnifferLogs() {
        _attackLogsSniffer.value = emptyList()
    }

    fun clearSnifferDeauthLogs() {
        _attackLogsDeauth.value = emptyList()
    }

    fun clearEvilTwinLogs() {
        _attackLogsEvilTwin.value = emptyList()
    }

    private fun hasPermission(permission: String): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    // Parse received notification (délègue à l'objet pur, testable en JVM)
    private fun parseBleMessage(raw: String): BleEvent =
        BleMessageParser.parse(raw) { android.util.Base64.decode(it, android.util.Base64.NO_WRAP) }

}
