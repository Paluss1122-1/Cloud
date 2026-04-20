@file:Suppress("AssignedValueIsNeverRead")

package com.cloud.tabs


import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.Transparent
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.core.graphics.scale
import com.cloud.core.objects.Config
import com.cloud.quiethoursnotificationhelper.flashcardVokabelnFlow
import com.cloud.quiethoursnotificationhelper.trySendImageToLaptop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


data class Vokabel(val latein: String, val deutsch: String, val id: Int)
data class VokabelSet(
    val name: String,
    val vokabeln: List<Vokabel>,
    val createdAt: Long = System.currentTimeMillis()
)

enum class VokabelTabScreen { HOME, UPLOAD, REVIEW, LEARN }

private val BgSurface = Color(0xFF1E1E1E)
private val BgCard = Color(0xFF2A2A2A)
private val AccentViolet = Color(0xFF7C4DFF)
private val AccentVioletDim = Color(0xFF4A148C)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFFB0B0B0)
private val TextTertiary = Color(0xFF757575)

@Composable
fun VocabTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("vocab_sets", Context.MODE_PRIVATE) }

    var screen by remember { mutableStateOf(VokabelTabScreen.HOME) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var vokabeln by remember { mutableStateOf<List<Vokabel>>(emptyList()) }
    var isExtracting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var savedSets by remember { mutableStateOf(loadVokabelSets(prefs)) }
    var activeSet by remember { mutableStateOf<VokabelSet?>(null) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var saveNameInput by remember { mutableStateOf("") }

    val imagePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                bitmap = uriToBitmap(context, it)
                vokabeln = emptyList()
                screen = VokabelTabScreen.UPLOAD
            }
        }

    if (showSaveDialog) {
        SaveSetDialog(
            initial = saveNameInput,
            onConfirm = { name ->
                val set = VokabelSet(name.trim(), vokabeln)
                savedSets = saveVokabelSet(prefs, set)
                showSaveDialog = false
                saveNameInput = ""
                screen = VokabelTabScreen.HOME
            },
            onDismiss = { showSaveDialog = false }
        )
    }

    Crossfade(
        targetState = screen,
        label = "tab_transition"
    ) { current ->
        when (current) {
            VokabelTabScreen.HOME -> HomeScreen(
                savedSets = savedSets,
                prefs = prefs,
                onNewSet = { screen = VokabelTabScreen.UPLOAD },
                onOpenSet = { set ->
                    activeSet = set; vokabeln = set.vokabeln; screen = VokabelTabScreen.LEARN
                },
                onLearnWeak = { set ->
                    activeSet = set
                    vokabeln = loadWeakVokabeln(prefs, set.createdAt)
                    screen = VokabelTabScreen.LEARN
                },
                onDeleteSet = { set ->
                    saveWeakVokabeln(prefs, set.createdAt, emptyList())
                    savedSets = deleteVokabelSet(prefs, set)
                }
            )

            VokabelTabScreen.UPLOAD -> UploadScreen(
                bitmap = bitmap,
                isExtracting = isExtracting,
                errorMessage = errorMessage,
                onPickImage = { imagePicker.launch("image/*") },
                onBack = { screen = VokabelTabScreen.HOME },
                onExtract = {
                    bitmap?.let { bmp ->
                        isExtracting = true
                        errorMessage = null
                        scope.launch {
                            try {
                                val bytes = ByteArrayOutputStream().also {
                                    bmp.compress(Bitmap.CompressFormat.JPEG, 90, it)
                                }.toByteArray()
                                val sent = trySendImageToLaptop(bytes)
                                if (sent) {
                                    val result = flashcardVokabelnFlow.first { it != null }
                                    vokabeln = result ?: emptyList()
                                    if (vokabeln.isNotEmpty()) screen = VokabelTabScreen.REVIEW
                                    else {
                                        errorMessage = "Lokale Extraktion leer..."
                                    }
                                } else {
                                    fun Bitmap.scaleForApi(maxPx: Int = 1280): Bitmap {
                                        val scale = maxPx.toFloat() / maxOf(width, height)
                                        if (scale >= 1f) return this
                                        return this.scale(
                                            (width * scale).toInt(),
                                            (height * scale).toInt()
                                        )
                                    }

                                    val scaledBmp = bmp.scaleForApi(1280)
                                    val bytes2 = ByteArrayOutputStream().also {
                                        scaledBmp.compress(Bitmap.CompressFormat.JPEG, 92, it)
                                    }.toByteArray()
                                    val base64 = Base64.encodeToString(
                                        bytes2,
                                        Base64.NO_WRAP
                                    )
                                    Log.d("TOTOTO", "$base64")
                                    val nvidiaResult =
                                        withContext(Dispatchers.IO) {
                                            try {
                                                val messagesJson = JSONArray().apply {
                                                    put(JSONObject().apply {
                                                        put("role", "user")
                                                        put("content", JSONArray().apply {
                                                            put(JSONObject().apply {
                                                                put("type", "image_url")
                                                                put(
                                                                    "image_url",
                                                                    JSONObject().apply {
                                                                        put(
                                                                            "url",
                                                                            "data:image/jpeg;base64,$base64"
                                                                        )
                                                                    })
                                                            })
                                                            put(JSONObject().apply {
                                                                put("type", "text")
                                                                put(
                                                                    "text", """
                                                                        Look at this vocabulary list image carefully.
                                                                        There are TWO columns: Latin on the LEFT, German on the RIGHT.
                                                                        Each row is one vocabulary entry.
                                                                        
                                                                        Rules:
                                                                        - Match each Latin entry with the German entry on the SAME vertical position
                                                                        - Latin entries are on the left half of the image
                                                                        - German entries are on the right half
                                                                        - Ignore page numbers (like "116")
                                                                        - Ignore any repeated/duplicate blocks at bottom
                                                                        
                                                                        Return ONLY a JSON array like this (one object per row):
                                                                        [{"latein":"salūtem dīcere (m. Dat.)","deutsch":"(jdn.) grüßen, begrüßen"},{"latein":"gaudium","deutsch":"die Freude"},...]
                                                                        
                                                                        No markdown, no explanation, ONLY the JSON array.
                                                                        """.trimIndent()
                                                                )
                                                            })
                                                        })
                                                    })
                                                }
                                                val body = JSONObject().apply {
                                                    put(
                                                        "model",
                                                        "meta/llama-3.2-90b-vision-instruct"
                                                    )
                                                    put("messages", messagesJson)
                                                    put("stream", false)
                                                }
                                                val conn =
                                                    URL("https://integrate.api.nvidia.com/v1/chat/completions")
                                                        .openConnection() as HttpURLConnection
                                                conn.requestMethod = "POST"
                                                conn.setRequestProperty(
                                                    "Authorization",
                                                    "Bearer ${Config.NVIDIA}"
                                                )
                                                conn.setRequestProperty(
                                                    "Content-Type",
                                                    "application/json"
                                                )
                                                conn.doOutput = true
                                                conn.outputStream.use {
                                                    it.write(
                                                        body.toString().toByteArray()
                                                    )
                                                }
                                                val code = conn.responseCode
                                                if (code != 200) {
                                                    null
                                                } else {
                                                    val raw =
                                                        conn.inputStream.bufferedReader().readText()
                                                    JSONObject(raw).getJSONArray("choices")
                                                        .getJSONObject(0).getJSONObject("message")
                                                        .getString("content").trim()
                                                }
                                            } catch (_: Exception) {
                                                null
                                            }
                                        }
                                    if (nvidiaResult != null) {
                                        try {
                                            val startIdx = nvidiaResult.indexOf('[')
                                            val endIdx = nvidiaResult.lastIndexOf(']')
                                            if (startIdx != -1 && endIdx > startIdx) {
                                                val arr = JSONArray(
                                                    nvidiaResult.substring(
                                                        startIdx,
                                                        endIdx + 1
                                                    )
                                                )
                                                vokabeln = (0 until arr.length()).map {
                                                    val o = arr.getJSONObject(it)
                                                    Vokabel(
                                                        o.getString("latein"),
                                                        o.getString("deutsch"),
                                                        it
                                                    )
                                                }
                                            }
                                        } catch (e: Exception) {
                                            errorMessage = "Parse-Fehler: ${e.message}\nRaw: ${
                                                nvidiaResult.take(300)
                                            }"
                                        }
                                    } else {
                                        errorMessage = "API keine Antwort – prüfe Key & Netzwerk"
                                    }
                                    if (vokabeln.isNotEmpty()) screen = VokabelTabScreen.REVIEW
                                    else errorMessage = "Keine Vokabeln erkannt."
                                }
                            } catch (e: Exception) {
                                errorMessage = "Fehler: ${e.localizedMessage}"
                            } finally {
                                isExtracting = false
                            }
                        }
                    }
                }
            )

            VokabelTabScreen.REVIEW -> ReviewScreen(
                vokabeln = vokabeln,
                setName = activeSet?.name,
                onVokabelnChanged = { vokabeln = it },
                onStartLearning = { screen = VokabelTabScreen.LEARN },
                onSave = { saveNameInput = activeSet?.name ?: ""; showSaveDialog = true },
                onBack = {
                    screen =
                        if (activeSet != null) VokabelTabScreen.LEARN else VokabelTabScreen.UPLOAD
                },
                checkExist = activeSet != null
            )

            VokabelTabScreen.LEARN -> LearnScreen(
                vokabeln = vokabeln,
                prefs = prefs,
                setCreatedAt = activeSet?.createdAt ?: 0L,
                onBack = {
                    screen =
                        if (activeSet != null) VokabelTabScreen.HOME else VokabelTabScreen.REVIEW
                },
                setName = activeSet?.name
            )
        }
    }
}

