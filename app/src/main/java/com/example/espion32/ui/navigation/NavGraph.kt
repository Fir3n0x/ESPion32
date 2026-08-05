package com.example.espion32.ui.navigation

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.espion32.ble.BleConnectionState
import com.example.espion32.ui.screens.wifi.ConnectedScreen
import com.example.espion32.ui.screens.ble.ConnectingScreen
import com.example.espion32.ui.screens.ble.DeviceScreen
import com.example.espion32.ui.screens.LoginScreen
import com.example.espion32.ui.screens.pcap.PcapLibraryScreen
import com.example.espion32.ui.screens.wifi.BFSScreen
import com.example.espion32.ui.screens.wifi.DeauthScreen
import com.example.espion32.ui.screens.wifi.EvilTwinScreen
import com.example.espion32.ui.screens.wifi.SnifferScreen
import com.example.espion32.viewmodel.BleViewModel
import com.example.espion32.viewmodel.WifiViewModel

@SuppressLint("MissingPermission")
@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    // ViewModel state to share with screens
    val bleViewModel: BleViewModel = viewModel()
    val wifiViewModel: WifiViewModel = viewModel()

    // Track previous route to avoid unnecessary resets
    val previousRoute = remember { mutableStateOf<String?>(null) }

    // Handle snackbar
    val snackbarHostState = remember { SnackbarHostState() }

    // Observe snackbar events
    LaunchedEffect(Unit) {
        bleViewModel.uiEvents.collect { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
        }
    }

    // Handle ble shut down
    val state by bleViewModel.connectionState.collectAsState()

    LaunchedEffect(state) {
        when (state) {
            is BleConnectionState.Error -> {
                Log.e("BLE", "Error state detected: $state")

                bleViewModel.handleBleDisconnect()
                wifiViewModel.resetAll()

                navController.navigate("login") {
                    popUpTo(0) { inclusive = true }
                }
            }

            is BleConnectionState.Disconnected -> {
                val currentRoute = navController.currentDestination?.route

                // Only handle if we're NOT already on login/connecting
                // AND we haven't already handled an error
                if (currentRoute !in listOf("login", "connecting", "devices")) {
                    Log.w("BLE", "Unexpected disconnect from route: $currentRoute")

                    bleViewModel.handleBleDisconnect()
                    wifiViewModel.resetAll()

                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }

            else -> {}
        }
    }

    // Disconnect from BLE only when manually going back to login
    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            val currentRoute = destination.route
            val wasConnectedOrConnecting = previousRoute.value in listOf("connected", "connecting")

            // Only reset if:
            // 1. Coming to login from connected/connecting screens
            // 2. AND connection is not in error state (error already handles cleanup)
            if (currentRoute == "login" &&
                wasConnectedOrConnecting) {
                bleViewModel.bleManager.resetSession()
                bleViewModel.clearSelection()
            }

            previousRoute.value = currentRoute
        }

        navController.addOnDestinationChangedListener(listener)

        onDispose {
            navController.removeOnDestinationChangedListener(listener)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "login",
            modifier = Modifier.padding(padding)
        ) {
            composable("login") {
                LoginScreen(
                    navController = navController,
                    viewModel = bleViewModel
                )
            }
            composable("devices") {
                DeviceScreen(
                    navController = navController,
                    viewModel = bleViewModel
                )
            }
            composable("connecting") {
                ConnectingScreen(
                    navController = navController,
                    viewModel = bleViewModel
                )
            }
            composable("connected") {
                ConnectedScreen(
                    navController = navController,
                    bleViewModel = bleViewModel,
                    wifiViewModel = wifiViewModel
                )
            }
            composable("sniffer") {
                SnifferScreen(
                    navController = navController,
                    bleViewModel = bleViewModel,
                    wifiViewModel = wifiViewModel
                )
            }
            composable("deauth") {
                DeauthScreen(
                    navController = navController,
                    bleViewModel = bleViewModel,
                    wifiViewModel = wifiViewModel
                )
            }
            composable("beacon") {
                BFSScreen(
                    navController = navController,
                    bleViewModel = bleViewModel,
                    wifiViewModel = wifiViewModel
                )
            }
            composable("eviltwin") {
                EvilTwinScreen(
                    navController = navController,
                    bleViewModel = bleViewModel,
                    wifiViewModel = wifiViewModel
                )
            }
            composable("pcap_library") {
                PcapLibraryScreen(navController = navController, bleViewModel = bleViewModel)
            }
        }
    }
}