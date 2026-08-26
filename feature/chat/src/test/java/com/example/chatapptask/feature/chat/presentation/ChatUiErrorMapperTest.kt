package com.example.chatapptask.feature.chat.presentation

import com.example.chatapptask.feature.chat.R
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ChatUiErrorMapperTest {
    @Test
    fun unknownHost_mapsToOfflineResource() {
        val raw =
            "HTTP request to https://xyz.supabase.co/rest/v1/messages failed with message: " +
                "Unable to resolve host xyz.supabase.co"
        val resId = ChatUiErrorMapper.messageResId(
            UnknownHostException(raw),
            ChatErrorContext.SYNC,
        )

        assertEquals(R.string.chat_error_offline, resId)
        assertNotEquals(raw, resId.toString())
    }

    @Test
    fun wrappedUnableToResolveHostMessage_mapsToOfflineResource() {
        val wrapped = IllegalStateException(
            "HTTP request to https://example.supabase.co/rest/v1/messages " +
                "failed with message: Unable to resolve host example.supabase.co",
        )

        assertEquals(
            R.string.chat_error_offline,
            ChatUiErrorMapper.messageResId(wrapped, ChatErrorContext.SYNC),
        )
    }

    @Test
    fun socketTimeout_mapsToTimeoutResource() {
        assertEquals(
            R.string.chat_error_timeout,
            ChatUiErrorMapper.messageResId(
                SocketTimeoutException("timeout"),
                ChatErrorContext.SYNC,
            ),
        )
    }

    @Test
    fun namedTimeoutException_mapsToTimeoutResource() {
        class HttpRequestTimeoutException(message: String) : RuntimeException(message)

        assertEquals(
            R.string.chat_error_timeout,
            ChatUiErrorMapper.messageResId(
                HttpRequestTimeoutException("Request timeout: 30000 ms"),
                ChatErrorContext.SEND,
            ),
        )
    }

    @Test
    fun syncContextGenericFailure_mapsToSyncResource_notRawMessage() {
        val raw = "HTTP 500 from https://xyz.supabase.co/rest/v1/messages body={secret}"
        val resId = ChatUiErrorMapper.messageResId(
            IllegalStateException(raw),
            ChatErrorContext.SYNC,
        )

        assertEquals(R.string.chat_error_sync, resId)
    }

    @Test
    fun unexpectedFailure_mapsToUnexpectedResource_notRawMessage() {
        val raw = "supabase.PostgrestException: JWT keys leaked at https://evil.example"
        val resId = ChatUiErrorMapper.messageResId(
            RuntimeException(raw),
            ChatErrorContext.GENERIC,
        )

        assertEquals(R.string.chat_error_unexpected, resId)
    }

    @Test
    fun causeChainOffline_isDetectedThroughWrapper() {
        val root = UnknownHostException("Unable to resolve host api.example")
        val wrapped = RuntimeException("HTTP request failed", root)

        assertEquals(
            R.string.chat_error_offline,
            ChatUiErrorMapper.messageResId(wrapped, ChatErrorContext.REALTIME),
        )
    }
}
