package com.example.chatapptask.core.network

internal object SupabaseConfig {
    val url: String by lazy {
        requireConfiguredValue(
            name = "SUPABASE_URL",
            value = BuildConfig.SUPABASE_URL,
        )
    }

    val anonKey: String by lazy {
        requireConfiguredValue(
            name = "SUPABASE_ANON_KEY",
            value = BuildConfig.SUPABASE_ANON_KEY,
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
