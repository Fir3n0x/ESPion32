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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.espion32.autowide
import com.example.espion32.model.Command
import com.example.espion32.viewmodel.BleViewModel
import com.example.espion32.viewmodel.WifiViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class HarvestMethod(val label: String) {
    LOGIN_KEY("WPA2/WPA3-Enterprise(802-1X/EAP)"),
    CAPTIVE_PORTAL("Captive Portal")
}

@SuppressLint("MissingPermission")
@Composable
fun EvilTwinScreen(navController: NavController, bleViewModel: BleViewModel, wifiViewModel: WifiViewModel) {

    // Page state
    var isAttackRunning by remember { mutableStateOf(false) }
    var safetyCheckbox by remember { mutableStateOf(false) }
    val attackLogs by bleViewModel.attackLogsEvilTwin.collectAsState()

    // Parameters
    var nameHarvestMethod by remember { mutableStateOf(HarvestMethod.LOGIN_KEY) }
    var nameEvilTwin by remember { mutableStateOf("") }

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
                    text = "Evil Twin Panel",
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
            // Fonctionnalité non implémentée côté firmware (stub) : on prévient
            // clairement l'opérateur et on désactive le lancement.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF5A1E1E), RoundedCornerShape(6.dp))
                    .padding(10.dp)
            ) {
                Text(
                    text = "⚠ Evil Twin non implémenté (firmware stub) — panneau désactivé",
                    color = Color(0xFFFFC9C9),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }

            Spacer(Modifier.height(12.dp))

            // Column / Box Set Harvest Method
            EvilTwinDropdown(
                label = "Harvest Method",
                value = nameHarvestMethod,
                values = HarvestMethod.entries.toList(),
                onValueChange = { nameHarvestMethod = it }
            ) { it.label }

            Spacer(Modifier.height(16.dp))

            // Column / Box Set Evil Twin name
            Text(
                text = "Name Evil Twin",
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF363535),
                fontFamily = autowide,
                fontSize = 16.sp
            )

            Spacer(Modifier.height(8.dp))

            BasicTextField(
                value = nameEvilTwin,
                onValueChange = { nameEvilTwin = it },
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
                    if (nameEvilTwin.isEmpty()) {
                        Text(
                            "INSERT A NAME",
                            color = Color.Gray,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp
                        )
                    }
                    innerTextField()
                }
            )

            Spacer(Modifier.height(16.dp))

            // Attack Logs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Attack Logs",
                    color = Color(0xFF363535),
                    fontFamily = autowide,
                    fontSize = 16.sp
                )

                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(Color(0xFF1E2624), RoundedCornerShape(4.dp))
                        .clickable {
                            // Cleaning action
                            bleViewModel.clearEvilTwinLogs()
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

            Spacer(Modifier.height(16.dp))

            Spacer(modifier = Modifier.weight(1f))

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
                        .clickable(enabled = false) { // désactivé : firmware non implémenté
                            isAttackRunning = !isAttackRunning
                            if (isAttackRunning) {
                                // START ATTACK
                                bleViewModel.logLocalEvilTwin("Evil Twin started as $nameEvilTwin with ${nameHarvestMethod.label}")
                                launchEvilTwinAttack(bleViewModel, nameHarvestMethod, nameEvilTwin)
                            } else {
                                // STOP ATTACK
                                bleViewModel.logLocalEvilTwin("Evil Twin stopped")
                                stopEvilTwinAttack(bleViewModel)
                                safetyCheckbox = false
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

@Composable
fun <T> EvilTwinDropdown(
    label: String,
    value: T,
    values: List<T>,
    onValueChange: (T) -> Unit,
    valueLabel: (T) -> String,
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text(label,
            color = Color(0xFF363535),
            fontFamily = autowide,
            fontSize = 16.sp
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.DarkGray, RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                .clickable { expanded = true }
                .padding(12.dp)
        ) {
            Text(
                text = valueLabel(value),
                color = Color(0xFF363535),
                fontFamily = autowide,
                fontSize = 12.sp
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.heightIn(max = 250.dp)
            ) {
                values.forEach {
                    DropdownMenuItem(
                        text = { Text(valueLabel(it)) },
                        onClick = {
                            onValueChange(it)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
fun launchEvilTwinAttack(bleViewModel: BleViewModel, nameHarvestMethod: HarvestMethod, nameEvilTwin: String) {
    bleViewModel.bleManager.sendCommand(
        Command.SendStartEvilTwin(
            harvestMethod = nameHarvestMethod,
            nameEvilTwin = nameEvilTwin
        )
    )
}

@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
fun stopEvilTwinAttack(bleViewModel: BleViewModel) {
    bleViewModel.bleManager.sendCommand(
        Command.SendStopEvilTwin
    )
}

@Preview
@Composable
fun EvilTwinScreenPreview() {

}