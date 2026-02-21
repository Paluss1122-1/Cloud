package com.example.cloud.authenticator

import android.app.Activity
import android.content.Context
import android.os.Bundle
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
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import kotlinx.coroutines.*
import java.net.URLDecoder
import kotlin.apply
import kotlin.collections.any
import kotlin.let
import kotlin.text.equals
import kotlin.text.isNullOrBlank
import kotlin.text.removePrefix
import androidx.core.net.toUri
import com.example.cloud.ERRORINSERT
import com.example.cloud.ERRORINSERTDATA
import java.time.Instant

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
        AndroidView(
            factory = { ctx ->
                DecoratedBarcodeView(ctx).apply {
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
        val decodedText = withContext(Dispatchers.IO) {
            URLDecoder.decode(qrText, "UTF-8")
        }
        val uri = decodedText.toUri()

        if (uri.scheme != "otpauth") {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "❌ Ungültiges Format!", Toast.LENGTH_LONG).show()
                ERRORINSERT(
                    ERRORINSERTDATA(
                        "Capture Activity",
                        "❌ Ungültiges Format! (${uri})",
                        Instant.now().toString(),
                        "Error"
                    )
                )
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
                ERRORINSERT(
                    ERRORINSERTDATA(
                        "Capture Activity",
                        "❌ Kein Secret gefunden! (uri: $uri, secret: $secretParam)",
                        Instant.now().toString(),
                        "Error"
                    )
                )
                (context as? Activity)?.finish()
            }
            return
        }

        val db = TwoFADatabase.getDatabase(context)

        val existingEntries = db.twoFADao().getAll()
        val alreadyExists = existingEntries.any {
            it.secret == secretParam || it.name.equals(displayName, ignoreCase = true)
        }

        if (alreadyExists) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "⚠️ Eintrag existiert bereits!", Toast.LENGTH_LONG).show()
                (context as? Activity)?.finish()
            }
            ERRORINSERT(
                ERRORINSERTDATA(
                    "Capture Activity",
                    "⚠️ Eintrag existiert bereits! (secret: ${secretParam}, name: $displayName)",
                    Instant.now().toString(),
                    "Warning"
                )
            )
            return
        }

        val newEntry = TwoFAEntry(
            name = displayName,
            secret = secretParam
        )

        db.twoFADao().insert(newEntry)

        val supabaseSuccess = saveTwoFaEntryToSupabase(newEntry, db)

        withContext(Dispatchers.Main) {
            val message = if (supabaseSuccess) {
                "✅ Token für $displayName hinzugefügt (lokal & Cloud)!"
            } else {
                "✅ Token für $displayName hinzugefügt (Cloud-Sync fehlgeschlagen)"
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()

            (context as? Activity)?.finish()
        }

    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "❌ Fehler: ${e.message}", Toast.LENGTH_LONG).show()
            (context as? Activity)?.finish()
        }
        ERRORINSERT(
            ERRORINSERTDATA(
                "Capture Activity",
                "❌ Fehler: ${e.message}",
                Instant.now().toString(),
                "Error"
            )
        )
    }
}