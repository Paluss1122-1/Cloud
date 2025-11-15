package com.example.cloud.browsertab

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction


@Composable
fun BrowserTabContent(
    url: String,
    onUrlChange: (String) -> Unit,
    onEnterFullScreen: () -> Unit,
    webViewState: WebView?,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF2A2A2A))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally, // zentriert alle Kinder horizontal
        verticalArrangement = Arrangement.Center // optional: zentriert auch vertikal im gesamten Column
    ) {
        TextField(
            value = url,
            onValueChange = onUrlChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp), // optional: feste Höhe für konsistentes Aussehen
            singleLine = true,
            placeholder = { Text("URL hier eingeben", color = Color.Gray) },
            colors = TextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color.White,
                focusedContainerColor = Color(0xFF333333),
                unfocusedContainerColor = Color(0xFF333333),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(
                onGo = {
                    val newUrl = if (url.startsWith("http://") || url.startsWith("https://")) {
                        url
                    } else {
                        "https://$url"
                    }
                    webViewState?.loadUrl(newUrl)
                    keyboardController?.hide()
                }
            )
        )

        Spacer(modifier = Modifier.height(16.dp)) // Abstand zwischen Input und Button

        Button(
            onClick = {
                val newUrl = if (url.startsWith("http://") || url.startsWith("https://")) {
                    url
                } else {
                    "https://$url"
                }
                webViewState?.loadUrl(newUrl)
                keyboardController?.hide()
                onEnterFullScreen()
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4CAF50),
                contentColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth() // oder .defaultMinSize(minWidth = ...) je nach Wunsch
                .height(56.dp)
        ) {
            Text("Öffnen", fontSize = 20.sp)
        }
    }
}