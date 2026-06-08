package com.tabslify.core.functions

import com.tabslify.core.activities.Tabslify.Companion.serviceScope
import com.tabslify.core.objects.Config
import io.github.jan.supabase.functions.functions
import kotlinx.coroutines.launch

fun errorInsert(serviceName: String, errorMessage: String, createdAt: String, severity: String) {
    if (!Config.realDevice) return
    serviceScope.launch {
        try {
            Config.client.functions.invoke(
                function = "report_error",
                body = mapOf(
                    "serviceName" to serviceName,
                    "errorMessage" to errorMessage,
                    "createdAt" to createdAt,
                    "severity" to severity
                )
            )
        } catch (e: Exception) {
            println("Error invoking Edge Function: ${e.message}")
        }
    }
}