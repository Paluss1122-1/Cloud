package com.cloud.vocabtab

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloud.quiethoursnotificationhelper.flashcardVokabelnFlow
import com.cloud.quiethoursnotificationhelper.trySendOcrToLaptop
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.math.abs

// ──────────────────────────────────────────────
// Data
// ──────────────────────────────────────────────

data class Vokabel(
    val latein: String,
    val deutsch: String
)

enum class VokabelTabScreen { UPLOAD, REVIEW, LEARN }

// ──────────────────────────────────────────────
// Main Tab
// ──────────────────────────────────────────────

@Composable
fun VocabTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var screen by remember { mutableStateOf(VokabelTabScreen.UPLOAD) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var rawText by remember { mutableStateOf("") }
    var vokabeln by remember { mutableStateOf<List<Vokabel>>(emptyList()) }
    var isExtracting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Image picker
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedUri = it
            bitmap = uriToBitmap(context, it)
            rawText = ""
            vokabeln = emptyList()
            screen = VokabelTabScreen.UPLOAD
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top bar with step indicator
        StepIndicator(current = screen)

        Spacer(Modifier.height(16.dp))

        when (screen) {
            VokabelTabScreen.UPLOAD -> UploadScreen(
                bitmap = bitmap,
                rawText = rawText,
                isExtracting = isExtracting,
                errorMessage = errorMessage,
                onPickImage = { imagePicker.launch("image/*") },
                // Statt extractVokabelnFromBitmap(bmp) direkt:
                onExtract = {
                    bitmap?.let { bmp ->
                        isExtracting = true
                        errorMessage = null
                        scope.launch {
                            try {
                                rawText = extractTextFromBitmap(bmp)
                                if (rawText.isBlank()) {
                                    errorMessage = "Kein Text erkannt."
                                    return@launch
                                }
                                // Immer Laptop versuchen, kein isLaptopConnected-Gate
                                val sent = trySendOcrToLaptop(rawText)
                                if (sent) {
                                    val result = flashcardVokabelnFlow
                                        .first { it != null }  // wartet auf Antwort (Flow hat 30s Timeout)
                                    vokabeln = result ?: emptyList()
                                    if (vokabeln.isNotEmpty()) screen = VokabelTabScreen.REVIEW
                                    else {
                                        errorMessage = "LLM-Extraktion leer, versuche lokal..."
                                        vokabeln = extractVokabelnFromBitmap(bmp)
                                        if (vokabeln.isNotEmpty()) screen = VokabelTabScreen.REVIEW
                                        else errorMessage = "Keine Vokabeln erkannt."
                                    }
                                } else {
                                    // Laptop nicht erreichbar → direkt lokal
                                    vokabeln = extractVokabelnFromBitmap(bmp)
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
                onVokabelnChanged = { vokabeln = it },
                onStartLearning = { screen = VokabelTabScreen.LEARN },
                onBack = { screen = VokabelTabScreen.UPLOAD }
            )

            VokabelTabScreen.LEARN -> LearnScreen(
                vokabeln = vokabeln,
                onBack = { screen = VokabelTabScreen.REVIEW }
            )
        }
    }
}

// ──────────────────────────────────────────────
// Step Indicator
// ──────────────────────────────────────────────

@Composable
fun StepIndicator(current: VokabelTabScreen) {
    val steps = listOf(
        "Bild" to VokabelTabScreen.UPLOAD,
        "Prüfen" to VokabelTabScreen.REVIEW,
        "Lernen" to VokabelTabScreen.LEARN
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { i, (label, step) ->
            val active = current == step
            val done = current.ordinal > step.ordinal
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            color = if (active || done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(50)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (done) Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    else Text(
                        "${i + 1}",
                        color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (i < steps.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.width(40.dp),
                    thickness = DividerDefaults.Thickness,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }
    }
}

// ──────────────────────────────────────────────
// Screen 1: Upload & Extract
// ──────────────────────────────────────────────

@Composable
fun UploadScreen(
    bitmap: Bitmap?,
    rawText: String,
    isExtracting: Boolean,
    errorMessage: String?,
    onPickImage: () -> Unit,
    onExtract: () -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            // Image area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onPickImage() },
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Vokabelbild",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.AddPhotoAlternate,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Bild auswählen", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "Format: LATEIN | DEUTSCH",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPickImage, modifier = Modifier.weight(1f)) {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (bitmap != null) "Anderes Bild" else "Bild wählen")
                }
                Button(
                    onClick = onExtract,
                    enabled = bitmap != null && !isExtracting,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isExtracting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Erkenne...")
                    } else {
                        Icon(
                            Icons.Default.DocumentScanner,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Text erkennen")
                    }
                }
            }
        }

        errorMessage?.let {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        if (rawText.isNotEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "Erkannter Text",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            rawText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ──────────────────────────────────────────────
// Screen 2: Review & Edit
// ──────────────────────────────────────────────

@Composable
fun ReviewScreen(
    vokabeln: List<Vokabel>,
    onVokabelnChanged: (List<Vokabel>) -> Unit,
    onStartLearning: () -> Unit,
    onBack: () -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Zurück"
                )
            }
            Text(
                "${vokabeln.size} Vokabeln erkannt",
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.weight(1f))
            Button(onClick = onStartLearning, enabled = vokabeln.isNotEmpty()) {
                Text("Lernen →")
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            "Tippe zum Bearbeiten, wische zum Löschen",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(4.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(vokabeln, key = { "${it.latein}${it.deutsch}" }) { vokabel ->
                var editMode by remember { mutableStateOf(false) }
                var editLatein by remember { mutableStateOf(vokabel.latein) }
                var editDeutsch by remember { mutableStateOf(vokabel.deutsch) }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editMode = !editMode }
                ) {
                    if (editMode) {
                        Column(
                            Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            OutlinedTextField(
                                editLatein,
                                { editLatein = it },
                                label = { Text("Latein") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                editDeutsch,
                                { editDeutsch = it },
                                label = { Text("Deutsch") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Row(
                                horizontalArrangement = Arrangement.End,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                TextButton(onClick = {
                                    onVokabelnChanged(vokabeln.filter { it != vokabel })
                                }) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
                                Spacer(Modifier.width(8.dp))
                                Button(onClick = {
                                    val idx = vokabeln.indexOf(vokabel)
                                    onVokabelnChanged(
                                        vokabeln.toMutableList().also {
                                            it[idx] = Vokabel(editLatein.trim(), editDeutsch.trim())
                                        })
                                    editMode = false
                                }) { Text("Speichern") }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                vokabel.latein,
                                modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.Medium
                            )
                            HorizontalDivider(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(20.dp),
                                thickness = DividerDefaults.Thickness,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                            Text(
                                vokabel.deutsch,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.End,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

// ──────────────────────────────────────────────
// Screen 3: Flashcard Learning
// ──────────────────────────────────────────────

@Composable
fun LearnScreen(vokabeln: List<Vokabel>, onBack: () -> Unit) {
    var shuffled by remember { mutableStateOf(vokabeln.shuffled()) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var showDeutsch by remember { mutableStateOf(false) }
    var showAnswer by remember { mutableStateOf(false) }
    var correct by remember { mutableIntStateOf(0) }
    var wrong by remember { mutableIntStateOf(0) }
    val flipped by animateFloatAsState(targetValue = if (showAnswer) 180f else 0f, label = "flip")

    val done = currentIndex >= shuffled.size

    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }
            Text(
                "Karteikarten",
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.weight(1f))
            // Lat→De / De→Lat toggle
            FilterChip(
                selected = showDeutsch,
                onClick = { showDeutsch = !showDeutsch; showAnswer = false },
                label = { Text(if (showDeutsch) "DE → LA" else "LA → DE") }
            )
        }

        if (!done) {
            // Progress
            LinearProgressIndicator(
                progress = { currentIndex.toFloat() / shuffled.size },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                color = ProgressIndicatorDefaults.linearColor,
                trackColor = ProgressIndicatorDefaults.linearTrackColor,
                strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
            )
            Text(
                "${currentIndex + 1} / ${shuffled.size}  ✓ $correct  ✗ $wrong",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(Modifier.height(24.dp))

            val vokabel = shuffled[currentIndex]
            val frontText = if (showDeutsch) vokabel.deutsch else vokabel.latein
            val backText = if (showDeutsch) vokabel.latein else vokabel.deutsch
            val frontLabel = if (showDeutsch) "Deutsch" else "Latein"
            val backLabel = if (showDeutsch) "Latein" else "Deutsch"

            // Flashcard
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .padding(horizontal = 16.dp)
                    .graphicsLayer { rotationY = flipped; cameraDistance = 12f * density }
                    .clickable { showAnswer = !showAnswer },
                contentAlignment = Alignment.Center
            ) {
                if (flipped <= 90f) {
                    // Front
                    Card(
                        modifier = Modifier.fillMaxSize(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        elevation = CardDefaults.cardElevation(8.dp)
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    frontLabel.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.6f)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    frontText,
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    "Tippe zum Aufdecken",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.5f)
                                )
                            }
                        }
                    }
                } else {
                    // Back (mirrored)
                    Card(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { rotationY = 180f },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        elevation = CardDefaults.cardElevation(8.dp)
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    backLabel.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(0.6f)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    backText,
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Answer buttons (only visible when card is flipped)
            AnimatedContent(
                targetState = showAnswer,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "buttons"
            ) { revealed ->
                if (revealed) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { wrong++; showAnswer = false; currentIndex++ },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Falsch", color = MaterialTheme.colorScheme.error)
                        }
                        Button(
                            onClick = { correct++; showAnswer = false; currentIndex++ },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Richtig", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                } else {
                    Box(Modifier.height(48.dp)) // placeholder height
                }
            }
        } else {
            // Results
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Card(modifier = Modifier.padding(24.dp)) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("🎉 Fertig!", fontSize = 32.sp)
                        Text(
                            "${shuffled.size} Vokabeln abgefragt",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "$correct",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text("Richtig", style = MaterialTheme.typography.labelSmall)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "$wrong",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text("Falsch", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Button(onClick = {
                            shuffled = vokabeln.shuffled()
                            currentIndex = 0; correct = 0; wrong = 0; showAnswer = false
                        }) { Text("Nochmal") }
                        OutlinedButton(onClick = onBack) { Text("Zurück zur Übersicht") }
                    }
                }
            }
        }
    }
}

// ──────────────────────────────────────────────
// Helpers
// ──────────────────────────────────────────────

fun uriToBitmap(context: android.content.Context, uri: Uri): Bitmap? {
    return try {
        android.graphics.ImageDecoder.decodeBitmap(
            android.graphics.ImageDecoder.createSource(
                context.contentResolver,
                uri
            )
        )
            .copy(Bitmap.Config.ARGB_8888, true)
    } catch (_: Exception) {
        null
    }
}

suspend fun extractTextFromBitmap(bitmap: Bitmap): String {
    val image = InputImage.fromBitmap(bitmap, 0)
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    val result = recognizer.process(image).await()
    return result.text
}

suspend fun extractVokabelnFromBitmap(bitmap: Bitmap): List<Vokabel> {
    val image = InputImage.fromBitmap(bitmap, 0)
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    val result = recognizer.process(image).await()

    data class OcrLine(val text: String, val left: Int, val right: Int, val top: Int, val bottom: Int) {
        val cx get() = (left + right) / 2
    }

    val w = bitmap.width.toFloat()

    // 3-Spalten-Zonen: Latein <45%, Deutsch 45–82%, Wortfamilien >82% (ignorieren)
    val zoneLatinMax  = (w * 0.45f).toInt()
    val zoneDeutschMin = (w * 0.44f).toInt()
    val zoneDeutschMax = (w * 0.82f).toInt()

    val allLines = result.textBlocks
        .flatMap { it.lines }
        .mapNotNull { line ->
            val b = line.boundingBox ?: return@mapNotNull null
            OcrLine(line.text.trim(), b.left, b.right, b.top, b.bottom)
        }

    // Kopfzeilen / kurze Artefakte herausfiltern (z.B. "Lernwörter", Seitenzahlen)
    val headerKeywords = setOf("lernwörter", "lernwörter", "lernwort")
    fun OcrLine.isNoise() =
        text.length < 2 || headerKeywords.any { text.lowercase().contains(it) }

    val leftLines  = allLines.filter { it.cx <= zoneLatinMax  && !it.isNoise() }.sortedBy { it.top }
    val rightLines = allLines.filter { it.left in zoneDeutschMin..zoneDeutschMax && !it.isNoise() }.sortedBy { it.top }

    data class Block(val text: String, val top: Int, val bottom: Int)

    // Zeilenabstand-Schwelle: 4% der Bildhöhe → selber Eintrag
    val gapThreshold = bitmap.height * 0.045f

    fun List<OcrLine>.mergeBlocks(): List<Block> {
        val blocks = mutableListOf<Block>()
        var buf = ""; var top = 0; var bottom = 0
        for (line in this) {
            when {
                buf.isEmpty() -> { buf = line.text; top = line.top; bottom = line.bottom }
                line.top - bottom < gapThreshold -> {
                    buf += ", ${line.text}"; bottom = line.bottom
                }
                else -> {
                    blocks += Block(buf, top, bottom)
                    buf = line.text; top = line.top; bottom = line.bottom
                }
            }
        }
        if (buf.isNotEmpty()) blocks += Block(buf, top, bottom)
        return blocks
    }

    val latinBlocks  = leftLines.mergeBlocks()
    val germanBlocks = rightLines.mergeBlocks()

    return latinBlocks.mapNotNull { latin ->
        // Bestes Match = maximale Y-Überschneidung ODER minimaler Y-Abstand
        val matched = germanBlocks.maxByOrNull { german ->
            val overlapTop    = maxOf(latin.top, german.top)
            val overlapBottom = minOf(latin.bottom, german.bottom)
            val overlap = maxOf(0, overlapBottom - overlapTop)
            // Wenn kein direkter Overlap: negativen Abstand als Nähe-Score
            if (overlap > 0) overlap.toFloat()
            else -minOf(
                abs(latin.top - german.bottom),
                abs(latin.bottom - german.top)
            ).toFloat()
        } ?: return@mapNotNull null

        // Zu weit entfernte Paarungen ablehnen (>8% Bildhöhe Abstand)
        val dist = minOf(
            abs(latin.top - matched.bottom),
            abs(latin.bottom - matched.top),
            abs((latin.top + latin.bottom) / 2 - (matched.top + matched.bottom) / 2)
        )
        if (dist > bitmap.height * 0.08f) return@mapNotNull null

        Vokabel(latin.text, matched.text)
    }.distinctBy { it.latein }
}