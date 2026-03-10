package com.cloud.autoclickertab

import android.content.Intent
import android.os.Parcelable
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloud.service.AutoClickAccessibilityService
import kotlinx.parcelize.Parcelize
import androidx.core.net.toUri
import com.cloud.service.AutoClickerService

@Parcelize
data class ClickPoint(
    val x: Int,
    val y: Int,
    val clickCount: Int,
    val delayMs: Long
) : Parcelable

@Composable
fun AutoClickerTabContent(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isServiceRunning by remember { mutableStateOf(false) }
    var clickPoints by remember { mutableStateOf(listOf<ClickPoint>()) }
    var showAddDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF2A2A2A))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Auto Clicker",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Status Anzeige
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF333333)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Status:", color = Color.White, fontSize = 16.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(
                                if (isServiceRunning) Color.Green else Color.Red,
                                CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isServiceRunning) "Aktiv" else "Inaktiv",
                        color = Color.White,
                        fontSize = 16.sp
                    )
                }
            }
        }

        // Overlay Permission Button
        Button(
            onClick = {
                if (!Settings.canDrawOverlays(context)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        "package:${context.packageName}".toUri()
                    )
                    context.startActivity(intent)
                } else if (!AutoClickAccessibilityService.isServiceEnabled()) {
                    // Öffne Accessibility Settings
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    context.startActivity(intent)
                } else {
                    // Starte Service mit Click Points
                    val serviceIntent = Intent(context, AutoClickerService::class.java).apply {
                        putParcelableArrayListExtra("clickPoints", ArrayList(clickPoints))
                    }
                    AutoClickerService.clickPoints.clear()
                    AutoClickerService.clickPoints.addAll(clickPoints)
                    context.startService(serviceIntent)
                    isServiceRunning = true
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4CAF50)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(
                when {
                    !Settings.canDrawOverlays(context) -> "Overlay-Berechtigung erteilen"
                    !AutoClickAccessibilityService.isServiceEnabled() -> "Accessibility aktivieren"
                    else -> "Overlay starten"
                },
                fontSize = 18.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Click Points Liste
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Klick-Punkte:",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = { showAddDialog = true }) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Punkt hinzufügen",
                    tint = Color(0xFF4CAF50)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            itemsIndexed(clickPoints) { index, point ->
                ClickPointItem(
                    point = point,
                    onDelete = {
                        clickPoints = clickPoints.filterIndexed { i, _ -> i != index }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    if (showAddDialog) {
        AddClickPointDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { point ->
                clickPoints = clickPoints + point
                showAddDialog = false
            }
        )
    }
}

@Composable
fun ClickPointItem(point: ClickPoint, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF333333)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Position: (${point.x}, ${point.y})",
                    color = Color.White,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Klicks: ${point.clickCount} | Verzögerung: ${point.delayMs}ms",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Löschen",
                    tint = Color.Red
                )
            }
        }
    }
}

@Composable
fun AddClickPointDialog(
    onDismiss: () -> Unit,
    onAdd: (ClickPoint) -> Unit
) {
    var x by remember { mutableStateOf("") }
    var y by remember { mutableStateOf("") }
    var clickCount by remember { mutableStateOf("1") }
    var delayMs by remember { mutableStateOf("1000") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Klick-Punkt hinzufügen") },
        text = {
            Column {
                OutlinedTextField(
                    value = x,
                    onValueChange = { x = it },
                    label = { Text("X-Koordinate") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = y,
                    onValueChange = { y = it },
                    label = { Text("Y-Koordinate") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = clickCount,
                    onValueChange = { clickCount = it },
                    label = { Text("Anzahl Klicks") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = delayMs,
                    onValueChange = { delayMs = it },
                    label = { Text("Verzögerung (ms)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val point = ClickPoint(
                        x = x.toIntOrNull() ?: 0,
                        y = y.toIntOrNull() ?: 0,
                        clickCount = clickCount.toIntOrNull() ?: 1,
                        delayMs = delayMs.toLongOrNull() ?: 1000
                    )
                    onAdd(point)
                }
            ) {
                Text("Hinzufügen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}