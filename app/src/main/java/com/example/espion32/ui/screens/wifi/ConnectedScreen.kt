package com.example.espion32.ui.screens.wifi

import android.annotation.SuppressLint
import android.graphics.Paint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.espion32.autowide
import com.example.espion32.model.WifiNetwork
import com.example.espion32.model.displayName
import com.example.espion32.viewmodel.BleViewModel
import com.example.espion32.viewmodel.WifiViewModel
import kotlinx.coroutines.delay

// Track where we come from
object ConnectedScreenState {
    var hasScannedOnce by mutableStateOf(false)
    var previousScreen: String? = null
}

@SuppressLint("MissingPermission")
@Composable
fun ConnectedScreen(navController: NavController, bleViewModel: BleViewModel, wifiViewModel: WifiViewModel) {

    // BLE Variables
    val state by bleViewModel.connectionState.collectAsState()

    // WIFI Variables
    val networks by wifiViewModel.networks.collectAsState()
    val selected by wifiViewModel.selectedNetwork.collectAsState()

    // Previous screen
    val previousRoute = navController.previousBackStackEntry?.destination?.route

    BackHandler {
        navController.navigate("login") {
            popUpTo(0)
        }
    }

    // Automatic scan only once
    LaunchedEffect(Unit) {
        // save where we come from
        ConnectedScreenState.previousScreen = previousRoute

        // scan if never done
        if (!ConnectedScreenState.hasScannedOnce) {
            wifiViewModel.scanWifi()
            ConnectedScreenState.hasScannedOnce = true
        }

        // Stop active attack
        delay(200)
        when (previousRoute) {
            "sniffer" -> {
                stopSnifferAttack(bleViewModel)
            }
            "deauth" -> {
                stopDeauthAttack(bleViewModel)
            }
            "beacon" -> {
                // stopBeaconAttack(bleViewModel)
            }
            else -> {
                // if we don't know where we come from
                stopSnifferAttack(bleViewModel)
                delay(1000)
                stopDeauthAttack(bleViewModel)
            }
        }
    }

    // reset
    DisposableEffect(Unit) {
        onDispose {
            // reset flag
            val currentRoute = navController.currentBackStackEntry?.destination?.route
            if (currentRoute == "login") {
                ConnectedScreenState.hasScannedOnce = false
            }
        }
    }

    // Content box
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFCDCDCD))
    ){
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
            // return
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp)
                    .background(Color(0xFF1E2624).copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                    .clickable {
                        navController.navigate("login") {
                            popUpTo(0)
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "<",
                    color = Color.White.copy(alpha = 0.9f),
                    fontFamily = autowide,
                    fontSize = 35.sp
                )
            }

            // title
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp)
            ) {
                Text(
                    text = "Control Panel",
                    color = Color(0xFF363535),
                    fontFamily = autowide,
                    fontSize = 24.sp
                )
            }

            // PCAP library button
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 16.dp)
                    .background(Color(0xFF1E2624).copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                    .clickable { navController.navigate("pcap_library") }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "PCAP",
                    color = Color.White.copy(alpha = 0.9f),
                    fontFamily = autowide,
                    fontSize = 14.sp
                )
            }
        }


        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 80.dp)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Target Network
            Text(
                text = "Target Network",
                color = Color(0xFF363535),
                fontFamily = autowide,
                fontSize = 16.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(Modifier.height(12.dp))

            TargetNetworkRow(wifiViewModel, networks, selected)

            Spacer(Modifier.height(24.dp))

            // Wifi Info collapsible
            WifiInfoSection(selected)

            Spacer(Modifier.height(24.dp))

            AttacksColumn(navController, selected)
        }
    }
}

