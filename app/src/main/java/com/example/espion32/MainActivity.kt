package com.example.espion32

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.espion32.ui.navigation.AppNavigation
import com.example.espion32.ui.theme.MiniMap32Theme

val autowide = FontFamily(
    Font(R.font.audiowide_regular, FontWeight.Normal)
)

class MainActivity : ComponentActivity() {

    private lateinit var bluetoothAdapter: BluetoothAdapter

    // Request MULTIPLE permissions at once
    @SuppressLint("MissingPermission")
    private val requestBluetoothPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            android.util.Log.d("MainActivity", "Permission results: $permissions")
            permissions.forEach { (perm, granted) ->
                android.util.Log.d("MainActivity", "  $perm: $granted")
            }

            // Le BLE peut fonctionner même si la localisation est refusée.
            val btOk = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED
            } else true

            if (btOk) {
                initBluetooth()
            } else {
                toast("Bluetooth refusé : impossible de piloter l'ESP32.")
            }

            // La localisation FINE est requise pour le scan WiFi.
            val locOk = checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
            if (!locOk) {
                toast("Localisation refusée : le scan WiFi renverra une liste vide.")
            }
        }

    private fun toast(msg: String) {
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MiniMap32Theme{
                AppNavigation()
            }
        }

        requestNeededPermissions()
    }

    private fun requestNeededPermissions() {
        val needed = mutableListOf<String>()

        // Android 12+ : permissions BLE dédiées
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) needed.add(android.Manifest.permission.BLUETOOTH_CONNECT)

            if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED
            ) needed.add(android.Manifest.permission.BLUETOOTH_SCAN)
        }

        // ACCESS_FINE_LOCATION : indispensable au scan WiFi (scanResults) sur
        // TOUS les niveaux d'API (et au scan BLE < Android 12).
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) needed.add(android.Manifest.permission.ACCESS_FINE_LOCATION)

        if (needed.isNotEmpty()) {
            requestBluetoothPermissions.launch(needed.toTypedArray())
        } else {
            initBluetooth()
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun initBluetooth() {
        val bluetoothManager =
            getSystemService(BLUETOOTH_SERVICE) as BluetoothManager

        bluetoothAdapter = bluetoothManager.adapter

        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent =
                Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivity(enableBtIntent)
        }
    }
}