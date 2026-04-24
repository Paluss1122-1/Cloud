package com.cloud.core.functions

import com.cloud.core.objects.SupabaseConfigALT
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.Serializable

@Serializable
data class ERRORINSERTDATA(
    val serviceName: String,
    val errorMessage: String,
    val createdAt: String,
    val severity: String,
    val id: Int? = null
)

suspend fun errorInsert(data: ERRORINSERTDATA): Int {
    try {
        SupabaseConfigALT.client.from("error_reports").insert(data)
        return 1
    } catch (_: Exception) {
    }
    return 0
}