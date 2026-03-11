package com.cloud.privatecloudapp

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import java.io.File

fun getVideoFirstFrame(file: File): Bitmap? {
    return try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(file.absolutePath)
        val bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        retriever.release()
        bitmap
    } catch (_: Exception) {
        null
    }
}