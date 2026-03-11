package com.cloud.quiethoursnotificationhelper

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import com.cloud.SupabaseConfigALT
import com.cloud.service.QuietHoursNotificationService
import com.cloud.service.QuietHoursNotificationService.Companion.ACTION_CANCEL_DELETE
import com.cloud.service.QuietHoursNotificationService.Companion.ACTION_CONFIRM_DELETE_IMAGE
import com.cloud.service.QuietHoursNotificationService.Companion.ACTION_DELETE_IMAGE
import com.cloud.service.QuietHoursNotificationService.Companion.ACTION_NEXT_GALLERY_IMAGE
import com.cloud.service.QuietHoursNotificationService.Companion.ACTION_PREV_GALLERY_IMAGE
import com.cloud.service.QuietHoursNotificationService.Companion.DELETE_CONFIRMATION_CHANNEL_ID
import com.cloud.service.QuietHoursNotificationService.Companion.EXTRA_IMAGE_INDEX
import com.cloud.service.QuietHoursNotificationService.Companion.GALLERY_CHANNEL_ID
import com.cloud.service.QuietHoursNotificationService.Companion.currentGalleryIndex
import com.cloud.service.QuietHoursNotificationService.Companion.galleryImages
import com.cloud.showSimpleNotificationExtern
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

data class GalleryImage(
    val uri: Uri,
    val lastModified: Long,
    val createdAt: Long,
    val displayName: String? = null
)

