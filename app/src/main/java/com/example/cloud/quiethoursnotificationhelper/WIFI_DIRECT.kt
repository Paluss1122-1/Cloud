package com.example.cloud.quiethoursnotificationhelper

import android.app.NotificationManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import com.example.cloud.service.QuietHoursNotificationService.Companion.CHANNEL_ID
import com.example.cloud.showSimpleNotificationExtern
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.util.Timer
import java.util.TimerTask
import kotlin.time.Duration.Companion.seconds

private var updateServerSocket: ServerSocket? = null
private var listenerTimer: Timer? = null
private var listenerJob: kotlinx.coroutines.Job? = null

data class TodoItem(
    val id: Long,
    val text: String,
    val completed: Boolean,
    val timestamp: Long
)

fun getTodos(context: Context): List<TodoItem> {
    val prefs = context.getSharedPreferences("todos_prefs", MODE_PRIVATE)
    val todosJson = prefs.getString("todos", "[]") ?: "[]"

    try {
        val todos = mutableListOf<TodoItem>()
        val jsonArray = org.json.JSONArray(todosJson)

        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            todos.add(
                TodoItem(
                    id = obj.getLong("id"),
                    text = obj.getString("text"),
                    completed = obj.getBoolean("completed"),
                    timestamp = obj.getLong("timestamp")
                )
            )
        }
        return todos
    } catch (e: Exception) {
        Log.e("TodoManager", "Error parsing todos", e)
        return emptyList()
    }
}

fun saveTodos(context: Context, todos: List<TodoItem>) {
    val prefs = context.getSharedPreferences("todos_prefs", MODE_PRIVATE)

    try {
        val jsonArray = org.json.JSONArray()
        todos.forEach { todo ->
            val obj = org.json.JSONObject().apply {
                put("id", todo.id)
                put("text", todo.text)
                put("completed", todo.completed)
                put("timestamp", todo.timestamp)
            }
            jsonArray.put(obj)
        }

        prefs.edit(commit = true) {
            putString("todos", jsonArray.toString())
        }
    } catch (e: Exception) {
        Log.e("TodoManager", "Error saving todos", e)
    }
}

fun addTodo(text: String, context: Context) {
    val todos = getTodos(context).toMutableList()
    val newTodo = TodoItem(
        id = System.currentTimeMillis(),
        text = text,
        completed = false,
        timestamp = System.currentTimeMillis()
    )
    todos.add(newTodo)
    saveTodos(context, todos)

    showSimpleNotificationExtern(
        "✅ To-do hinzugefügt",
        "\"$text\"\n\nGesamt: ${todos.size} To-dos",
        20.seconds,
        context
    )
}

fun completeTodo(index: Int, context: Context) {
    val todos = getTodos(context).toMutableList()

    if (index in todos.indices) {
        todos[index] = todos[index].copy(completed = true)
        saveTodos(context, todos)

        showSimpleNotificationExtern(
            "✓ Erledigt",
            "\"${todos[index].text}\"",
            20.seconds,
            context
        )
    } else {
        showSimpleNotificationExtern(
            "❌ Fehler",
            "To-do #${index + 1} existiert nicht",
            20.seconds,
            context
        )
    }
}

fun removeTodo(index: Int, context: Context) {
    val todos = getTodos(context).toMutableList()

    if (index in todos.indices) {
        val removed = todos.removeAt(index)
        saveTodos(context, todos)

        showSimpleNotificationExtern(
            "🗑️ Gelöscht",
            "\"${removed.text}\"",
            20.seconds,
            context
        )
    } else {
        showSimpleNotificationExtern(
            "❌ Fehler",
            "To-do #${index + 1} existiert nicht",
            20.seconds,
            context
        )
    }
}

