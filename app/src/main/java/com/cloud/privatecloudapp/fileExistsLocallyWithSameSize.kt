package com.cloud.privatecloudapp

import android.os.Environment
import java.io.File

fun fileExistsLocallyWithSameSize(fileName: String, remoteSize: Long): Boolean {
    val downloadsDir =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    val downloadFile = File(downloadsDir, fileName)
    if (downloadFile.exists() && downloadFile.length() == remoteSize) return true

    val dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
    val dcimFile = File(dcimDir, "Cloud/$fileName")
    return dcimFile.exists() && dcimFile.length() == remoteSize
}