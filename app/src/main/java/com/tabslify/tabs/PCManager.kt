package com.tabslify.tabs

import android.annotation.SuppressLint
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.tabslify.R
import com.tabslify.core.objects.prvt
import com.tabslify.quiethoursnotificationhelper.ensureRandomSyncSecret
import com.tabslify.quiethoursnotificationhelper.laptopIp
import com.tabslify.quiethoursnotificationhelper.laptopName
import com.tabslify.quiethoursnotificationhelper.resolveDisplayName
import com.tabslify.quiethoursnotificationhelper.resolveSyncSecret
import com.tabslify.quiethoursnotificationhelper.setDisplayName
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
    val displayName: String,
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
    
    val forbiddenMsg = stringResource(R.string.forbidden)
    val unknownMsg = stringResource(R.string.unknown)
    val pcAbgelehntMsg = stringResource(R.string.pc_abgelehnt)
    val registriertAmMsg = stringResource(R.string.registriert_am)
    val erfolgreichFreigegebenMsg = stringResource(R.string.erfolgreich_freigegeben)
    val pcEntferntMsg = stringResource(R.string.pc_entfernt)
    val alleAltenVerbindungenGeloeschtMsg = stringResource(R.string.alle_alten_verbindungen_geloscht)
    
    if (!prvt()) {
        Toast.makeText(context, forbiddenMsg, Toast.LENGTH_SHORT).show()
        return
    }

    val prefs = remember { context.getSharedPreferences("registered_pcs", Context.MODE_PRIVATE) }
    val secretsPrefs = remember { context.getSharedPreferences("pc_secrets", Context.MODE_PRIVATE) }
    val pendingPrefs = remember { context.getSharedPreferences("pending_pcs", Context.MODE_PRIVATE) }
    val uuidPrefs = remember { context.getSharedPreferences("pc_uuids", Context.MODE_PRIVATE) }
    val displayNamesPrefs = remember { context.getSharedPreferences("pc_display_names", Context.MODE_PRIVATE) }

    var pcDetailsList by remember { mutableStateOf(emptyList<PcDetails>()) }
    var pendingList by remember { mutableStateOf(emptyList<PendingPc>()) }
    var triggerStatus by remember { mutableStateOf("") }
    var editingName by remember { mutableStateOf<String?>(null) }
    var editText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(editingName) {
        if (editingName != null) focusRequester.requestFocus()
    }

    val saveName: (String) -> Unit = { key ->
        setDisplayName(context, key, editText.trim())
        editingName = null
    }

    LaunchedEffect(Unit) {
        while (true) {
            val allPcs = prefs.all.map { (name, regInfo) ->
                val uuid = uuidPrefs.getString(name, null) ?: "NO_UUID"
                val secret = resolveSyncSecret(context, name).orEmpty()
                val liveCode = if (secret.isNotEmpty()) TotpGenerator.generateTOTP(secret) else "?"
                PcDetails(
                    name = name,
                    displayName = resolveDisplayName(context, name) ?: name,
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
                    ip = data.getOrNull(0) ?: unknownMsg,
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
                    val shownName = currentConnectedName.let { resolveDisplayName(context, it) ?: it }
                    val displayName = shownName.ifEmpty { currentConnectedIp }

                    Text(
                        text = stringResource(R.string.aktiv_verbunden_mit),
                        fontSize = 12.sp,
                        color = Color.LightGray
                    )
                    Text(
                        text = displayName,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50)
                    )
                    if (shownName.isNotEmpty() && shownName != currentConnectedName) {
                        Text(
                            text = stringResource(R.string.hostname_label, currentConnectedName),
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                    if (currentConnectedIp.isNotEmpty() && currentConnectedIp != displayName) {
                        Text(
                            text = "IP: $currentConnectedIp",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.kein_pc_aktiv_verbunden),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFE57373)
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = stringResource(R.string.trigger_listener_2, triggerStatus),
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
                        text = stringResource(R.string.ausstehende_anfragen, pendingList.size),
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
                                text = stringResource(R.string.pc_name, pc.name),
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
                                text = stringResource(R.string.hardware_id, pc.uuid.take(16)),
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
                                        Toast.makeText(context, pcAbgelehntMsg.format(pc.name), Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCF6679)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Text(stringResource(R.string.ablehnen), color = Color.White)
                                }

                                Button(
                                    onClick = {
                                        val nowStr = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date())
                                        prefs.edit().putString(pc.name, registriertAmMsg.format(nowStr)).apply()
                                        uuidPrefs.edit().putString(pc.name, pc.uuid).apply()
                                        ensureRandomSyncSecret(context, pc.name)

                                        pendingPrefs.edit {remove(pc.name)}

                                        laptopIp = pc.ip
                                        laptopName = pc.name

                                        syncTodosWithLaptop(context, true)
                                        
                                        Toast.makeText(context, erfolgreichFreigegebenMsg.format(pc.name), Toast.LENGTH_LONG).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(stringResource(R.string.erlauben), color = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            // 2. Registered PCs Section
            item {
                Text(
                    text = stringResource(R.string.registrierte_gerate),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            if (pcDetailsList.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.keine_pcs_registriert),
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
                                if (editingName == pc.name) {
                                    OutlinedTextField(
                                        value = editText,
                                        onValueChange = { editText = it },
                                        singleLine = true,
                                        label = { Text(stringResource(R.string.pc_name_bearbeiten)) },
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                        keyboardActions = KeyboardActions(onDone = { saveName(pc.name) }),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedBorderColor = Color(0xFF81C784),
                                            unfocusedBorderColor = Color(0xFF3F3F5F),
                                            focusedLabelColor = Color(0xFF81C784),
                                            unfocusedLabelColor = Color.Gray,
                                            cursorColor = Color(0xFF81C784)
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .focusRequester(focusRequester)
                                    )
                                    Button(
                                        onClick = { saveName(pc.name) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x3381C784)),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.padding(start = 8.dp)
                                    ) {
                                        Text(stringResource(R.string.speichern), color = Color(0xFF81C784), fontSize = 11.sp)
                                    }
                                    Button(
                                        onClick = { editingName = null },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FFFFFF)),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(stringResource(R.string.abbrechen), color = Color.Gray, fontSize = 11.sp)
                                    }
                                } else {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                editText = pc.displayName
                                                editingName = pc.name
                                            }
                                    ) {
                                        Text(
                                            text = pc.displayName,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF81C784)
                                        )
                                        if (pc.displayName != pc.name) {
                                            Text(
                                                text = stringResource(R.string.hostname_label, pc.name),
                                                fontSize = 11.sp,
                                                color = Color.Gray
                                            )
                                        }
                                    }
                                    Button(
                                        onClick = {
                                            prefs.edit().remove(pc.name).apply()
                                            uuidPrefs.edit().remove(pc.name).apply()
                                            secretsPrefs.edit().remove(pc.name).apply()
                                            displayNamesPrefs.edit().remove(pc.name).apply()
                                            if (pc.name == laptopName) {
                                                stopAllSyncServices(context)
                                            }
                                            Toast.makeText(context, pcEntferntMsg.format(pc.name), Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x33CF6679)),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(stringResource(R.string.entfernen), color = Color(0xFFEF5350), fontSize = 11.sp)
                                    }
                                }
                            }
                            Text(
                                text = pc.regInfo,
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.totp_key, pc.secret),
                                fontSize = 12.sp,
                                color = Color(0xFFFFB74D),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.aktueller_code, pc.liveCode),
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
                        pendingPrefs.edit().clear().apply()
                        uuidPrefs.edit().clear().apply()
                        secretsPrefs.edit().clear().apply()
                        displayNamesPrefs.edit().clear().apply()
                        stopAllSyncServices(context)
                        Toast.makeText(context, alleAltenVerbindungenGeloeschtMsg, Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x1ACF6679)),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.alte_verbindungen_prefs_loschen), color = Color(0xFFEF5350), fontSize = 13.sp)
                }
            }
        }
    }
}
