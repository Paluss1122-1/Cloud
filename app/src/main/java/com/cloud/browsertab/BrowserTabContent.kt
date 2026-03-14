package com.cloud.browsertab

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloud.privatecloudapp.loadLastUrl

@Composable
fun BrowserTabContent(
    url: String,
    onUrlChange: (String) -> Unit,
    onEnterFullScreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current

    fun loadAndOpenUrl(targetUrl: String) {
        val finalUrl = if (targetUrl.startsWith("http://") || targetUrl.startsWith("https://")) {
            targetUrl
        } else {
            "https://$targetUrl"
        }
        
        onUrlChange(finalUrl)
        keyboardController?.hide()
        onEnterFullScreen()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        TextField(
            value = url,
            onValueChange = onUrlChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("URL eingeben...", color = Color.Gray) },
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
                onGo = { loadAndOpenUrl(url) }
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { loadAndOpenUrl(url) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Öffnen", fontSize = 20.sp)
        }

        Spacer(modifier = Modifier.height(32.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            QuickLinkButton("YouTube", Color.Red) { 
                loadAndOpenUrl("https://www.youtube.com") 
            }
            QuickLinkButton("Gmail", Color(0xFFFFA500)) { 
                loadAndOpenUrl("https://www.gmail.com") 
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        LastUrlButton(context) { lastUrl ->
            loadAndOpenUrl(lastUrl)
        }
    }
}

@Composable
fun QuickLinkButton(
    text: String, 
    bgColor: Color, 
    textColor: Color = Color.White, 
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = bgColor)
    ) {
        Text(text, fontSize = 12.sp, color = textColor)
    }
}

@Composable
fun LastUrlButton(context: Context, onUrlLoad: (String) -> Unit) {
    val lastUrl = remember { loadLastUrl(context) }

    Button(
        onClick = { onUrlLoad(lastUrl) },
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF444444))
    ) {
        Text("🔙 Letzte URL laden", fontSize = 14.sp)
    }
}