@Composable
fun TargetNetworkRow(
    wifiViewModel: WifiViewModel,
    networks: List<WifiNetwork>,
    selected: WifiNetwork?
) {
    var expanded by remember { mutableStateOf(false) }
    val isScanning by wifiViewModel.isScanning.collectAsState()

    // Observe refresh button
    val interactionSource = remember { MutableInteractionSource() }
    val isPressedReload by interactionSource.collectIsPressedAsState()
    val isPressedRemove by interactionSource.collectIsPressedAsState()

    LaunchedEffect(selected) {
        if (selected == null) {
            expanded = false
        }
    }


    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        // Dropdown
        Box(
            modifier = Modifier
                .weight(1f)
                .background(Color(0xFF1A1A1A), RoundedCornerShape(6.dp))
                .clickable { expanded = true }
                .padding(12.dp)
        ) {
            Text(
                text = selected?.displayName() ?: "Select a network",
                color = Color.White.copy(alpha = 0.9f),
                fontFamily = autowide
            )

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .heightIn(max = 250.dp)
            ) {
                when {
                    isScanning -> {
                        DropdownMenuItem(
                            text = { Text("Scanning...", color = Color.Gray) },
                            onClick = {}
                        )
                    }

                    networks.isEmpty() -> {
                        DropdownMenuItem(
                            text = { Text("No network found", color = Color.Gray) },
                            onClick = {}
                        )
                    }

                    else -> {
                        networks.forEach { net ->
                            DropdownMenuItem(
                                text = { Text(net.displayName()) },
                                onClick = {
                                    wifiViewModel.selectNetwork(net)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // Clear button
        Box(
            modifier = Modifier
                .padding(start = 8.dp)
                .background(if (isPressedRemove) Color(0xFF3A3A3A) else Color(0xFF1E2624).copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                .clickable { wifiViewModel.clearSelection() }
                .padding(10.dp)
        ) {
            Text("X", color = if(isPressedRemove) Color.Black else Color.White.copy(alpha = 0.4f))
        }

        // Refresh button
        Box(
            modifier = Modifier
                .padding(start = 8.dp)
                .background(if (isPressedReload) Color(0xFF3A3A3A) else Color(0xFF1E2624).copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                .clickable (
                    interactionSource = interactionSource,
                    indication = null
                ){ wifiViewModel.scanWifi() }
                .padding(10.dp)
        ) {
            Text("↻", color = if(isPressedReload) Color.Black else Color.White.copy(alpha = 0.4f))
        }

    }

}

@Composable
fun WifiInfoSection(selected: WifiNetwork?) {

    var infoExpanded by remember { mutableStateOf(false) }

//    LaunchedEffect(selected) {
//        infoExpanded = selected != null
//    }

    if(selected == null) return

    Spacer(Modifier.height(24.dp))

    // Don't show without selecting a network

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                infoExpanded = !infoExpanded
            }
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = if (infoExpanded) "⌄ Wifi Info" else "> Wifi Info",
            color = Color(0xFF363535),
            fontFamily = autowide,
            fontSize = 16.sp
        )
    }


    AnimatedVisibility(visible = infoExpanded) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .background(Color.Gray.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                .border(1.dp, Color(0xFF363535), RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Text("SSID: ${selected.ssid}", color = Color.White.copy(alpha = 0.9f))
            Text("BSSID: ${selected.bssid}", color = Color.White.copy(alpha = 0.9f))
            Text("RSSI: ${selected.level} dBm", color = Color.White.copy(alpha = 0.9f))
            Text("Frequency: ${selected.frequency} MHz", color = Color.White.copy(alpha = 0.9f))
            Text("Channel: ${selected.channel}", color = Color.White.copy(alpha = 0.9f))
        }
    }

}


@Composable
fun AttacksColumn(
    navController: NavController,
    selected: WifiNetwork?
) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .border(1.dp, Color(0xFF1A1A1A), RoundedCornerShape(12.dp))
            .background(Color(0xFF0F0F0F), RoundedCornerShape(12.dp))
    ) {

        // Title
        Text(
            text = "Actions",
            color = Color.White.copy(alpha = 0.9f),
            fontFamily = autowide,
            fontSize = 18.sp,
            modifier = Modifier
                .padding(16.dp)
        )

        // Scrollable
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp) // Visible height
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {

            // Buttons in scrollable zone

            // Require selectedNetwork on
            if (selected != null) {
                // Text
                Text(
                    text = "------ Require a network ------",
                    color = Color.White.copy(alpha = 0.4f),
                    fontFamily = autowide,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentWidth(Alignment.CenterHorizontally)
                )

                ActionButton("Sniffer") { navController.navigate("sniffer") }
                Spacer(Modifier.height(12.dp))

                ActionButton("Deauth") { navController.navigate("deauth") }
                Spacer(Modifier.height(12.dp))
            }

            // Do not require selectedNetwork

            // Text
            Text(
                text = "--- Do not require a network ---",
                color = Color.White.copy(alpha = 0.4f),
                fontFamily = autowide,
                fontSize = 14.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentWidth(Alignment.CenterHorizontally)
            )

            ActionButton("Beacon") { navController.navigate("beacon") }
            Spacer(Modifier.height(12.dp))

            ActionButton("Evil Twin") { navController.navigate("eviltwin") }
            Spacer(Modifier.height(12.dp))

        }
    }
}


@Composable
fun ActionButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E2624), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.9f),
            fontFamily = autowide,
            fontSize = 14.sp
        )
    }
}

@Composable
fun DisplayTargetedNetwork(
    selected: WifiNetwork?
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .background(Color(0xFF1A1A1A), RoundedCornerShape(6.dp))
    ) {
        Text(
            text = if(selected?.ssid != null) selected.displayName() else "Unknown network",
            color = Color.White.copy(alpha = 0.9f),
            fontFamily = autowide,
            fontSize = 14.sp
        )
    }
}


