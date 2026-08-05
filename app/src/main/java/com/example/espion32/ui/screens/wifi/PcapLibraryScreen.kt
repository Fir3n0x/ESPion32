package com.example.espion32.ui.screens.pcap

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.espion32.autowide
import com.example.espion32.viewmodel.BleViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun PcapLibraryScreen(navController: NavController, bleViewModel: BleViewModel) {
    val context = LocalContext.current
    // Utilise le PcapManager PARTAGÉ (via le ViewModel) : la librairie reflète
    // les captures reçues en direct, plus d'instance séparée désynchronisée.
    val captures by bleViewModel.savedCaptures.collectAsState()
    var selectedFile by remember { mutableStateOf<File?>(null) }

    // Rafraîchit à l'ouverture de l'écran
    LaunchedEffect(Unit) { bleViewModel.refreshCaptures() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFCDCDCD))
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 8.dp)
        ) {
            // Retour
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp)
                    .background(Color(0xFF1E2624).copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                    .clickable { navController.popBackStack() }
                    .padding(horizontal = 10.dp, vertical = 2.dp)
            ) {
                Text("<", color = Color.White, fontFamily = autowide, fontSize = 35.sp)
            }

            Text(
                text = "PCAP Library",
                color = Color(0xFF363535),
                fontFamily = autowide,
                fontSize = 24.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp)
            )
        }

        Spacer(Modifier.height(60.dp))

        if (captures.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No captures yet",
                    color = Color(0xFF363535).copy(alpha = 0.5f),
                    fontFamily = autowide,
                    fontSize = 16.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(captures) { file ->
                    PcapFileCard(
                        file = file,
                        isSelected = selectedFile == file,
                        onSelect = { selectedFile = if (selectedFile == file) null else file },
                        onDelete = {
                            file.delete()
                            bleViewModel.refreshCaptures()
                            if (selectedFile == file) selectedFile = null
                        },
                        onShare = { sharePcapFile(context, file) }
                    )
                }
            }
        }
    }
}

@Composable
fun PcapFileCard(
    file: File,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    // Parser le nom : SSID_YYYYMMDD_HHMMSS.pcap
    val nameParts = file.nameWithoutExtension.split("_")
    val ssid = nameParts.dropLast(2).joinToString("_").ifEmpty { "unknown" }
    val dateStr = run {
        try {
            val raw = "${nameParts[nameParts.size - 2]}_${nameParts.last()}"
            val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val date = sdf.parse(raw)
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(date!!)
        } catch (e: Exception) { "?" }
    }
    val sizeKb = "%.1f KB".format(file.length() / 1024f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A1A), RoundedCornerShape(10.dp))
            .clickable { onSelect() }
            .padding(12.dp)
    ) {
        // Ligne principale
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ssid,
                    color = Color.White,
                    fontFamily = autowide,
                    fontSize = 14.sp
                )
                Text(
                    text = "$dateStr · $sizeKb",
                    color = Color.White.copy(alpha = 0.5f),
                    fontFamily = autowide,
                    fontSize = 11.sp
                )
            }

            Text(
                text = if (isSelected) "⌄" else ">",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 18.sp
            )
        }

        // Actions dépliables
        if (isSelected) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Share
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFF1E2624), RoundedCornerShape(6.dp))
                        .clickable { onShare() }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Share", color = Color.White, fontFamily = autowide, fontSize = 12.sp)
                }

                // Delete
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFF3D1515), RoundedCornerShape(6.dp))
                        .clickable { onDelete() }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Delete", color = Color(0xFFFF6B6B), fontFamily = autowide, fontSize = 12.sp)
                }
            }
        }
    }
}

// Share helper
fun sharePcapFile(context: android.content.Context, file: File) {
    val uri = androidx.core.content.FileProvider.getUriForFile(
        context,
        "${context.packageName}.provider",
        file
    )
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "application/octet-stream"
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(android.content.Intent.createChooser(intent, "Share PCAP"))
}