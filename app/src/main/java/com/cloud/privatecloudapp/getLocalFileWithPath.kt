package com.cloud.privatecloudapp

import android.os.Environment
import java.io.File

fun getLocalFileWithPath(fileName: String, remoteSize: Long): File? {
    val downloadsDir =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    val downloadFile = File(downloadsDir, fileName)
    if (downloadFile.exists() && downloadFile.length() == remoteSize) return downloadFile

    val dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
    val dcimFile = File(dcimDir, "Cloud/$fileName")
    if (dcimFile.exists() && dcimFile.length() == remoteSize) return dcimFile

    return null
}