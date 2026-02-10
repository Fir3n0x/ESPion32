package com.example.espion32.ui.screens.wifi

import android.Manifest
import android.annotation.SuppressLint
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.navigation.NavController
import com.example.espion32.viewmodel.BleViewModel
import com.example.espion32.viewmodel.WifiViewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.espion32.autowide
import com.example.espion32.model.Command
import com.example.espion32.model.WifiNetwork
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@SuppressLint("MissingPermission")
@Composable
fun DeauthScreen(navController: NavController, bleViewModel: BleViewModel, wifiViewModel: WifiViewModel) {

    // WIFI Variables
    val selectedNetwork by wifiViewModel.selectedNetwork.collectAsState()

    // Deauth state
    var targetMac by remember { mutableStateOf("FF:FF:FF:FF:FF:FF") }
    val attackLogs by bleViewModel.attackLogsDeauth.collectAsState()
    var isAttackRunning by remember { mutableStateOf(false) }
    var safetyCheckbox by remember { mutableStateOf(false) }

    // Handle display information
    var lastNetwork by remember { mutableStateOf<WifiNetwork?>(null) }
    var hasInitialized by remember { mutableStateOf(false) }

    // List state
    val listState = rememberLazyListState()
    var autoScrollEnabled by remember { mutableStateOf(true) }

    // Disable auto-scroll while user is scrolling
    var resumeJob by remember { mutableStateOf<Job?>(null) }

    // When page is displayed
    LaunchedEffect(Unit) {
        isAttackRunning = false
        safetyCheckbox = false
    }

    // Detect when user scrolls
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            autoScrollEnabled = false

            // Cancel any pending resume
            resumeJob?.cancel()
        } else {
            // User released scroll → resume auto-scroll after 5s
            resumeJob?.cancel()
            resumeJob = launch {
                delay(5_000) // 5 seconds
                autoScrollEnabled = true
            }
        }
    }

    // Auto-scroll on new logs
    LaunchedEffect(attackLogs.size) {
        if (autoScrollEnabled && attackLogs.isNotEmpty()) {
            listState.scrollToItem(attackLogs.lastIndex)
        }
    }

    // When selectedNetwork change
    LaunchedEffect(selectedNetwork) {
        if (!hasInitialized) {
            hasInitialized = true
            lastNetwork = selectedNetwork
            return@LaunchedEffect
        }

        if(selectedNetwork != null && selectedNetwork != lastNetwork) {
            delay(500)
            bleViewModel.notifyEsp32ClearMacs()

            delay(500)
            bleViewModel.clearMacEvents()
            bleViewModel.clearDeauthLogs()
            lastNetwork = selectedNetwork
        }
    }

    // Content box
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFCDCDCD))
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
            // Return to login page
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp)
                    .background(Color(0xFF1E2624).copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                    .clickable {
                        navController.navigate("connected") {
                            popUpTo("deauth") { inclusive = true }
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
                    text = "Deauth Panel",
                    color = Color(0xFF363535),
                    fontFamily = autowide,
                    fontSize = 24.sp
                )
            }
        }

        // Main content
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

            // Display the targeted network
            DisplayTargetedNetwork(selectedNetwork)

            Spacer(Modifier.height(24.dp))

            // Main content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {

                // MAC Address Input
                Text(
                    text = "Target MAC Address",
                    color = Color(0xFF363535),
                    fontFamily = autowide,
                    fontSize = 12.sp
                )

                Spacer(Modifier.height(8.dp))

                BasicTextField(
                    value = targetMac,
                    onValueChange = { targetMac = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1A1A1A), RoundedCornerShape(6.dp))
                        .padding(12.dp),
                    textStyle = TextStyle(
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp
                    ),
                    decorationBox = { innerTextField ->
                        if (targetMac.isEmpty()) {
                            Text(
                                "FF:FF:FF:FF:FF:FF",
                                color = Color.Gray,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp
                            )
                        }
                        innerTextField()
                    }
                )

                Spacer(Modifier.height(16.dp))

                // ATTACK LOGS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Attack Logs",
                        color = Color(0xFF363535),
                        fontFamily = autowide,
                        fontSize = 12.sp
                    )

                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(Color(0xFF1E2624), RoundedCornerShape(4.dp))
                            .clickable {
                                // Cleaning action
                                bleViewModel.clearDeauthLogs()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("X", color = Color.White.copy(alpha = 0.4f), fontSize = 14.sp)
                    }
                }

                Spacer(Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(Color.Gray.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .border(1.dp, Color(0xFF363535), RoundedCornerShape(6.dp))
                        .padding(12.dp)
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(attackLogs) { log ->
                            Text(
                                text = "> $log",
                                color = Color.White.copy(alpha = 0.9f),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Bottom Controls: Launch Button & Safety Checkbox
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Launch/Stop Button
                Box(
                    modifier = Modifier
                        .background(
                            if (isAttackRunning) Color(0xFFCC0000) else Color(0xFF1E2624),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable(enabled = safetyCheckbox) {
                            isAttackRunning = !isAttackRunning
                            if (isAttackRunning) {
                                // START ATTACK
                                bleViewModel.logLocalDeauth("Attack started on ${selectedNetwork?.ssid}")
                                launchDeauthAttack(bleViewModel, selectedNetwork, targetMac)
                            } else {
                                // STOP ATTACK
                                bleViewModel.logLocalDeauth("Attack stopped")
                                stopDeauthAttack(bleViewModel)
                            }
                        }
                        .padding(horizontal = 32.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = if (isAttackRunning) "STOP ATTACK" else "LAUNCH ATTACK",
                        color = if (safetyCheckbox) Color.White.copy(alpha = 0.9f) else Color.Gray,
                        fontFamily = autowide,
                        fontSize = 16.sp
                    )
                }

                Spacer(Modifier.width(16.dp))

                // Safety Checkbox
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { safetyCheckbox = !safetyCheckbox }
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(
                                if (safetyCheckbox) Color(0xFF1A1A1A) else Color(0xFF1A1A1A),
                                RoundedCornerShape(4.dp)
                            )
                            .border(1.dp, Color(0xFF1E2624), RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (safetyCheckbox) {
                            Text("X", color = Color.White.copy(alpha = 0.9f), fontSize = 16.sp)
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    Text(
                        text = "Safety",
                        color = Color.White.copy(alpha = 0.9f),
                        fontFamily = autowide,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
fun launchDeauthAttack(bleViewModel: BleViewModel, selectedNetwork: WifiNetwork?, targetMac: String) {
    if(selectedNetwork == null) return

    bleViewModel.bleManager.sendCommand(
        Command.SendStartDeauth(
            targetMac = targetMac,
            apMac = selectedNetwork.bssid,
            channel = selectedNetwork.channel
        )
    )
}

@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
fun stopDeauthAttack(bleViewModel: BleViewModel) {
    bleViewModel.bleManager.sendCommand(
        Command.SendStopDeauth
    )
}