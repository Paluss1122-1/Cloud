package com.tabslify.tabs

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.tabslify.core.objects.Config.MAIL_NOTIFY_PORT
import com.tabslify.core.objects.prvt
import com.tabslify.quiethoursnotificationhelper.laptopIp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class EmailItem(
    val id: String,
    val subject: String,
    val from: String,
    val date: String,
    val timestamp: Long,
    val body: String,
    val summary: String?,
    val hasSummary: Boolean,
)

private fun loadServerIp(): String = laptopIp

private fun saveServerIp(ip: String) {
    laptopIp = ip
}

private fun loadLocalSummaryCache(context: android.content.Context): MutableMap<String, String> {
    val prefs =
        context.getSharedPreferences("email_summary_cache", android.content.Context.MODE_PRIVATE)
    return prefs.all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap().toMutableMap()
}

private fun saveLocalSummary(context: android.content.Context, id: String, summary: String) {
    context.getSharedPreferences("email_summary_cache", android.content.Context.MODE_PRIVATE)
        .edit { putString(id, summary) }
}

private suspend fun fetchEmailsStreaming(
    serverIp: String,
    onEmailReceived: suspend (EmailItem) -> Unit,
    onSummaryUpdate: suspend (id: String, summary: String) -> Unit = { _, _ -> }
): String? = withContext(Dispatchers.IO) {
    try {
        val conn = (URL("http://$serverIp:$MAIL_NOTIFY_PORT/emails/stream")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 0
        }
        if (conn.responseCode != 200) return@withContext "HTTP ${conn.responseCode}"

        conn.inputStream.bufferedReader().use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line?.takeIf { it.isNotBlank() } ?: continue
                try {
                    val obj = JSONObject(l)
                    if (obj.optString("type") == "summary_update") {
                        onSummaryUpdate(obj.getString("id"), obj.getString("summary"))
                    } else {
                        onEmailReceived(
                            EmailItem(
                                id = obj.optString("id"),
                                subject = obj.optString("subject", "(Kein Betreff)"),
                                from = obj.optString("from", "Unbekannt"),
                                date = obj.optString("date", ""),
                                timestamp = obj.optLong("timestamp", 0L),
                                body = obj.optString("body", ""),
                                summary = obj.optString("summary", "").takeIf { it.isNotBlank() },
                                hasSummary = obj.optBoolean("has_summary", false),
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.e("CLOUDSA", "Stream parse error: $e")
                }
            }
        }
        conn.disconnect()
        null
    } catch (e: Exception) {
        "Verbindungsfehler: ${e.message}"
    }
}

@Composable
fun GmailTabContent() {
    val context = androidx.compose.ui.platform.LocalContext.current
    if (!prvt()) {
        Toast.makeText(context, "Forbidden", Toast.LENGTH_SHORT).show()
        return
    }
    val scope = rememberCoroutineScope()

    val localSummaryCache = remember { loadLocalSummaryCache(context) }
    var serverIp by remember { mutableStateOf(loadServerIp()) }
    var emails by remember { mutableStateOf<List<EmailItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var selectedEmail by remember { mutableStateOf<EmailItem?>(null) }
    var showIpDialog by remember { mutableStateOf(serverIp.isBlank()) }

    fun loadEmails() {
        if (serverIp.isBlank()) {
            showIpDialog = true; return
        }
        scope.launch {
            isLoading = true
            errorMsg = null
            emails = emptyList()

            val error = fetchEmailsStreaming(
                serverIp,
                onEmailReceived = { emailItem ->
                    val enriched = when {
                        emailItem.hasSummary && emailItem.summary != null -> {
                            saveLocalSummary(context, emailItem.id, emailItem.summary)
                            localSummaryCache[emailItem.id] = emailItem.summary
                            emailItem
                        }

                        localSummaryCache.containsKey(emailItem.id) ->
                            emailItem.copy(
                                summary = localSummaryCache[emailItem.id],
                                hasSummary = true
                            )

                        else -> emailItem
                    }
                    withContext(Dispatchers.Main) { emails = emails + enriched }
                },
                onSummaryUpdate = { id, summary ->
                    saveLocalSummary(context, id, summary)
                    localSummaryCache[id] = summary
                    withContext(Dispatchers.Main) {
                        emails = emails.map {
                            if (it.id == id) it.copy(
                                summary = summary,
                                hasSummary = true
                            ) else it
                        }
                    }
                }
            )

            isLoading = false
            if (error != null && emails.isEmpty()) errorMsg = error
        }
    }

    LaunchedEffect(serverIp) {
        if (serverIp.isNotBlank()) loadEmails()
    }

    if (showIpDialog) {
        ServerIpDialog(
            current = serverIp,
            onConfirm = { ip ->
                serverIp = ip
                saveServerIp(ip)
                showIpDialog = false
                loadEmails()
            },
            onDismiss = { if (serverIp.isNotBlank()) showIpDialog = false }
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF111114))
    ) {
        when {
            selectedEmail != null -> EmailDetailView(
                email = selectedEmail!!,
                onBack = { selectedEmail = null })

            else -> Column(Modifier.fillMaxSize()) {
                EmailTopBar(
                    cacheInfo = if (isLoading) "Lädt... (${emails.size} bisher)" else "${emails.size} Emails",
                    isLoading = isLoading,
                    onReload = { loadEmails() },
                    onSettings = { showIpDialog = true }
                )
                when {
                    isLoading && emails.isEmpty() -> LoadingPlaceholder()
                    errorMsg != null && emails.isEmpty() -> ErrorPlaceholder(errorMsg!!) { loadEmails() }
                    emails.isEmpty() -> EmptyPlaceholder { loadEmails() }
                    else -> {
                        if (isLoading) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp),
                                color = Color(0xFF4285F4),
                                trackColor = Color.Transparent
                            )
                        }
                        EmailList(emails = emails, onClick = { selectedEmail = it })
                    }
                }
            }
        }
    }
}