fun showAllTodos(context: Context) {
    val todos = getTodos(context)

    if (todos.isEmpty()) {
        showSimpleNotificationExtern(
            "📝 To-dos",
            "Keine To-dos vorhanden\n\nErstelle eins mit: * \"deine aufgabe\"",
            context = context
        )
        return
    }

    val activeTodos = todos.filter { !it.completed }
    val completedTodos = todos.filter { it.completed }

    val todoText = buildString {
        if (activeTodos.isNotEmpty()) {
            append("📌 OFFEN (${activeTodos.size}):\n")
            activeTodos.forEachIndexed { index, todo ->
                append("${todos.indexOf(todo) + 1}. ${todo.text}\n")
            }
        }

        if (completedTodos.isNotEmpty()) {
            if (activeTodos.isNotEmpty()) append("\n")
            append("✓ ERLEDIGT (${completedTodos.size}):\n")
            completedTodos.forEachIndexed { _, todo ->
                append("${todos.indexOf(todo) + 1}. ${todo.text}\n")
            }
        }
    }

    val chunks = splitText(todoText)

    val notificationManager = context.getSystemService(NotificationManager::class.java)

    chunks.forEachIndexed { index, chunk ->
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle("📝 To-do Liste (${todos.size})")
            .setStyle(NotificationCompat.BigTextStyle().bigText(chunk))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(60000 + index, notification)
    }
}

@OptIn(DelicateCoroutinesApi::class)
fun syncTodosWithLaptop(context: Context) {
    showSimpleNotificationExtern(
        "🔄 Sync gestartet",
        "Verbinde mit Laptop...",
        10.seconds,
        context
    )

    GlobalScope.launch {
        try {
            val todos = getTodos(context)
            val todosJson = org.json.JSONArray()

            todos.forEach { todo ->
                val obj = org.json.JSONObject().apply {
                    put("id", todo.id)
                    put("text", todo.text)
                    put("completed", todo.completed)
                    put("timestamp", todo.timestamp)
                }
                todosJson.put(obj)
            }

            // Socket-Verbindung zum Laptop (IP muss angepasst werden)
            val socket = java.net.Socket()
            socket.connect(java.net.InetSocketAddress("192.168.178.20", 8888), 5000)

            val writer = java.io.PrintWriter(socket.getOutputStream(), true)
            writer.println(todosJson.toString())
            writer.flush()

            socket.close()

            Handler(Looper.getMainLooper()).post {
                startUpdateListener(context, 60)

                showSimpleNotificationExtern(
                    "✅ Sync erfolgreich",
                    "${todos.size} To-dos übertragen\n🔄 Listener aktiv für 60min",
                    20.seconds,
                    context
                )
            }
        } catch (e: Exception) {
            Log.e("TodoSync", "Sync failed", e)
            android.os.Handler(Looper.getMainLooper()).post {
                showSimpleNotificationExtern(
                    "❌ Sync fehlgeschlagen",
                    "Laptop nicht erreichbar: ${e.message}\n\nStelle sicher, dass das Python-Script läuft",
                    20.seconds,
                    context
                )
            }
        }
    }
}

