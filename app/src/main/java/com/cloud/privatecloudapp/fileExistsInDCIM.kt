package com.example.cloud.privatecloudapp

import android.os.Environment
import java.io.File

fun fileExistsInDCIM(fileName: String): File? {
    val dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
    val appFolder = File(dcimDir, "Cloud") // z.B. "CloudApp"
    val file = File(appFolder, fileName)
    return if (file.exists()) file else null
}