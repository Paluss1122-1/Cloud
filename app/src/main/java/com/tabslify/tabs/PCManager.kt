package com.tabslify.tabs

import android.annotation.SuppressLint
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.Transparent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.tabslify.core.objects.prvt
import com.tabslify.quiethoursnotificationhelper.laptopIp
import com.tabslify.quiethoursnotificationhelper.laptopName
import com.tabslify.quiethoursnotificationhelper.stopAllSyncServices
import com.tabslify.quiethoursnotificationhelper.syncTodosWithLaptop
import com.tabslify.quiethoursnotificationhelper.getTriggerListenerStatus
import com.tabslify.tabs.authenticator.TotpGenerator
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

data class PcDetails(
    val name: String,
    val ip: String,
    val uuid: String,
    val regInfo: String,
    val secret: String,
    val liveCode: String
)

data class PendingPc(
    val name: String,
    val ip: String,
    val uuid: String
)

@SuppressLint("UseKtx")
@Composable
fun PCManagerTab() {
    val context = LocalContext.current
    if (!prvt()) {
        Toast.makeText(context, "Forbidden", Toast.LENGTH_SHORT).show()
        return
    }

    val prefs = remember { context.getSharedPreferences("registered_pcs", Context.MODE_PRIVATE) }
    val secretsPrefs = remember { context.getSharedPreferences("pc_secrets", Context.MODE_PRIVATE) }
    val pendingPrefs = remember { context.getSharedPreferences("pending_pcs", Context.MODE_PRIVATE) }
    val uuidPrefs = remember { context.getSharedPreferences("pc_uuids", Context.MODE_PRIVATE) }

    var pcDetailsList by remember { mutableStateOf(emptyList<PcDetails>()) }
    var pendingList by remember { mutableStateOf(emptyList<PendingPc>()) }
    var triggerStatus by remember { mutableStateOf("") }

    // Poll to keep the UI in sync
    LaunchedEffect(Unit) {
        while (true) {
            val allPcs = prefs.all.map { (name, regInfo) ->
                // pc_secrets is keyed by hardware UUID, not name - UUID is the only
                // identifier that can't change out from under a registered PC.
                val uuid = uuidPrefs.getString(name, null) ?: "NO_UUID"
                val secret = secretsPrefs.getString(uuid, null) ?: ""
                val liveCode = if (secret.isNotEmpty()) TotpGenerator.generateTOTP(secret) else "?"
                PcDetails(
                    name = name,
                    ip = "",
                    uuid = uuid,
                    regInfo = regInfo.toString(),
                    secret = secret,
                    liveCode = liveCode
                )
            }
            pcDetailsList = allPcs

            val pending = pendingPrefs.all.map { (name, value) ->
                val data = value.toString().split("|")
                PendingPc(
                    name = name,
                    ip = data.getOrNull(0) ?: "Unknown",
                    uuid = data.getOrNull(1) ?: "NO_UUID"
                )
            }
            pendingList = pending
            
            triggerStatus = getTriggerListenerStatus()

            delay(1000L.milliseconds)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Transparent)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF28283E)),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF3F3F5F), RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val currentConnectedName = laptopName
                val currentConnectedIp = laptopIp
                if (currentConnectedName.isNotEmpty() || currentConnectedIp.isNotEmpty()) {
                    val displayName = currentConnectedName.ifEmpty { currentConnectedIp }

                    Text(
                        text = "Aktiv verbunden mit",
                        fontSize = 12.sp,
                        color = Color.LightGray
                    )
                    Text(
                        text = displayName,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50)
                    )
                    if (currentConnectedIp.isNotEmpty() && currentConnectedIp != displayName) {
                        Text(
                            text = "IP: $currentConnectedIp",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                } else {
                    Text(
                        text = "Kein PC aktiv verbunden",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFE57373)
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Trigger Listener: $triggerStatus",
                    fontSize = 11.sp,
                    color = Color.LightGray
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Pending Requests Section
            if (pendingList.isNotEmpty()) {
                item {
                    Text(
                        text = "Ausstehende Anfragen (${pendingList.size})",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFB74D),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                items(pendingList) { pc ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2C251C)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFFFB74D).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = "PC Name: ${pc.name}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "IP: ${pc.ip}",
                                fontSize = 13.sp,
                                color = Color.LightGray
                            )
                            Text(
                                text = "Hardware-ID: ${pc.uuid.take(16)}...",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Button(
                                    onClick = {
                                        pendingPrefs.edit { remove(pc.name) }
                                        Toast.makeText(context, "${pc.name} abgelehnt", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCF6679)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Text("Ablehnen", color = Color.White)
                                }

                                Button(
                                    onClick = {
                                        val allowedChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
                                        val newSecret = (1..16).map { allowedChars.random() }.joinToString("")
                                        
                                        val nowStr = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date())
                                        secretsPrefs.edit().putString(pc.uuid, newSecret).apply()
                                        prefs.edit().putString(pc.name, "Registriert am $nowStr").apply()
                                        uuidPrefs.edit().putString(pc.name, pc.uuid).apply()

                                        pendingPrefs.edit {remove(pc.name)}

                                        laptopIp = pc.ip
                                        laptopName = pc.name

                                        syncTodosWithLaptop(context, true)
                                        
                                        Toast.makeText(context, "${pc.name} erfolgreich freigegeben!", Toast.LENGTH_LONG).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Erlauben", color = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            // 2. Registered PCs Section
            item {
                Text(
                    text = "Registrierte Geräte",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            if (pcDetailsList.isEmpty()) {
                item {
                    Text(
                        text = "Keine PCs registriert",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                items(pcDetailsList) { pc ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF2C2C3E), RoundedCornerShape(12.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = pc.name,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF81C784)
                                )
                                Button(
                                    onClick = {
                                        prefs.edit().remove(pc.name).apply()
                                        secretsPrefs.edit().remove(pc.uuid).apply()
                                        uuidPrefs.edit().remove(pc.name).apply()
                                        if (pc.name == laptopName) {
                                            stopAllSyncServices(context)
                                        }
                                        Toast.makeText(context, "${pc.name} entfernt", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x33CF6679)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("Entfernen", color = Color(0xFFEF5350), fontSize = 11.sp)
                                }
                            }
                            Text(
                                text = pc.regInfo,
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "TOTP Key: ${pc.secret}",
                                fontSize = 12.sp,
                                color = Color(0xFFFFB74D),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Aktueller Code: ${pc.liveCode}",
                                fontSize = 14.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        prefs.edit().clear().apply()
                        secretsPrefs.edit().clear().apply()
                        pendingPrefs.edit().clear().apply()
                        uuidPrefs.edit().clear().apply()
                        stopAllSyncServices(context)
                        Toast.makeText(context, "Alle alten Verbindungen gelöscht!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x1ACF6679)),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Alte Verbindungen & Prefs löschen", color = Color(0xFFEF5350), fontSize = 13.sp)
                }
            }
        }
    }
}
