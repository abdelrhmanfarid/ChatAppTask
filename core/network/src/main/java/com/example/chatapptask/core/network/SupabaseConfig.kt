package com.example.chatapptask.core.network

object SupabaseConfig {
    val url: String by lazy {
        requireConfiguredValue(
            name = "SUPABASE_URL",
            value = BuildConfig.SUPABASE_URL,
        )
    }

    /**
     * Legacy JWT-style anon key (`eyJ...`) for the normal Supabase Kotlin client
     * (PostgREST, Realtime, Storage).
     */
    val anonKey: String by lazy {
        requireConfiguredValue(
            name = "SUPABASE_ANON_KEY",
            value = BuildConfig.SUPABASE_ANON_KEY,
        )
    }

    /**
     * New-format client-safe publishable key (`sb_publishable_...`) for Edge Function
     * calls that authenticate via the `apikey` header only (e.g. `register-push`).
     */
    val publishableKey: String by lazy {
        requireConfiguredValue(
            name = "SUPABASE_PUBLISHABLE_KEY",
            value = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
        )
    }

    private fun requireConfiguredValue(
        name: String,
        value: String,
    ): String =
        value.trim().also { configuredValue ->
            check(configuredValue.isNotEmpty()) {
                "$name is missing. Add it to local.properties or the environment."
            }
        }
}