@Composable
fun HomeScreen(
    savedSets: List<VokabelSet>,
    prefs: SharedPreferences,
    onNewSet: () -> Unit,
    onOpenSet: (VokabelSet) -> Unit,
    onLearnWeak: (VokabelSet) -> Unit,
    onDeleteSet: (VokabelSet) -> Unit
) {
    var setToDelete by remember { mutableStateOf<VokabelSet?>(null) }

    if (setToDelete != null) {
        AlertDialog(
            onDismissRequest = { setToDelete = null },
            containerColor = BgSurface,
            title = { Text("Set löschen?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("\"${setToDelete!!.name}\" wird gelöscht.", color = TextSecondary) },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFB71C1C))
                        .clickable { onDeleteSet(setToDelete!!); setToDelete = null }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) { Text("Löschen", color = TextPrimary, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(BgCard)
                        .clickable { setToDelete = null }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) { Text("Abbrechen", color = TextSecondary) }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primary)
                .clickable { onNewSet() }
                .padding(vertical = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("📷", fontSize = 22.sp)
                Text(
                    "Neues Set scannen",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        if (savedSets.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("📚", fontSize = 56.sp)
                    Text(
                        "Noch keine Sets",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Scan ein Vokabelbild zum Starten", color = TextTertiary, fontSize = 14.sp)
                }
            }
        } else {
            Text(
                "Gespeicherte Sets (${savedSets.size})",
                color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(savedSets, key = { it.createdAt }) { set ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable { onOpenSet(set) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            AccentViolet,
                                            AccentVioletDim
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("📖", fontSize = 22.sp)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                set.name,
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "${set.vokabeln.size} Vokabeln · ${formatSetDate(set.createdAt)}",
                                color = TextTertiary, fontSize = 12.sp
                            )
                        }
                        val weakCount = loadWeakVokabeln(prefs, set.createdAt).size
                        if (weakCount > 0) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFB71C1C).copy(alpha = 0.2f))
                                    .clickable { onLearnWeak(set) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    "✗ $weakCount",
                                    color = Color(0xFFEF5350),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                        }
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFB71C1C).copy(alpha = 0.15f))
                                .clickable { setToDelete = set },
                            contentAlignment = Alignment.Center
                        ) { Text("🗑", fontSize = 14.sp) }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
