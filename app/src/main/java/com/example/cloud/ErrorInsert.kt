package com.example.cloud

import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.Serializable

@Serializable
data class ERRORINSERTDATA(
    val service_name: String,
    val error_message: String,
    val created_at: String,
    val severity: String
)

suspend fun ERRORINSERT(data: ERRORINSERTDATA) {
    SupabaseConfig.client.from("error_reports").insert(data)
}