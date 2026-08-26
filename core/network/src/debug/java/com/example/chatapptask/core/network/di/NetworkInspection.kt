package com.example.chatapptask.core.network.di

import android.util.Log
import io.github.jan.supabase.SupabaseClientBuilder
import io.github.jan.supabase.annotations.SupabaseInternal
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.http.HttpHeaders

private const val NETWORK_LOG_TAG = "SupabaseHttp"

@OptIn(SupabaseInternal::class)
internal fun SupabaseClientBuilder.configureNetworkInspection() {
    httpConfig {
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    Log.d(NETWORK_LOG_TAG, message)
                }
            }
            level = LogLevel.HEADERS
            sanitizeHeader { header -> isSensitiveHeader(header) }
        }
    }
}

private fun isSensitiveHeader(header: String): Boolean =
    header.equals(HttpHeaders.Authorization, ignoreCase = true) ||
        header.equals(HttpHeaders.ProxyAuthorization, ignoreCase = true) ||
        header.equals(HttpHeaders.Cookie, ignoreCase = true) ||
        header.equals(HttpHeaders.SetCookie, ignoreCase = true) ||
        header.equals("apikey", ignoreCase = true) ||
        header.equals("x-api-key", ignoreCase = true) ||
        header.equals("x-supabase-api-key", ignoreCase = true)