@OptIn(DelicateCoroutinesApi::class)
fun uploadCurrentGalleryImageToSupabase(date: String, imageName: String?, context: Context) {
    GlobalScope.launch(Dispatchers.IO) {
        try {
            if (galleryImages.isEmpty()) {
                Handler(Looper.getMainLooper()).post {
                    showSimpleNotificationExtern(
                        "❌ Keine Galerie",
                        "Öffne zuerst die Galerie mit 'gallery'",
                        20.seconds,
                        context
                    )
                }
                return@launch
            }

            if (currentGalleryIndex < 0 || currentGalleryIndex >= galleryImages.size) {
                Handler(Looper.getMainLooper()).post {
                    showSimpleNotificationExtern(
                        "❌ Fehler",
                        "Ungültiger Galerie-Index",
                        20.seconds,
                        context
                    )
                }
                return@launch
            }

            val imageUri = galleryImages[currentGalleryIndex].uri

            val imageName = if (imageName == null) {
                val cursor = context.contentResolver.query(
                    imageUri,
                    arrayOf(MediaStore.Images.Media.DISPLAY_NAME),
                    null,
                    null,
                    null
                )
                val name = cursor?.use {
                    if (it.moveToFirst()) {
                        it.getString(0)?.substringBeforeLast(".") ?: "image"
                    } else {
                        "image"
                    }
                } ?: "image"
                cursor?.close()
                name
            } else {
                imageName
            }

            // Bild als ByteArray laden
            val inputStream = context.contentResolver.openInputStream(imageUri)
            val imageBytes = inputStream?.readBytes()
            inputStream?.close()

            if (imageBytes == null) {
                Handler(Looper.getMainLooper()).post {
                    showSimpleNotificationExtern(
                        "❌ Fehler",
                        "Bild konnte nicht gelesen werden",
                        20.seconds,
                        context
                    )
                }
                return@launch
            }

            // Dateiendung ermitteln
            val mimeType = context.contentResolver.getType(imageUri) ?: "image/jpeg"
            val extension = when (mimeType) {
                "image/jpeg" -> "jpg"
                "image/png" -> "png"
                "image/webp" -> "webp"
                else -> "jpg"
            }

            val supabaseUrl = SupabaseConfigALT.SUPABASE_URL
            val supabaseKey = SupabaseConfigALT.SUPABASE_PUBLISHABLE_KEY
            val bucketName = "Tagesberichte"
            val imagename = imageName.replace(" ", "_")
            val storagePath = "$date/${imagename}.${extension}"

            val url = URL("$supabaseUrl/storage/v1/object/$bucketName/$storagePath")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $supabaseKey")
            connection.setRequestProperty("apikey", supabaseKey)
            connection.setRequestProperty("Content-Type", mimeType)
            connection.setRequestProperty("x-upsert", "false")
            connection.setRequestProperty("Content-Length", imageBytes.size.toString())
            connection.connectTimeout = 30000
            connection.readTimeout = 60000
            connection.doOutput = true

            connection.outputStream.use { output ->
                output.write(imageBytes)
                output.flush()
            }

            when (val responseCode = connection.responseCode) {
                in 200..299 -> {
                    Handler(Looper.getMainLooper()).post {
                        showSimpleNotificationExtern(
                            "✅ Upload erfolgreich",
                            "Bild '$imagename' wurde hochgeladen",
                            context = context
                        )
                    }
                }

                400 -> {
                    val error =
                        connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    if (error.contains("Duplicate") || error.contains("already exists")) {
                        Log.w("QuietHoursService", "⚠️ Existiert bereits: $imagename")
                        Handler(Looper.getMainLooper()).post {
                            showSimpleNotificationExtern(
                                "⚠️ Bereits vorhanden",
                                "Bild '$imagename' existiert bereits",
                                context = context
                            )
                        }
                    } else {
                        Log.e("QuietHoursService", "❌ Fehler 400: $error")
                        Handler(Looper.getMainLooper()).post {
                            showSimpleNotificationExtern(
                                "❌ Upload fehlgeschlagen",
                                "Fehler 400: $error",
                                20.seconds,
                                context
                            )
                        }
                    }
                }

                409 -> {
                    Log.w("QuietHoursService", "⚠️ Existiert bereits (409): $imagename")
                    Handler(Looper.getMainLooper()).post {
                        showSimpleNotificationExtern(
                            "⚠️ Bereits vorhanden",
                            "Bild '$imagename' existiert bereits",
                            context = context
                        )
                    }
                }

                else -> {
                    val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    Log.e("QuietHoursService", "❌ Fehler $responseCode: $error")
                    Handler(Looper.getMainLooper()).post {
                        showSimpleNotificationExtern(
                            "❌ Upload fehlgeschlagen",
                            "Fehler $responseCode: $error",
                            20.seconds,
                            context
                        )
                    }
                }
            }

        } catch (e: Exception) {
            Log.e("QuietHoursService", "❌ Upload-Fehler", e)
            Handler(Looper.getMainLooper()).post {
                showSimpleNotificationExtern(
                    "❌ Upload fehlgeschlagen",
                    "Fehler: ${e.message}",
                    20.seconds,
                    context
                )
            }
        }
    }
}

fun loadGalleryImages(number: Int, context: Context) {
    Log.d("CURRENTINDEX", "$number")
    try {
        galleryImages = emptyList()
        val images = mutableListOf<GalleryImage>()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DISPLAY_NAME
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"

        val cursor = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )

        cursor?.use {
            val idColumn =
                it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateColumn =
                it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val createdColumn =
                it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val nameColumn =
                it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)

            while (it.moveToNext()) {
                val id = it.getLong(idColumn)
                val dateModified = it.getLong(dateColumn) * 1000 // Sekunden → Millisekunden
                val dateCreated = it.getLong(createdColumn) * 1000 // Sekunden → Millisekunden
                val displayName = it.getString(nameColumn)

                val uri = Uri.withAppendedPath(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id.toString()
                )

                images.add(GalleryImage(uri, dateModified, dateCreated, displayName))

                if (images.size >= 5000) break
            }
        }

        galleryImages = images

        if (galleryImages.isEmpty()) {
            showSimpleNotificationExtern(
                "📷 Galerie leer",
                "Keine Bilder in deiner Galerie gefunden",
                context = context
            )
            return
        }

        // WICHTIG: Bild anzeigen nach dem Laden
        currentGalleryIndex = number
        showGalleryImage(number, context)
    } catch (e: Exception) {
        Log.e("QuietHoursService", "Error loading gallery images", e)
        showSimpleNotificationExtern(
            "❌ Fehler",
            "Galerie konnte nicht geladen werden: ${e.message}",
            20.seconds,
            context = context
        )
    }
}

