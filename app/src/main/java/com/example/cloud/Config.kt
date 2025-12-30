package com.example.cloud

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.ktor.client.engine.android.Android
import io.ktor.client.engine.cio.CIO

object SupabaseConfig {
    const val SUPABASE_URL = "https://oulgglfvobyjmfongnil.supabase.co"
    const val SUPABASE_ANON_KEY = BuildConfig.SUPABASE_ANON_KEY
    const val SUPABASE_BUCKET = "Files"

    val client = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_ANON_KEY
    ) {
        install(Storage)
        install(Postgrest)
        install(Realtime)
    }
}

object TMDBConfig {
    const val ApiKey = "c9b585a04735e5a130f9c6ee642088c0"
}

object AIConfig {
    const val APIKEY = "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"
}