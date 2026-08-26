package com.example.chatapptask.data.chat.remote

import android.util.Log
import com.example.chatapptask.core.network.SupabaseConfig
import com.example.chatapptask.core.network.di.RegisterPushHttpClient
import com.example.chatapptask.core.network.dto.RegisterPushRequestDto
import io.ktor.client.HttpClient
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import kotlinx.serialization.json.Json

/**
 * Invokes `register-push` via a plain Ktor POST.
 *
 * supabase-kt Functions always attaches Authorization through authenticatedSupabaseApi,
 * which causes HTTP 401 with new-format publishable keys. This path sends only apikey.
 */
class SupabasePushRegistrationRemoteDataSource @Inject constructor(
    @RegisterPushHttpClient private val httpClient: HttpClient,
) : PushRegistrationRemoteDataSource {
    override suspend fun registerInstallation(
        ownerId: UUID,
        installationId: String,
    ) {
        Log.d(LOG_TAG, "register-push attempt started")
        try {
            val response: HttpResponse = httpClient.post(
                registerPushUrl(SupabaseConfig.url),
            ) {
                headers {
                    appendAll(registerPushRequestHeaders(SupabaseConfig.publishableKey))
                }
                contentType(ContentType.Application.Json)
                setBody(
                    JSON.encodeToString(
                        RegisterPushRequestDto.serializer(),
                        RegisterPushRequestDto(
                            ownerId = ownerId.toString(),
                            installationId = installationId,
                        ),
                    ),
                )
            }
            if (!response.status.isSuccess()) {
                Log.w(LOG_TAG, "register-push failed with HTTP ${response.status.value}")
                throw IOException(
                    "register-push failed with HTTP ${response.status.value}",
                )
            }
            Log.d(LOG_TAG, "register-push succeeded")
        } catch (error: IOException) {
            throw error
        } catch (error: Exception) {
            Log.w(LOG_TAG, "register-push failed: ${error.javaClass.simpleName}")
            throw error
        }
    }

    private companion object {
        const val LOG_TAG = "PushRegistration"
        val JSON = Json { encodeDefaults = true }
    }
}

internal const val REGISTER_PUSH_APIKEY_HEADER = "apikey"
internal const val REGISTER_PUSH_FUNCTION_PATH = "/functions/v1/register-push"

/**
 * Builds `<SUPABASE_URL>/functions/v1/register-push`.
 */
internal fun registerPushUrl(supabaseUrl: String): String =
    supabaseUrl.trimEnd('/') + REGISTER_PUSH_FUNCTION_PATH

/**
 * Outgoing headers for `register-push`: apikey + JSON only. No Authorization.
 */
internal fun registerPushRequestHeaders(publishableApiKey: String): io.ktor.http.Headers =
    io.ktor.http.Headers.build {
        append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        append(REGISTER_PUSH_APIKEY_HEADER, publishableApiKey)
    }

/**
 * Encodes the register-push JSON body with stable camelCase field names.
 */
internal fun registerPushRequestBodyJson(
    ownerId: String,
    installationId: String,
): String =
    Json { encodeDefaults = true }.encodeToString(
        RegisterPushRequestDto.serializer(),
        RegisterPushRequestDto(
            ownerId = ownerId,
            installationId = installationId,
        ),
    )