fun showDeleteConfirmation(imageIndex: Int, context: Context) {
    try {
        if (galleryImages.isEmpty() || imageIndex < 0 || imageIndex >= galleryImages.size) {
            showSimpleNotificationExtern("❌ Fehler", "Ungültiger Bildindex", context = context)
            return
        }

        val imageUri = galleryImages[imageIndex].uri

        val bitmap = try {
            ImageDecoder.decodeBitmap(
                ImageDecoder.createSource(context.contentResolver, imageUri)
            )
        } catch (_: Exception) {
            null
        }

        val deleteIntent = Intent(context, QuietHoursNotificationService::class.java).apply {
            action = ACTION_DELETE_IMAGE
            putExtra(EXTRA_IMAGE_INDEX, imageIndex)
        }
        val deletePendingIntent = PendingIntent.getService(
            context, 81, deleteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(context, QuietHoursNotificationService::class.java).apply {
            action = ACTION_CANCEL_DELETE
        }
        val cancelPendingIntent = PendingIntent.getService(
            context, 82, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, DELETE_CONFIRMATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_delete)
            .setContentTitle("🗑️ Bild löschen?")
            .setContentText("Bild ${imageIndex + 1} von ${galleryImages.size} wirklich löschen?")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setGroup("group_confirmations")
            .addAction(android.R.drawable.ic_delete, "Löschen", deletePendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Abbrechen",
                cancelPendingIntent
            )

        if (bitmap != null) {
            builder.setLargeIcon(bitmap)
                .setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(bitmap)
                        .bigLargeIcon(null as Bitmap?)
                )
        }

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            notificationManager.notify(80000, builder.build())
        }

    } catch (e: Exception) {
        Log.e("QuietHoursService", "Error showing delete confirmation", e)
        showSimpleNotificationExtern(
            "❌ Fehler",
            "Löschbestätigung konnte nicht angezeigt werden: ${e.message}",
            20.seconds,
            context = context
        )
    }
}

fun deleteGalleryImage(imageIndex: Int, context: Context) {
    try {
        if (galleryImages.isEmpty() || imageIndex < 0 || imageIndex >= galleryImages.size) {
            showSimpleNotificationExtern("❌ Fehler", "Ungültiger Bildindex", context = context)
            return
        }

        val imageUri = galleryImages[imageIndex].uri

        val deleted = context.contentResolver.delete(imageUri, null, null)

        if (deleted > 0) {
            val mutableList = galleryImages.toMutableList()
            mutableList.removeAt(imageIndex)
            galleryImages = mutableList

            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.cancel(80000)

            showSimpleNotificationExtern(
                "✅ Gelöscht",
                "Bild wurde erfolgreich gelöscht (${galleryImages.size} verbleibend)",
                context = context
            )

            if (galleryImages.isNotEmpty()) {
                if (currentGalleryIndex >= galleryImages.size) {
                    currentGalleryIndex = galleryImages.size - 1
                }
                showGalleryImage(currentGalleryIndex, context)
            } else {
                notificationManager.cancel(70000)
                showSimpleNotificationExtern(
                    "📷 Galerie leer",
                    "Alle Bilder wurden gelöscht",
                    context = context
                )
            }

        } else {
            showSimpleNotificationExtern(
                "❌ Löschen fehlgeschlagen",
                "Bild konnte nicht gelöscht werden (keine Berechtigung?)",
                20.seconds,
                context = context
            )
        }

    } catch (e: Exception) {
        Log.e("QuietHoursService", "Error deleting gallery image", e)
        showSimpleNotificationExtern(
            "❌ Fehler",
            "Fehler beim Löschen: ${e.message}",
            20.seconds,
            context = context
        )
    }
}

