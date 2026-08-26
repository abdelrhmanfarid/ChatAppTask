package com.example.chatapptask.data.chat.remote

import io.ktor.http.HttpHeaders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegisterPushRequestHeadersTest {
    @Test
    fun registerPushRequestHeaders_usesPublishableKeyNotLegacyAnon_withoutAuthorization() {
        val legacyAnonKey = "legacy-anon-test-key"
        val publishableKey = "sb_publishable_test_key"

        val headers = registerPushRequestHeaders(publishableKey)

        assertEquals("application/json", headers[HttpHeaders.ContentType])
        assertEquals(publishableKey, headers[REGISTER_PUSH_APIKEY_HEADER])
        assertNotEquals(legacyAnonKey, headers[REGISTER_PUSH_APIKEY_HEADER])
        assertFalse(
            headers.getAll(REGISTER_PUSH_APIKEY_HEADER).orEmpty().contains(legacyAnonKey),
        )
        assertTrue(headers.getAll(HttpHeaders.Authorization).isNullOrEmpty())
        assertFalse(headers.names().any { it.equals(HttpHeaders.Authorization, ignoreCase = true) })
    }

    @Test
    fun registerPushUrl_appendsFunctionsPath() {
        assertEquals(
            "https://example.supabase.co/functions/v1/register-push",
            registerPushUrl("https://example.supabase.co"),
        )
        assertEquals(
            "https://example.supabase.co/functions/v1/register-push",
            registerPushUrl("https://example.supabase.co/"),
        )
    }

    @Test
    fun registerPushRequestBodyJson_keepsCamelCaseFieldNames() {
        val json = registerPushRequestBodyJson(
            ownerId = "11111111-1111-1111-1111-111111111111",
            installationId = "fid-test-value",
        )

        assertTrue(json.contains("\"ownerId\""))
        assertTrue(json.contains("\"installationId\""))
        assertFalse(json.contains("owner_id"))
        assertFalse(json.contains("installation_id"))
        assertTrue(json.contains("11111111-1111-1111-1111-111111111111"))
        assertTrue(json.contains("fid-test-value"))
    }
}
