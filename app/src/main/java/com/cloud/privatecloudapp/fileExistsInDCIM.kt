package com.cloud.privatecloudapp

import android.os.Environment
import java.io.File

fun fileExistsInDCIM(fileName: String): File? {
    val dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
    val appFolder = File(dcimDir, "Cloud")
    val file = File(appFolder, fileName)
    return if (file.exists()) file else null
}