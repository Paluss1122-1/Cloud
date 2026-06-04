package com.tabslify1.core.objects

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object MigrationManager {

    fun exportPrefs(context: Context): Uri {
        val sharedPrefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
        val exportJson = JSONObject()
        
        if (sharedPrefsDir.exists() && sharedPrefsDir.isDirectory) {
            val files = sharedPrefsDir.listFiles()
            if (files != null) {
                for (file in files) {
                    if (file.name.endsWith(".xml")) {
                        val prefName = file.name.substring(0, file.name.length - 4)
                        val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                        val allEntries = prefs.all
                        
                        val prefObj = JSONObject()
                        for ((key, value) in allEntries) {
                            if (value is Set<*>) {
                                val jsonArray = JSONArray()
                                for (item in value) {
                                    jsonArray.put(item.toString())
                                }
                                // To distinguish from normal arrays, we can just use JSONArray
                                prefObj.put(key, jsonArray)
                            } else if (value is Float) {
                                prefObj.put(key, value.toDouble())
                            } else {
                                prefObj.put(key, value)
                            }
                        }
                        exportJson.put(prefName, prefObj)
                    }
                }
            }
        }
        
        val exportFile = File(context.getExternalFilesDir(null), "migration_export.json")
        exportFile.writeText(exportJson.toString())
        
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", exportFile)
    }

    fun importPrefs(context: Context, uri: Uri) {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return
        val jsonText = inputStream.bufferedReader().use { it.readText() }
        val exportJson = JSONObject(jsonText)
        
        val keys = exportJson.keys()
        while (keys.hasNext()) {
            val prefName = keys.next()
            val prefObj = exportJson.getJSONObject(prefName)
            val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
            val editor = prefs.edit()
            
            val prefKeys = prefObj.keys()
            while (prefKeys.hasNext()) {
                val key = prefKeys.next()
                val value = prefObj.get(key)
                
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Double -> editor.putFloat(key, value.toFloat())
                    is Float -> editor.putFloat(key, value)
                    is String -> editor.putString(key, value)
                    is JSONArray -> {
                        val set = mutableSetOf<String>()
                        for (i in 0 until value.length()) {
                            set.add(value.getString(i))
                        }
                        editor.putStringSet(key, set)
                    }
                }
            }
            editor.apply()
        }
    }
}