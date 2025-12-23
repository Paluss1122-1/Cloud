package com.example.cloud.Authenticator

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.room.Room
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import kotlinx.coroutines.*
import java.net.URLDecoder
import kotlin.apply
import kotlin.collections.any
import kotlin.jvm.java
import kotlin.let
import kotlin.text.equals
import kotlin.text.isNullOrBlank
import kotlin.text.removePrefix
import androidx.core.net.toUri

class SilentCaptureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SilentCaptureScreen()
        }
    }
}

@Composable
fun SilentCaptureScreen() {
    val scope = rememberCoroutineScope()
    var isProcessing by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Kameraansicht (Barcode Scanner)
        AndroidView(
            factory = { ctx ->
                DecoratedBarcodeView(ctx).apply {
                    // Laser + Rahmen unsichtbar machen
                    viewFinder.visibility = View.GONE
                    setStatusText("")

                    decodeContinuous(object : BarcodeCallback {
                        override fun barcodeResult(result: BarcodeResult?) {
                            if (!isProcessing && result?.text != null) {
                                isProcessing = true
                                pause()

                                scope.launch(Dispatchers.IO) {
                                    handleQrCode(result.text, ctx)
                                }
                            }
                        }

                        override fun possibleResultPoints(resultPoints: MutableList<com.google.zxing.ResultPoint>?) {}
                    })
                    resume()
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Eigenes Overlay-Design (Rahmen + Text)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .size(250.dp)
                    .border(4.dp, Color(0xFF9B4DCA), RoundedCornerShape(16.dp))
                    .background(Color.Transparent)
            )
            Spacer(Modifier.height(40.dp))
            Text(
                text = "Halte den QR-Code in den Rahmen",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

suspend fun handleQrCode(qrText: String, context: Context) {
    try {
        val decodedText = URLDecoder.decode(qrText, "UTF-8")
        val uri = decodedText.toUri()

        if (uri.scheme != "otpauth") {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "❌ Ungültiges Format!", Toast.LENGTH_LONG).show()
                (context as? Activity)?.finish()
            }
            return
        }

        val label = uri.path?.removePrefix("/") ?: "Unbekannt"
        val secretParam = uri.getQueryParameter("secret")
        val issuerParam = uri.getQueryParameter("issuer")
        val displayName = issuerParam?.let { "$it ($label)" } ?: label

        if (secretParam.isNullOrBlank()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "❌ Kein Secret gefunden!", Toast.LENGTH_LONG).show()
                (context as? Activity)?.finish()
            }
            return
        }

        val db = Room.databaseBuilder(
            context,
            TwoFADatabase::class.java,
            "twofa_database"
        )
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

        // Prüfen ob bereits vorhanden
        val existingEntries = db.twoFADao().getAll()
        val alreadyExists = existingEntries.any {
            it.secret == secretParam || it.name.equals(displayName, ignoreCase = true)
        }

        if (alreadyExists) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "⚠️ Eintrag existiert bereits!", Toast.LENGTH_LONG).show()
                (context as? Activity)?.finish()
            }
            return
        }

        // Neuen Eintrag erstellen
        val newEntry = TwoFAEntry(
            name = displayName,
            secret = secretParam,
            folder = null
        )

        // 1. Lokal in Room speichern
        db.twoFADao().insert(newEntry)

        // 2. Zu Supabase hochladen und ID zurückbekommen
        val supabaseSuccess = saveTwoFaEntryToSupabase(newEntry, db)

        // 3. User-Feedback
        withContext(Dispatchers.Main) {
            val message = if (supabaseSuccess) {
                "✅ Token für $displayName hinzugefügt (lokal & Cloud)!"
            } else {
                "✅ Token für $displayName hinzugefügt (Cloud-Sync fehlgeschlagen)"
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()

            // Activity beenden und zurück zur Hauptseite
            (context as? Activity)?.finish()
        }

    } catch (e: Exception) {
        Log.e("QR_SCAN", "Fehler beim Verarbeiten: ${e.message}", e)
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "❌ Fehler: ${e.message}", Toast.LENGTH_LONG).show()
            (context as? Activity)?.finish()
        }
    }
}