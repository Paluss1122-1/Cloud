package com.example.cloud

import android.util.Log
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.Serializable
import kotlinx.coroutines.CancellationException

@Serializable
data class ERRORINSERTDATA(
    val service_name: String,
    val error_message: String,
    val created_at: String,
    val severity: String,
    val id: Int? = null
)

suspend fun ERRORINSERT(data: ERRORINSERTDATA): Int {
    try {
        SupabaseConfig.client.from("error_reports")
            .insert(data)
            .decodeSingle<ErrorReportResponse>()

        return 1
    } catch (e: CancellationException) {
        Log.w("ERRORINSERT", "⚠️ Coroutine was cancelled")
        throw e
    } catch (e: Exception) {
        Log.e("ERRORINSERT", "❌ FAILURE - Error insertion failed")
        Log.e("ERRORINSERT", "🔴 Exception type: ${e::class.simpleName}")
        Log.e("ERRORINSERT", "🔴 Error message: ${e.message}")
        Log.e("ERRORINSERT", "🔴 Error code: ${if (e is io.github.jan.supabase.postgrest.exception.PostgrestRestException) e.code else "N/A"}")
        Log.e("ERRORINSERT", "📝 Failed data: $data")
        Log.e("ERRORINSERT", "Stack trace:", e)
    }
    return 0
}

@Serializable
data class ErrorReportResponse(
    val id: Int,
    val service_name: String,
    val error_message: String,
    val created_at: String,
    val severity: String
)