package com.example.chatapptask.core.domain

/**
 * Turns a chat-media Storage-relative path into a displayable HTTP URL.
 *
 * [storagePath] is bucket-relative, e.g. `{messageId}/{mediaId}.jpg`.
 * Implementations live in the data layer so feature UI never depends on Supabase.
 */
fun interface ChatMediaPublicUrlFactory {
    fun publicUrlFor(storagePath: String): String?
}
