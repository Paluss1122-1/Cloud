package com.tabslify.quiethoursnotificationhelper

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.tabslify.core.objects.MigrationManager

/*class ImportActivity : Activity() {
    companion object {
        const val PICK_JSON_FILE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "application/zip"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(intent, PICK_JSON_FILE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_JSON_FILE && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                try {
                    MigrationManager.importPrefs(this, uri)
                    Toast.makeText(this, "Prefs erfolgreich importiert", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Fehler beim Import: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        finish()
    }
}
*/