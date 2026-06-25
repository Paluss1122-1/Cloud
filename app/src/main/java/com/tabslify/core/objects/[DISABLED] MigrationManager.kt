package com.tabslify.core.objects

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object MigrationManager {

    fun exportPrefs(context: Context): Uri {
        val sharedFilesDir = File(context.filesDir, "shared_files")
        val exportFile = File(context.getExternalFilesDir(null), "migration_export.zip")
        
        ZipOutputStream(BufferedOutputStream(FileOutputStream(exportFile), 64 * 1024)).use { zos ->
            if (sharedFilesDir.exists() && sharedFilesDir.isDirectory) {
                sharedFilesDir.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        val entry = ZipEntry(file.name)
                        zos.putNextEntry(entry)
                        BufferedInputStream(file.inputStream(), 64 * 1024).use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }
        }

        return FileProvider.getUriForFile(context, "${context.packageName}.provider", exportFile)
    }

    fun importPrefs(context: Context, uri: Uri) {
        val sharedFilesDir = File(context.filesDir, "shared_files")
        if (!sharedFilesDir.exists()) {
            sharedFilesDir.mkdirs()
        }
        
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            ZipInputStream(BufferedInputStream(inputStream, 64 * 1024)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val outFile = File(sharedFilesDir, entry.name)
                        BufferedOutputStream(FileOutputStream(outFile), 64 * 1024).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
    }
}