// ─── Top-Bar ─────────────────────────────────────────────────────────────────

@Composable
fun EmailTopBar(
    cacheInfo: String?,
    isLoading: Boolean,
    onReload: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1A22))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GmailLogoText()
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                if (cacheInfo != null) {
                    Text(cacheInfo, color = Color(0xFF555568), fontSize = 11.sp)
                }
            }
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color(0xFF4285F4),
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(4.dp))
            }
            IconButton(onClick = onReload, enabled = !isLoading) {
                Icon(
                    Icons.Default.Refresh,
                    null,
                    tint = Color(0xFF8888AA),
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onSettings) {
                Icon(
                    Icons.Default.Settings,
                    null,
                    tint = Color(0xFF555568),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        HorizontalDivider(color = Color(0xFF222230))
    }
}

// ─── Email-Liste ─────────────────────────────────────────────────────────────

@Composable
fun EmailList(
    emails: List<EmailItem>,
    onClick: (EmailItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(emails, key = { it.id }) { email ->
            EmailRow(email = email, onClick = { onClick(email) })
        }
    }
}

@Composable
fun EmailRow(email: EmailItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(Color(0xFF161620))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Sender-Avatar
            val initial = email.from.firstOrNull { it.isLetter() }?.uppercase() ?: "?"
            val avatarColor = remember(email.from) {
                val colors = listOf(
                    0xFF4285F4L, 0xFFEA4335L, 0xFF34A853L, 0xFFFBBC05L,
                    0xFF9C27B0L, 0xFF00BCD4L, 0xFFFF5722L, 0xFF607D8BL
                )
                Color(colors[email.from.hashCode().and(0x7FFFFFFF) % colors.size])
            }
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(avatarColor),
                contentAlignment = Alignment.Center
            ) {
                Text(initial, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = extractDisplayName(email.from),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = email.date,
                        color = Color(0xFF555568),
                        fontSize = 11.sp,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = email.subject,
                    color = Color(0xFFCCCCDD),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // AI-Zusammenfassung direkt unter der Email-Zeile
        if (email.hasSummary && email.summary != null) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E2A3A))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text("🤖", fontSize = 13.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = email.summary,
                    color = Color(0xFF7EB8F7),
                    fontSize = 12.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(top = 10.dp),
            color = Color(0xFF1E1E2A)
        )
    }
}

