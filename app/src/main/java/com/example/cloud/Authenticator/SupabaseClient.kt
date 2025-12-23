package com.example.cloud.Authenticator

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

val supabase = createSupabaseClient(
    supabaseUrl = "https://oulgglfvobyjmfongnil.supabase.co",
    supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im91bGdnbGZ2b2J5am1mb25nbmlsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjA2MzQ5NzgsImV4cCI6MjA3NjIxMDk3OH0.5Y-jhzuIJZ0xOQ4WPn9mjnCo2dcxO0AOx1Hkaur5Sc4"
) {
    install(Postgrest)
}