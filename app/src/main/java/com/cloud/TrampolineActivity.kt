package com.example.cloud

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import java.time.LocalTime

class TrampolineActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            val hour = LocalTime.now().hour
            if (hour >= 21) {
                val pm: PackageManager = packageManager
                val launchIntent: Intent? = pm.getLaunchIntentForPackage("com.whatsapp")
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(launchIntent)
                } else {
                    Toast.makeText(this, "WhatsApp nicht installiert", Toast.LENGTH_SHORT).show()
                    Log.w("TrampolineActivity", "Launch-Intent für com.whatsapp ist null")
                }
            } else {
                Log.d("TrampolineActivity", "Vor 21 Uhr – keine Aktion")
            }
        } catch (e: Exception) {
            Log.e("TrampolineActivity", "Fehler beim Starten von WhatsApp", e)
        } finally {
            finish()
        }
    }
}