@OptIn(DelicateCoroutinesApi::class)
fun startUpdateListener(context: Context, durationMinutes: Int = 60) {
    stopUpdateListener()

    startClipboardSync(context)

    Log.d("TodoSync", "Starte Update Listener für $durationMinutes Minuten")

    listenerJob = GlobalScope.launch(Dispatchers.IO) {
        try {
            updateServerSocket = ServerSocket(8890)
            Log.d("TodoSync", "✅ Update Listener läuft auf Port 8890")

            while (!Thread.currentThread().isInterrupted) {
                try {
                    val client = updateServerSocket?.accept()
                    if (client != null) {
                        Log.d("TodoSync", "📥 Update empfangen von ${client.inetAddress}")

                        val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                        val jsonData = reader.readLine()
                        client.close()

                        if (jsonData != null) {
                            val updatedTodos = parseTodosFromJson(jsonData)
                            saveTodos(context, updatedTodos)

                            withContext(Dispatchers.Main) {
                                showSimpleNotificationExtern(
                                    "🔄 To-dos aktualisiert",
                                    "Änderungen vom Laptop empfangen",
                                    10.seconds,
                                    context
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (e !is java.net.SocketException && !Thread.currentThread().isInterrupted) {
                        Log.e("TodoSync", "Fehler beim Empfangen", e)
                    }
                }
            }
        } catch (e: Exception) {
            if (e !is java.net.SocketException) {
                Log.e("TodoSync", "Update Listener Fehler", e)
            }
        }
    }

    // Timer: Stoppe nach X Minuten
    listenerTimer = Timer()
    listenerTimer?.schedule(object : TimerTask() {
        override fun run() {
            Log.d("TodoSync", "⏰ $durationMinutes Minuten abgelaufen, stoppe Listener")
            stopUpdateListener()

            Handler(Looper.getMainLooper()).post {
                stopClipboardSync(context)

                showSimpleNotificationExtern(
                    "⏸️ Sync-Listener gestoppt",
                    "Nach $durationMinutes min automatisch beendet.\nSynce erneut für Reaktivierung.",
                    15.seconds,
                    context
                )
            }
        }
    }, durationMinutes * 60L * 1000L)
}

fun stopUpdateListener() {
    try {
        // Stoppe Timer
        listenerTimer?.cancel()
        listenerTimer = null

        // Stoppe Coroutine
        listenerJob?.cancel()
        listenerJob = null

        // Schließe Socket
        updateServerSocket?.close()
        updateServerSocket = null

        Log.d("TodoSync", "🛑 Update Listener gestoppt")
    } catch (e: Exception) {
        Log.e("TodoSync", "Fehler beim Schließen des Servers", e)
    }
}

private fun parseTodosFromJson(jsonData: String): List<TodoItem> {
    try {
        val todos = mutableListOf<TodoItem>()
        val jsonArray = org.json.JSONArray(jsonData)

        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            todos.add(
                TodoItem(
                    id = obj.getLong("id"),
                    text = obj.getString("text"),
                    completed = obj.getBoolean("completed"),
                    timestamp = obj.getLong("timestamp")
                )
            )
        }
        return todos
    } catch (e: Exception) {
        Log.e("TodoSync", "Fehler beim Parsen", e)
        return emptyList()
    }
}

private fun sendClipboardToLaptop(context: Context, text: String) {
    GlobalScope.launch(Dispatchers.IO) {
        try {
            val socket = java.net.Socket()
            socket.connect(java.net.InetSocketAddress("192.168.178.20", 8891), 3000)

            val writer = java.io.PrintWriter(socket.getOutputStream(), true)
            writer.println("CLIPBOARD:$text")
            writer.flush()
            socket.close()

            Log.d("ClipboardSync", "📋 Zwischenablage an Laptop gesendet")
        } catch (e: Exception) {
            Log.e("ClipboardSync", "Fehler beim Senden", e)
        }
    }
}

// Neue Funktion zum Überwachen der Zwischenablage
private var clipboardListener: ClipboardManager.OnPrimaryClipChangedListener? = null

fun startClipboardSync(context: Context) {
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        val clip = clipboardManager.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString()
            if (!text.isNullOrEmpty()) {
                sendClipboardToLaptop(context, text)
            }
        }
    }

    clipboardManager.addPrimaryClipChangedListener(clipboardListener)
    Log.d("ClipboardSync", "📋 Clipboard Sync aktiviert")
}

fun stopClipboardSync(context: Context) {
    if (clipboardListener != null) {
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.removePrimaryClipChangedListener(clipboardListener)
        clipboardListener = null
        Log.d("ClipboardSync", "📋 Clipboard Sync deaktiviert")
    }
}

private fun splitText(text: String): List<String> {
    val parts = mutableListOf<String>()
    var start = 0

    while (start < text.length) {
        val end = minOf(start + 200, text.length)
        parts.add(text.substring(start, end))
        start = end
    }
    return parts
}