fun UploadScreen(
    bitmap: Bitmap?,
    isExtracting: Boolean,
    errorMessage: String?,
    onPickImage: () -> Unit,
    onBack: () -> Unit,
    onExtract: () -> Unit
) {
    BackHandler {
        onBack()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Zurück",
                    tint = TextPrimary
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(BgSurface)
                        .clickable { onPickImage() },
                    contentAlignment = Alignment.Center
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("📷", fontSize = 48.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("Bild auswählen", color = TextSecondary, fontSize = 15.sp)
                            Text("Format: LATEIN | DEUTSCH", color = TextTertiary, fontSize = 12.sp)
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(BgSurface)
                            .clickable { onPickImage() }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (bitmap != null) "Anderes Bild" else "Bild wählen",
                            color = TextSecondary, fontSize = 14.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (bitmap != null && !isExtracting) SolidColor(
                                    MaterialTheme.colorScheme.primary
                                ) else Brush.horizontalGradient(listOf(BgCard, BgCard))
                            )
                            .clickable(enabled = bitmap != null && !isExtracting) { onExtract() }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isExtracting) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = TextPrimary
                                )
                                Text("Erkenne...", color = TextPrimary, fontSize = 14.sp)
                            }
                        } else {
                            Text(
                                "Text erkennen",
                                color = if (bitmap != null) TextPrimary else TextTertiary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            errorMessage?.let {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFB71C1C).copy(alpha = 0.15f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⚠️", fontSize = 16.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(it, color = Color(0xFFEF9A9A), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ReviewScreen(
    vokabeln: List<Vokabel>,
    setName: String?,
    onVokabelnChanged: (List<Vokabel>) -> Unit,
    onStartLearning: (() -> Unit)? = null,
    onSave: () -> Unit,
    onBack: () -> Unit,
    checkExist: Boolean = true
) {
    var currentVokabeln by remember { mutableStateOf(vokabeln) }
    val initVocabs by remember { mutableStateOf(vokabeln) }  // Nutzt jetzt den Parameter direkt

    fun calculateChanges(original: List<Vokabel>, current: List<Vokabel>): Int {
        var changeCount = 0

        // Zähle gelöschte Vokabeln
        original.forEach { origVokabel ->
            if (current.none { it.id == origVokabel.id }) {
                changeCount++
            }
        }

        // Zähle geänderte Vokabeln
        current.forEach { currVokabel ->
            val origVokabel = original.firstOrNull { it.id == currVokabel.id }
            if (origVokabel != null) {
                if (origVokabel.latein != currVokabel.latein || origVokabel.deutsch != currVokabel.deutsch) {
                    changeCount++
                }
            } else {
                // Neu hinzugefügte Vokabel (falls das jemals passiert)
                changeCount++
            }
        }

        return changeCount
    }

    // Berechne changes direkt, statt zu akkumulieren
    val changes = remember(currentVokabeln, initVocabs) {
        calculateChanges(initVocabs, currentVokabeln)
    }

    BackHandler {
        onBack()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, end = 16.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = TextPrimary
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    setName ?: "Neue Vokabeln",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text("${currentVokabeln.size} Vokabeln", color = TextTertiary, fontSize = 12.sp)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(BgSurface)
                    .clickable {
                        if (changes > 0) {
                            onVokabelnChanged(currentVokabeln)
                        } else {
                            onSave()
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    if (changes > 0) "Bestätigen ($changes)" else if (checkExist) "Umbenennen" else "💾 Speichern",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (onStartLearning != null) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (currentVokabeln.isNotEmpty()) SolidColor(MaterialTheme.colorScheme.primary)
                            else Brush.horizontalGradient(listOf(BgCard, BgCard))
                        )
                        .clickable(enabled = currentVokabeln.isNotEmpty()) { onStartLearning() }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        "Lernen →",
                        color = if (currentVokabeln.isNotEmpty()) TextPrimary else TextTertiary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(currentVokabeln, key = { it.id }) { vokabel ->  // Nutze id als key
                var editMode by remember { mutableStateOf(false) }
                var editLatein by remember(vokabel.latein) { mutableStateOf(vokabel.latein) }
                var editDeutsch by remember(vokabel.deutsch) { mutableStateOf(vokabel.deutsch) }

                BackHandler(editMode) {
                    editMode = false
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(BgSurface)
                        .clickable { editMode = !editMode }
                ) {
                    if (editMode) {
                        Column(
                            Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                editLatein, { editLatein = it },
                                label = { Text("Latein") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                editDeutsch, { editDeutsch = it },
                                label = { Text("Deutsch") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFFB71C1C).copy(alpha = 0.15f))
                                        .clickable {
                                            currentVokabeln = currentVokabeln.filter { it.id != vokabel.id }
                                            editMode = false
                                        }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) { Text("Löschen", color = Color(0xFFEF9A9A), fontSize = 13.sp) }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.primary)
                                        .clickable {
                                            val idx = currentVokabeln.indexOfFirst { it.id == vokabel.id }
                                            if (idx >= 0) {
                                                currentVokabeln = currentVokabeln.toMutableList().also {
                                                    it[idx] = Vokabel(
                                                        editLatein.trim(),
                                                        editDeutsch.trim(),
                                                        vokabel.id
                                                    )
                                                }
                                            }
                                            editMode = false
                                        }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "Speichern",
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                vokabel.latein,
                                modifier = Modifier.weight(1f),
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(18.dp)
                                    .background(BgCard)
                            )
                            Text(
                                vokabel.deutsch,  // Entfernt: + vokabel.id
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.End,
                                color = TextSecondary,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@SuppressLint("UnusedContentLambdaTargetStateParameter")
@Composable
fun LearnScreen(
    vokabeln: List<Vokabel>,
    prefs: SharedPreferences,
    setCreatedAt: Long,
    onBack: () -> Unit,
    setName: String?
) {
    var setToReview by remember { mutableStateOf(false) }
    var vokabeln by remember { mutableStateOf(vokabeln) }
    var shuffled by remember { mutableStateOf(vokabeln) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var showDeutsch by remember { mutableStateOf(false) }
    var showAnswer by remember { mutableStateOf(false) }
    var correct by remember { mutableIntStateOf(0) }
    var wrong by remember { mutableIntStateOf(0) }
    val flipped by animateFloatAsState(targetValue = if (showAnswer) 180f else 0f, label = "flip")
    val done = currentIndex >= shuffled.size
    var correctVokabeln by remember { mutableStateOf<List<Vokabel>>(emptyList()) }
    var wrongVokabeln by remember { mutableStateOf<List<Vokabel>>(emptyList()) }
    LaunchedEffect(vokabeln) {
        shuffled = vokabeln.shuffled()
        showAnswer = showDeutsch
    }

    Column(modifier = Modifier.fillMaxSize().alpha(if (setToReview) 0f else 1f)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, end = 16.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary)
            }
            Text(
                "$setName",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(BgSurface)
                    .clickable { showDeutsch = !showDeutsch; showAnswer = false }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    if (showDeutsch) "DE → LA" else "LA → DE",
                    color = AccentViolet,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(BgSurface)
                    .clickable { setToReview = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    "Back to Review Screen",
                    tint = AccentViolet
                )
            }
        }

        if (!done) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(currentIndex.toFloat() / shuffled.size)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary)
                        .clip(RoundedCornerShape(2.dp))
                )
            }
            Text(
                "${currentIndex + 1}/${shuffled.size}  ✓ $correct  ✗ $wrong",
                color = TextTertiary, fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 4.dp)
            )

            Spacer(Modifier.height(24.dp))

            val vokabel = shuffled[currentIndex]
            val frontText = if (showDeutsch) vokabel.deutsch else vokabel.latein
            val backText = if (showDeutsch) vokabel.latein else vokabel.deutsch
            val frontLabel = if (showDeutsch) "DEUTSCH" else "LATEIN"
            val backLabel = if (showDeutsch) "LATEIN" else "DEUTSCH"

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 20.dp, vertical = 80.dp)
                    .graphicsLayer { rotationY = flipped; cameraDistance = 12f * density }
                    .clickable { showAnswer = !showAnswer },
                contentAlignment = Alignment.Center
            ) {
                if (flipped <= 90f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                frontLabel,
                                color = TextPrimary.copy(0.5f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                frontText,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                color = TextPrimary,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                            Spacer(Modifier.height(20.dp))
                            Text(
                                if (currentIndex >= 1) "" else "Tippe zum Aufdecken",
                                color = TextPrimary.copy(0.4f),
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .graphicsLayer { rotationY = 180f },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                backLabel,
                                color = AccentViolet.copy(0.7f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                backText,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                color = TextPrimary,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                            Spacer(Modifier.height(20.dp))
                            Text(
                                " ",
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            AnimatedContent(
                targetState = showAnswer,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "btns",
                modifier = Modifier.padding(bottom = 50.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFB71C1C).copy(alpha = 0.2f))
                            .clickable {
                                wrong++
                                wrongVokabeln = wrongVokabeln + shuffled[currentIndex]
                                showAnswer = false
                                currentIndex++
                            }
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "✗",
                                color = Color(0xFFEF5350),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Falsch",
                                color = Color(0xFFEF5350),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable {
                                correct++
                                correctVokabeln = correctVokabeln + shuffled[currentIndex]
                                showAnswer = false
                                currentIndex++
                            }
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "✓",
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Richtig",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LaunchedEffect(true) {
                    updateWeakVokabeln(prefs, setCreatedAt, correctVokabeln, wrongVokabeln)
                }
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(BgSurface)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("🎉", fontSize = 48.sp)
                    Text(
                        "Fertig!",
                        color = TextPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${shuffled.size} Vokabeln abgefragt",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "$correct",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentViolet
                            )
                            Text("Richtig", color = TextTertiary, fontSize = 12.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "$wrong",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEF5350)
                            )
                            Text("Falsch", color = TextTertiary, fontSize = 12.sp)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable {
                                shuffled = vokabeln.shuffled(); currentIndex = 0; correct =
                                0; wrong = 0; showAnswer = false
                            }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Nochmal",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (wrongVokabeln.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFFB71C1C).copy(alpha = 0.25f))
                                .clickable {
                                    shuffled = wrongVokabeln.shuffled()
                                    wrongVokabeln = emptyList()
                                    currentIndex = 0; correct = 0; wrong = 0; showAnswer = false
                                }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "✗ Falsche wiederholen (${wrongVokabeln.size})",
                                color = Color(0xFFEF5350),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(BgCard)
                            .clickable { onBack() }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("Zurück zur Übersicht", color = TextSecondary, fontSize = 14.sp) }
                }
            }
        }
    }

    if (setToReview) {
        Box(Modifier.fillMaxSize()) {
            ReviewScreen(
                vokabeln = vokabeln,
                onBack = { setToReview = false },
                onVokabelnChanged = { new ->
                    vokabeln = new
                    shuffled = new.shuffled()
                },
                onSave = {},
                setName = setName,
                checkExist = true
            )
        }
    }
}

