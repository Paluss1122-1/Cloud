package com.cloud

import android.util.Log
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

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
        SupabaseConfigALT.client.from("error_reports").insert(data)
        return 1
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e("ERRORINSERT", "❌ FAILURE - Error insertion failed")
        Log.e("ERRORINSERT", "🔴 ${e::class.simpleName}: ${e.message}")
        Log.e("ERRORINSERT", "📝 Failed data: $data")
        Log.e("ERRORINSERT", "Stack trace:", e)
    }
    return 0
}