package com.example.espion32.ui.screens

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ModifierLocalBeyondBoundsLayout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.espion32.autowide
import com.example.espion32.viewmodel.BleViewModel
import kotlinx.coroutines.delay

object AppState {
    var isFirstLaunch by mutableStateOf(true)
}

@SuppressLint("MissingPermission")
@Composable
fun LoginScreen(navController: NavController, viewModel: BleViewModel) {
    val selectedDevices by viewModel.selectedDevice.collectAsState()

    val deviceName = when {
        selectedDevices == null -> "Select Device"
        selectedDevices!!.name.isNullOrBlank() -> "Unknown device"
        else -> selectedDevices!!.name
    }

    // Animation variable
    var showPcapLibrary by remember { mutableStateOf(false) }
    var showTitle by remember { mutableStateOf(false) }
    var showSubTitle by remember { mutableStateOf(false) }
    var showSelectDeviceButton by remember { mutableStateOf(false) }
    var showConnectDeviceButton by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (AppState.isFirstLaunch) {
            // First launch sequential animation
            showTitle = true
            delay(900)
            showSubTitle = true
            delay(400)
            showSelectDeviceButton = true
            delay(400)
            showPcapLibrary = true
            delay(400)
            showConnectDeviceButton = true
            delay(700)
            AppState.isFirstLaunch = false
        } else {
            // Next launch, direct display without animation
            showTitle = true
            showPcapLibrary = true
            showSubTitle = true
            showSelectDeviceButton = true
            showConnectDeviceButton = true
        }
    }


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFCDCDCD))
    ) {
        // Main Column
        Column(
            modifier = Modifier
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                // PCAP Library button
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


            Spacer(Modifier.height(150.dp))

            // Animation
            // Title at the top
            AnimatedVisibility(
                visible = showTitle,
                enter = if (AppState.isFirstLaunch) fadeIn() + expandVertically() else fadeIn(animationSpec = tween(0)),
                exit = fadeOut(),
                modifier = Modifier
            ) {
                TerminalTitle(
                    animate = AppState.isFirstLaunch
                )
            }

            // Subtitle
            AnimatedVisibility(
                visible = showSubTitle,
                enter = if (AppState.isFirstLaunch) fadeIn() + expandVertically() else fadeIn(animationSpec = tween(0)),
                exit = fadeOut(),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                DisplaySubtitle()

                Spacer(modifier = Modifier.height(12.dp))

                ScanLine()
            }

            Spacer(Modifier.height(80.dp))

            // Connect device button
            AnimatedVisibility(
                visible = showConnectDeviceButton,
                enter = if (AppState.isFirstLaunch) fadeIn() + expandVertically() else fadeIn(animationSpec = tween(0)),
                modifier = Modifier,
                exit = fadeOut()
            ) {
                Box(contentAlignment = Alignment.Center) {
                    ConnectButton(
                        navController = navController,
                        enabled = selectedDevices != null
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Select Device button
            AnimatedVisibility(
                visible = showSelectDeviceButton,
                enter = if (AppState.isFirstLaunch) fadeIn() + expandVertically(expandFrom = Alignment.Bottom) else fadeIn(animationSpec = tween(0)),
                exit = fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp, start = 32.dp, end = 32.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    SelectButton(
                        deviceName = deviceName,
                        onClick = { navController.navigate("devices") }
                    )
                }
            }
        }
    }
}

@Composable
private fun TerminalTitle(animate: Boolean = true) {
    var showCursor by remember { mutableStateOf(false) }
    var textToDisplay by remember { mutableStateOf(if (animate) "" else "ESPion32") }
    var showPrefix by remember { mutableStateOf(animate) }
    val fullText = "ESPion32"
    val prefix = "$> "
    var animationComplete by remember { mutableStateOf(!animate) }


    LaunchedEffect(animate) {
        if (animate) {
            showCursor = true
            fullText.forEachIndexed { index, _ ->
                textToDisplay = fullText.take(index + 1)
                delay(150)
            }
            animationComplete = true
            delay(500)
            showCursor = false
            showPrefix = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 0.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (showPrefix) {
                Text(
                    text = prefix,
                    color = Color(0xFF363535),
                    fontFamily = autowide,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.shadow(4.dp)
                )
            }

            Text(
                text = textToDisplay,
                color = Color(0xFF363535),
                fontFamily = autowide,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.shadow(4.dp),
                textAlign = TextAlign.Center
            )

            if (showCursor && !animationComplete) {
                AnimatedVisibility(
                    visible = showCursor,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .size(4.dp, 48.dp)
                            .background(Color(0xFF363535))
                    )
                }
            }

        }
    }
}

@Composable
fun ScanLine() {
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        modifier = Modifier
            .padding(top = 35.dp)
            .width(120.dp)
            .height(2.dp)
            .background(Color(0xFF616060).copy(alpha = alpha))
    )
}


@Composable
fun DisplaySubtitle() {
    Text(
        text = "offsec tool",
        color = Color.White.copy(alpha = 0.7f),
        fontFamily = autowide,
        fontSize = 16.sp,
        letterSpacing = 2.sp
    )
}


@Composable
private fun ConnectButton(
    navController: NavController,
    enabled: Boolean
) {

    val infiniteTransition = rememberInfiniteTransition()
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (enabled) 1.05f else 1f,   // No pulse if disabled
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    val borderPulse by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(260.dp)
            .height(120.dp)
            .graphicsLayer {
                scaleX = if (enabled) pulse else 1f
                scaleY = if (enabled) pulse else 1f
                alpha = if (enabled) 1f else 0.4f   // if disabled
            }
            .background(
                color = if (enabled) Color(0xFF1E2624).copy(alpha = 0.8f) else Color.Gray.copy(alpha = 0.4f),
                shape = RoundedCornerShape(22.dp)
            )
            .border(
                width = 2.dp,
//                color = if(enabled) Color(0xFFFF2A2A).copy(alpha = redPulse) else Color.Gray.copy(alpha = 0.4f),
                color = if(enabled) Color.White.copy(alpha = 0.9f) else Color(0xFF363535).copy(alpha = borderPulse),
                shape = RoundedCornerShape(22.dp)
            )
            .then(if (enabled) Modifier.clickable { navController.navigate("connecting") } else Modifier)
            .padding(16.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (enabled) "CONNECT" else "SELECT DEVICE",
                color = if (enabled) Color.White.copy(alpha = 0.9f) else Color.Gray,
                fontFamily = autowide,
                fontSize = if(enabled) 28.sp else 14.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (enabled) "Ready" else "No device selected",
                color = Color.White.copy(alpha = 0.6f),
                fontFamily = autowide,
                fontSize = 14.sp
            )
        }
    }
}


@Composable
private fun SelectButton(deviceName: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(
                color = Color(0xFFA8A8A8),
                shape = CircleShape
            )
            .border(
                width = 1.dp,
                color = Color(0xFF363535).copy(alpha = 0.4f),
                shape = CircleShape
            )
            .clickable { onClick() }
            .padding(horizontal = 24.dp)
    ) {
        Text(
            text = deviceName,
            color = Color.White.copy(alpha = 0.9f),
            fontFamily = autowide,
            fontSize = 16.sp,
            maxLines = 1
        )
    }
}


//@Preview(showBackground = true)
//@Composable
//fun LoginScreenPreview(){
//    val navController : NavController = rememberNavController()
//    LoginScreen(navController, viewModel = null)
//}