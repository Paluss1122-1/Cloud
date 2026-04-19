package com.cloud.core.functions

import com.cloud.core.objects.SupabaseConfigALT
import io.github.jan.supabase.postgrest.from
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
    } catch (_: Exception) {
    }
    return 0
}