package com.tabslify.core.functions

import com.tabslify.core.objects.Config
import io.github.jan.supabase.functions.functions

suspend fun errorInsert(serviceName: String, errorMessage: String, createdAt: String, severity: String): Int {
    if (!Config.realDevice) return 0
    return try {
        val response = Config.client.functions.invoke(
            function = "report_error",
            body = mapOf(
                "serviceName" to serviceName,
                "errorMessage" to errorMessage,
                "createdAt" to createdAt,
                "severity" to severity
            )
        )
        response.status.value
    } catch (e: Exception) {
        println("Error invoking Edge Function: ${e.message}")
        500
    }
}
