package com.example.espion32.ui.screens.wifi

import android.annotation.SuppressLint
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.espion32.autowide
import com.example.espion32.viewmodel.BleViewModel
import com.example.espion32.viewmodel.WifiViewModel

enum class WifiBand(val label: String) {
    BAND_24("2.4 GHz"),
    BAND_5("5 GHz")
}

fun channelsForBand(band: WifiBand): List<Int> =
    when(band) {
        WifiBand.BAND_24 -> (1..14).toList()
        WifiBand.BAND_5 -> listOf(
            36, 40, 44, 48, 52, 56, 60, 64,
            100, 104, 108, 112, 116, 120, 124, 128,
            132, 136, 140, 144, 149, 153, 157, 161, 165
        )
    }

@SuppressLint("MissingPermission")
@Composable
fun BFSScreen(navController: NavController, bleViewModel: BleViewModel, wifiViewModel: WifiViewModel) {

    // Page state
    var isAttackRunning by remember { mutableStateOf(false) }
    var safetyCheckbox by remember { mutableStateOf(false) }

    // Parameters
    var band by remember { mutableStateOf(WifiBand.BAND_24) }
    var channel by remember { mutableStateOf(1) }
    var ssidInput by remember { mutableStateOf("") }
    var ssids by remember { mutableStateOf(listOf<String>(
        "Free_Network",
        "Guest",
        "Free_Wifi2",
        "ACCORHOTELS-GUEST",
        "Freebox-D9BE8A",
        "Freebox-olivier",
        "Bbox-C9E2820",
        "babyblue",
        "DIRECT-11-HP-K382",
        "SFR-742F",
        "freebox_GRNV3",
        "Livebox-7C40",
        "SFR_FB4E",
        "GlobalWiFi_3ABNC8",
        "Cake is a lie",
        "Freebox-34NFA3",
        "SFR_3180_GUEST",
        "Livebox-4294",
        "Livebox-382E",
        "Samsung-AP34023"
    )) }
    var logs by remember { mutableStateOf(listOf("Ready")) }

    // When page is displayed
    LaunchedEffect(Unit) {
        isAttackRunning = false
        safetyCheckbox = false
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
                            popUpTo("bfs") { inclusive = true }
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
                    text = "BFS Panel",
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
            // Row Target Frequency
            // Row Target Channel
            // Row Target SSID
            WifiParameters(
                band = band,
                onBandChange = {
                    band = it
                    channel = channelsForBand(it).first()
                },
                channel = channel,
                onChannelChange = { channel = it },
                ssid = ssidInput,
                onSsidChange = { ssidInput = it },
                onAddSsid = {
                    if (ssidInput.isNotBlank()) {
                        ssids = ssids + ssidInput
                        logs = logs + "SSID added: $ssidInput"
                        ssidInput = ""
                    }
                }
            )

            Spacer(Modifier.height(16.dp))

            // List SSIDs
            SsidList(
                ssids = ssids,
                onDelete = {
                    logs = logs + "SSID removed: ${ssids[it]}"
                    ssids = ssids.toMutableList().also { list -> list.removeAt(it) }
                },
                onEdit = {
                    ssidInput = ssids[it]
                    ssids = ssids.toMutableList().also { list -> list.removeAt(it) }
                }
            )

            Spacer(Modifier.height(16.dp))

            // Attack Logs

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

                            } else {
                                // STOP ATTACK

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
fun <T> SimpleDropdown(
    label: String,
    value: T,
    values: List<T>,
    onValueChange: (T) -> Unit,
    valueLabel: (T) -> String
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text(label, fontSize = 14.sp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.DarkGray, RoundedCornerShape(6.dp))
                .clickable { expanded = true }
                .padding(12.dp)
        ) {
            Text(valueLabel(value))
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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

@Composable
fun WifiParameters(
    band: WifiBand,
    onBandChange: (WifiBand) -> Unit,
    channel: Int,
    onChannelChange: (Int) -> Unit,
    ssid: String,
    onSsidChange: (String) -> Unit,
    onAddSsid: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

        SimpleDropdown(
            label = "Frequency",
            value = band,
            values = WifiBand.values().toList(),
            onValueChange = onBandChange
        ) { it.label }

        SimpleDropdown(
            label = "Channel",
            value = channel,
            values = channelsForBand(band),
            onValueChange = onChannelChange
        ) { it.toString() }

        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.OutlinedTextField(
                value = ssid,
                onValueChange = onSsidChange,
                label = { Text("SSID") },
                modifier = Modifier.weight(1f)
            )

            Spacer(Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .background(Color(0xFF1E2624), RoundedCornerShape(6.dp))
                    .clickable { onAddSsid() }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text("ADD", color = Color.White)
            }
        }
    }
}

@Composable
fun SsidList(
    ssids: List<String>,
    onDelete: (Int) -> Unit,
    onEdit: (Int) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .background(Color(0xFF0F0F0F), RoundedCornerShape(6.dp))
            .border(
                width = 2.dp,
                color = Color(0xFF1E2624),
                shape = RoundedCornerShape(6.dp)
            )
            .padding(8.dp)
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {

            // HEADER
            item {
                Text("SSIDs", fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
            }

            // LIST SSIDs
            itemsIndexed(ssids) { index, ssid ->

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.LightGray, RoundedCornerShape(6.dp))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(ssid, modifier = Modifier.weight(1f))

                    Text(
                        "[+]️",
                        modifier = Modifier
                            .padding(4.dp)
                            .clickable { onEdit(index) }
                    )

                    Text(
                        "X️",
                        modifier = Modifier
                            .padding(4.dp)
                            .clickable { onDelete(index) }
                    )
                }
            }
        }
    }
}



@Preview(showBackground = true)
@Composable
fun BFSScreenPreview() {
    Column(modifier = Modifier.padding(16.dp)) {
        var band by remember { mutableStateOf(WifiBand.BAND_24) }
        var channel by remember { mutableStateOf(1) }
        var ssid by remember { mutableStateOf("") }
        var ssids by remember { mutableStateOf(listOf("TestNet", "ESP32_AP")) }

        WifiParameters(
            band = band,
            onBandChange = {
                band = it
                channel = channelsForBand(it).first()
            },
            channel = channel,
            onChannelChange = { channel = it },
            ssid = ssid,
            onSsidChange = { ssid = it },
            onAddSsid = {
                ssids = ssids + ssid
                ssid = ""
            }
        )

        Spacer(Modifier.height(16.dp))
        SsidList(ssids, {}, {})
        Spacer(Modifier.height(16.dp))
    }
}
