package com.cloud.authenticator

import android.content.ClipData
import android.content.ClipboardManager
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

class FavoritTile3Service : TileService() {

    override fun onStartListening() {
        val prefs = getSharedPreferences("favorites", MODE_PRIVATE)
        val name = prefs.getString("fav3_name", null)

        qsTile?.apply {
            label = name ?: "Favorit 3"
            state = if (name != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        val prefs = getSharedPreferences("favorites", MODE_PRIVATE)
        val secret = prefs.getString("fav3_secret", null)
        val name = prefs.getString("fav3_name", null)

        if (secret != null && name != null) {
            val code = TotpGenerator.generateTOTP(secret, System.currentTimeMillis())
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("TOTP Code", code))
            Toast.makeText(this, "Code für $name kopiert!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Kein Favorit 3 festgelegt", Toast.LENGTH_SHORT).show()
            qsTile?.apply {
                label = "Favorit 3"
                state = Tile.STATE_INACTIVE
                updateTile()
            }
        }
    }
}
