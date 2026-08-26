package com.example.chatapptask.core.network.di

import javax.inject.Qualifier

/** Ktor client for Edge Function calls that must not send an Authorization header. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RegisterPushHttpClient
