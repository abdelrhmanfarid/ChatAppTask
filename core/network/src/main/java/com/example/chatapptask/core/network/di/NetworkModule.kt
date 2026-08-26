package com.example.chatapptask.core.network.di

import com.example.chatapptask.core.network.SupabaseConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient =
        createSupabaseClient(
            supabaseUrl = SupabaseConfig.url,
            supabaseKey = SupabaseConfig.anonKey,
        ) {
            configureNetworkInspection()
            install(Postgrest)
            install(Realtime)
            install(Storage)
            install(Functions)
        }

    /**
     * Plain Ktor client for `register-push` only.
     * Does not attach Supabase Auth / Authorization defaults.
     */
    @Provides
    @Singleton
    @RegisterPushHttpClient
    fun provideRegisterPushHttpClient(): HttpClient =
        HttpClient(OkHttp) {
            expectSuccess = false
        }
}