private fun showGalleryImage(index: Int, context: Context) {
    try {
        if (galleryImages.isEmpty() || index < 0 || index >= galleryImages.size) {
            showSimpleNotificationExtern(
                "❌ Fehler",
                "Ungültiger Image-Index",
                20.seconds,
                context = context
            )
            return
        }

        val galleryImage = galleryImages[index]
        val imageUri = galleryImage.uri

        val lastModifiedText = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            .format(Date(galleryImage.lastModified))

        val createdAtText = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            .format(Date(galleryImage.createdAt))

        val originalBitmap = try {
            ImageDecoder.decodeBitmap(
                ImageDecoder.createSource(context.contentResolver, imageUri)
            )
        } catch (e: Exception) {
            Log.e("QuietHoursService", "Error decoding image at index $index", e)
            showSimpleNotificationExtern(
                "❌ Fehler",
                "Bild konnte nicht geladen werden",
                20.seconds,
                context = context
            )
            return
        }

        val prevIntent = Intent(context, QuietHoursNotificationService::class.java).apply {
            action = ACTION_PREV_GALLERY_IMAGE
        }
        val prevPendingIntent = PendingIntent.getService(
            context, 71, prevIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = Intent(context, QuietHoursNotificationService::class.java).apply {
            action = ACTION_NEXT_GALLERY_IMAGE
        }
        val nextPendingIntent = PendingIntent.getService(
            context, 72, nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val confirmDeleteIntent =
            Intent(context, QuietHoursNotificationService::class.java).apply {
                action = ACTION_CONFIRM_DELETE_IMAGE
                putExtra(EXTRA_IMAGE_INDEX, index)
            }
        val confirmDeletePendingIntent = PendingIntent.getService(
            context, 73, confirmDeleteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(imageUri, "image/*")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        val openPendingIntent = PendingIntent.getActivity(
            context, 74, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, GALLERY_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setContentTitle("📷 ${galleryImage.displayName ?: "Galerie"}")
            .setContentText("Tippen zum Löschen • Wischen für Details")
            .setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(originalBitmap)
                    .bigLargeIcon(null as Bitmap?)
                    .showBigPictureWhenCollapsed(true)
                    .setSummaryText("Bild ${index + 1}/${galleryImages.size} • $lastModifiedText • $createdAtText")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setAutoCancel(false)
            .setGroup("group_services")
            .setGroupSummary(false)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_media_previous, "◀", prevPendingIntent)
            .addAction(android.R.drawable.ic_menu_view, "Löschen", confirmDeletePendingIntent)
            .addAction(android.R.drawable.ic_media_next, "▶", nextPendingIntent)
            .build()

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            notificationManager.notify(70000, notification)
        }
    } catch (e: Exception) {
        Log.e("QuietHoursService", "Error showing gallery image", e)
        showSimpleNotificationExtern(
            "❌ Fehler",
            "Galerie konnte nicht angezeigt werden: ${e.message}",
            20.seconds,
            context = context
        )
    }
}

fun showNextGalleryImage(context: Context) {
    if (galleryImages.isEmpty()) {
        showSimpleNotificationExtern(
            "❌ Galerie leer",
            "Keine Bilder zum Anzeigen",
            20.seconds,
            context = context
        )
        return
    }

    currentGalleryIndex = (currentGalleryIndex + 1) % galleryImages.size
    showGalleryImage(currentGalleryIndex, context)
}

fun showPreviousGalleryImage(context: Context) {
    if (galleryImages.isEmpty()) {
        showSimpleNotificationExtern(
            "❌ Galerie leer",
            "Keine Bilder zum Anzeigen",
            20.seconds,
            context = context
        )
        return
    }

    currentGalleryIndex = if (currentGalleryIndex - 1 < 0) {
        galleryImages.size - 1
    } else {
        currentGalleryIndex - 1
    }
    showGalleryImage(currentGalleryIndex, context)
}