// ─── Email-Detail ─────────────────────────────────────────────────────────────

@Composable
fun EmailDetailView(email: EmailItem, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF111114))
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1A22))
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
            }
            Text(
                text = "E-Mail",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
        }
        HorizontalDivider(color = Color(0xFF222230))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                // Subject
                Text(
                    text = email.subject,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 24.sp
                )
            }
            item {
                // Meta
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1A1A22))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    MetaRow("Von", email.from)
                    MetaRow("Datum", email.date)
                }
            }

            // AI-Zusammenfassung ganz oben im Detail
            if (email.hasSummary && email.summary != null) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF131E2E))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🤖", fontSize = 16.sp)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "KI-Zusammenfassung",
                                color = Color(0xFF4285F4),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = email.summary,
                            color = Color(0xFF9EC8F5),
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                    }
                }
            }

            item {
                // Body
                Text(
                    text = email.body,
                    color = Color(0xFFCCCCDD),
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
fun MetaRow(label: String, value: String) {
    Row {
        Text(
            text = "$label:  ",
            color = Color(0xFF555568),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            color = Color(0xFF9999AA),
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ─── Dialoge / Platzhalter ────────────────────────────────────────────────────

@Composable
fun ServerIpDialog(
    current: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var ip by remember { mutableStateOf(current) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E2A),
        title = {
            Text("Server-IP eingeben", color = Color.White, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "IP-Adresse des Laptops (ohne Port):",
                    color = Color(0xFF9999AA),
                    fontSize = 13.sp
                )
                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    placeholder = { Text("z.B. 192.168.1.100", color = Color(0xFF555568)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color(0xFFCCCCDD),
                        focusedBorderColor = Color(0xFF4285F4),
                        unfocusedBorderColor = Color(0xFF333345),
                        cursorColor = Color(0xFF4285F4)
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (ip.isNotBlank()) onConfirm(ip.trim()) }) {
                Text("Speichern", color = Color(0xFF4285F4), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen", color = Color(0xFF555568))
            }
        }
    )
}

@Composable
fun LoadingPlaceholder() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(color = Color(0xFF4285F4))
            Text("Emails werden geladen...", color = Color(0xFF555568), fontSize = 14.sp)
        }
    }
}

@Composable
fun ErrorPlaceholder(error: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text("⚠️", fontSize = 40.sp)
            Text(
                "Fehler beim Laden",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(error, color = Color(0xFF555568), fontSize = 13.sp, textAlign = TextAlign.Center)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF4285F4))
                    .clickable { onRetry() }
                    .padding(horizontal = 24.dp, vertical = 10.dp)
            ) {
                Text(
                    "Erneut versuchen",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun EmptyPlaceholder(onRefresh: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("📭", fontSize = 48.sp)
            Text(
                "Keine Emails",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF4285F4))
                    .clickable { onRefresh() }
                    .padding(horizontal = 24.dp, vertical = 10.dp)
            ) {
                Text(
                    "Laden",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun GmailLogoText() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        listOf(
            'G' to Color(0xFF4285F4),
            'm' to Color(0xFFEA4335),
            'a' to Color(0xFFFBBC05),
            'i' to Color(0xFF4285F4),
            'l' to Color(0xFF34A853)
        ).forEach { (char, clr) ->
            Text(char.toString(), color = clr, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

private fun extractDisplayName(from: String): String {
    val match = Regex("""^"?([^"<]+)"?\s*<""").find(from.trim())
    return match?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
        ?: from.substringBefore("<").trim().takeIf { it.isNotBlank() }
        ?: from
}