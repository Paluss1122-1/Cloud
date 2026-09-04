package com.tabslify.core.objects

import android.content.Context
import com.tabslify.quicksettingsfunctions.BatteryDataRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

object CloudBackup {
    fun backupNow(context: Context, scope: CoroutineScope) {
        scope.launch {
            runCatching {
                PrefsBackup.backupNow(context, force = false)
                BatteryDataRepository.trySync(context)
            }
        }
    }
}
