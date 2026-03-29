package com.cloud.jsoneditor

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import org.json.JSONArray
import java.io.File

@Composable
fun JsonEditorContent(
    filePath: String,
    fileUri: Uri?,
    context: Context,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var jsonContent by remember { mutableStateOf(loadJsonFile(context, filePath, fileUri)) }
    var isEditing by remember { mutableStateOf(false) }
    var editedContent by remember { mutableStateOf(jsonContent) }
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF2A2A2A))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "JSON Editor",
                color = Color.White,
                fontSize = 20.sp,
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = onClose) {
                Text("✕", color = Color.White, fontSize = 24.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = filePath,
            color = Color.Gray,
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = {
                    if (isEditing) {
                        if (validateJson(editedContent)) {
                            saveJsonFile(context, filePath, fileUri, editedContent)
                            jsonContent = editedContent
                            isEditing = false
                            Toast.makeText(context, "Gespeichert!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Ungültiges JSON-Format!", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        editedContent = jsonContent
                        isEditing = true
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isEditing) Color(0xFF4CAF50) else Color(0xFF2196F3)
                )
            ) {
                Text(if (isEditing) "💾 Speichern" else "✏️ Bearbeiten")
            }

            if (isEditing) {
                Button(
                    onClick = {
                        editedContent = jsonContent
                        isEditing = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF5722)
                    )
                ) {
                    Text("✖ Abbrechen")
                }
            }

            Button(
                onClick = {
                    val formatted = formatJson(if (isEditing) editedContent else jsonContent)
                    if (isEditing) {
                        editedContent = formatted
                    } else {
                        jsonContent = formatted
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF9C27B0)
                )
            ) {
                Text("🔧 Formatieren")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E1E1E)
            )
        ) {
            if (isEditing) {
                TextField(
                    value = editedContent,
                    onValueChange = { editedContent = it },
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(8.dp),
                    textStyle = LocalTextStyle.current.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = Color(0xFFE0E0E0)
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = Color.White
                    )
                )
            } else {
                Text(
                    text = jsonContent,
                    color = Color(0xFFE0E0E0),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (isEditing) "⚠️ Im Bearbeitungsmodus" else "👁️ Anzeigemodus",
            color = if (isEditing) Color(0xFFFFA500) else Color.Gray,
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

private fun loadJsonFile(context: Context, filePath: String, fileUri: Uri?): String {
    return try {
        if (fileUri != null && fileUri.scheme == "content") {
            context.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                inputStream.bufferedReader().use { it.readText() }
            } ?: throw Exception("Konnte Datei nicht öffnen")
        } else {
            File(filePath).readText()
        }
    } catch (e: Exception) {
        "{\n  \"error\": \"Datei konnte nicht geladen werden\",\n  \"message\": \"${e.message}\"\n}"
    }
}

private fun saveJsonFile(context: Context, filePath: String, fileUri: Uri?, content: String) {
    try {
        if (fileUri != null && fileUri.scheme == "content") {
            context.contentResolver.openOutputStream(fileUri)?.use { outputStream ->
                outputStream.write(content.toByteArray())
            } ?: throw Exception("Konnte nicht in Datei schreiben")
        } else {
            File(filePath).writeText(content)
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Fehler beim Speichern: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

private fun validateJson(content: String): Boolean {
    return try {
        JSONObject(content)
        true
    } catch (e1: Exception) {
        try {
            JSONArray(content)
            true
        } catch (e2: Exception) {
            false
        }
    }
}

private fun formatJson(content: String): String {
    return try {
        val jsonObject = JSONObject(content)
        jsonObject.toString(2)
    } catch (e1: Exception) {
        try {
            val jsonArray = JSONArray(content)
            jsonArray.toString(2)
        } catch (e2: Exception) {
            content
        }
    }
}