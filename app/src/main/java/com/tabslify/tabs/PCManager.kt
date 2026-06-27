package com.tabslify.tabs

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tabslify.core.objects.prvt
import com.tabslify.quiethoursnotificationhelper.laptopIp
import com.tabslify.tabs.authenticator.TotpGenerator
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

data class PcDetails(
    val ip: String,
    val regInfo: String,
    val secret: String,
    val liveCode: String
)

@Composable
fun PCManagerTab() {
    val context = LocalContext.current
    if (!prvt()) {
        Toast.makeText(context, "Forbidden", Toast.LENGTH_SHORT).show()
        return
    }
    val prefs = remember { context.getSharedPreferences("registered_pcs", Context.MODE_PRIVATE) }
    val secretsPrefs = remember { context.getSharedPreferences("pc_secrets", Context.MODE_PRIVATE) }
    var pcDetailsList by remember { mutableStateOf(emptyList<PcDetails>()) }

    LaunchedEffect(Unit) {
        while (true) {
            val allPcs = prefs.all.map { (ip, regInfo) ->
                val secret = secretsPrefs.getString(ip, "COMMANDEXECUTORK") ?: "COMMANDEXECUTORK"
                val liveCode = TotpGenerator.generateTOTP(secret)
                PcDetails(
                    ip = ip,
                    regInfo = regInfo.toString(),
                    secret = secret,
                    liveCode = liveCode
                )
            }
            pcDetailsList = allPcs
            delay(1000.milliseconds)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "PC Manager",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(16.dp))
        val currentConnectedIp = laptopIp
        if (currentConnectedIp.isNotEmpty()) {
            val currentSecret =
                secretsPrefs.getString(currentConnectedIp, "COMMANDEXECUTORK") ?: "COMMANDEXECUTORK"
            val currentCode = TotpGenerator.generateTOTP(currentSecret)
            Text(
                text = "Verbunden mit: $currentConnectedIp",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.Green
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Aktueller Code:",
                fontSize = 14.sp,
                color = Color.Gray
            )
            Text(
                text = currentCode,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Green
            )
        } else {
            Text(
                text = "Kein PC aktiv verbunden",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.Red
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Registrierte PCs:",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (pcDetailsList.isEmpty()) {
            Text(
                text = "Keine PCs registriert",
                fontSize = 14.sp,
                color = Color.Gray,
                modifier = Modifier.align(Alignment.Start)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(pcDetailsList) { pc ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    ) {
                        Text(
                            text = pc.ip,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Cyan
                        )
                        Text(
                            text = pc.regInfo,
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                        Text(
                            text = "Secret: ${pc.secret}",
                            fontSize = 12.sp,
                            color = Color.LightGray
                        )
                        Text(
                            text = "Live-Code: ${pc.liveCode}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Green
                        )
                    }
                }
            }
        }
    }
}