@Composable
fun SaveSetDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgSurface,
        title = { Text("Set speichern", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name", color = TextTertiary) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (name.isNotBlank())
                            SolidColor(MaterialTheme.colorScheme.primary)
                        else
                            Brush.horizontalGradient(listOf(BgCard, BgCard))
                    )
                    .clickable(enabled = name.isNotBlank()) { onConfirm(name) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    "Speichern",
                    color = if (name.isNotBlank()) TextPrimary else TextTertiary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(BgCard)
                    .clickable { onDismiss() }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) { Text("Abbrechen", color = TextSecondary) }
        }
    )
}

private const val SETS_KEY = "vocab_sets"

fun saveVokabelSet(prefs: SharedPreferences, set: VokabelSet): List<VokabelSet> {
    val existing = loadVokabelSets(prefs).toMutableList()
    val idx = existing.indexOfFirst { it.name == set.name }
    if (idx >= 0) existing[idx] = set else existing.add(0, set)
    val json = JSONArray().also { arr ->
        existing.forEach { s ->
            arr.put(JSONObject().apply {
                put("name", s.name)
                put("createdAt", s.createdAt)
                put("vokabeln", JSONArray().also { va ->
                    s.vokabeln.forEach { v ->
                        va.put(
                            JSONObject()
                                .apply {
                                    put("latein", v.latein); put(
                                    "deutsch",
                                    v.deutsch
                                ); put("id", v.id)
                                })
                    }
                })
            })
        }
    }.toString()
    prefs.edit { putString(SETS_KEY, json) }
    return existing
}

