package com.cloud.core.functions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager

fun canNotify(context: Context): Boolean {
    return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}