fun loadVokabelSets(prefs: SharedPreferences): List<VokabelSet> {
    val raw = prefs.getString(SETS_KEY, null) ?: return emptyList()
    return try {
        val arr = JSONArray(raw)
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            val va = o.getJSONArray("vokabeln")
            VokabelSet(
                name = o.getString("name"),
                createdAt = o.getLong("createdAt"),
                vokabeln = (0 until va.length()).map { i ->
                    val v = va.getJSONObject(i)
                    Vokabel(v.getString("latein"), v.getString("deutsch"), v.getInt("id"))
                }
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}

fun deleteVokabelSet(prefs: SharedPreferences, set: VokabelSet): List<VokabelSet> {
    val updated = loadVokabelSets(prefs).filter { it.createdAt != set.createdAt }
    val json = JSONArray().also { arr ->
        updated.forEach { s ->
            arr.put(JSONObject().apply {
                put("name", s.name)
                put("createdAt", s.createdAt)
                put("vokabeln", JSONArray().also { va ->
                    s.vokabeln.forEach { v ->
                        va.put(
                            JSONObject()
                                .apply { put("latein", v.latein); put("deutsch", v.deutsch) })
                    }
                })
            })
        }
    }.toString()
    prefs.edit { putString(SETS_KEY, json) }
    return updated
}

private fun formatSetDate(ts: Long): String =
    SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(Date(ts))

fun uriToBitmap(context: Context, uri: Uri): Bitmap? {
    return try {
        ImageDecoder.decodeBitmap(
            ImageDecoder.createSource(
                context.contentResolver,
                uri
            )
        )
            .copy(Bitmap.Config.ARGB_8888, true)
    } catch (_: Exception) {
        null
    }
}

fun loadWeakVokabeln(prefs: SharedPreferences, setCreatedAt: Long): List<Vokabel> {
    val raw = prefs.getString("weak_$setCreatedAt", null) ?: return emptyList()
    return try {
        val arr = JSONArray(raw)
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Vokabel(o.getString("latein"), o.getString("deutsch"), o.getInt("id"))
        }
    } catch (_: Exception) {
        emptyList()
    }
}

fun saveWeakVokabeln(
    prefs: SharedPreferences,
    setCreatedAt: Long,
    list: List<Vokabel>
) {
    val json = JSONArray().also { arr ->
        list.forEach { v ->
            arr.put(
                JSONObject().apply { put("latein", v.latein); put("deutsch", v.deutsch) })
        }
    }.toString()
    prefs.edit { putString("weak_$setCreatedAt", json) }
}

fun updateWeakVokabeln(
    prefs: SharedPreferences,
    setCreatedAt: Long,
    correct: List<Vokabel>,
    wrong: List<Vokabel>
) {
    val current = loadWeakVokabeln(prefs, setCreatedAt).toMutableList()
    correct.forEach { v -> current.removeAll { it.latein == v.latein } }
    wrong.forEach { v -> if (current.none { it.latein == v.latein }) current.add(v) }
    saveWeakVokabeln(prefs, setCreatedAt, current)
}