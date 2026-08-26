package com.example.chatapptask.data.chat.remote

import com.example.chatapptask.core.domain.ChatMediaPublicUrlFactory
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import javax.inject.Inject

class SupabaseChatMediaPublicUrlFactory @Inject constructor(
    private val supabaseClient: SupabaseClient,
) : ChatMediaPublicUrlFactory {
    override fun publicUrlFor(storagePath: String): String? {
        val path = normalizedChatMediaStoragePath(storagePath) ?: return null
        return runCatching {
            supabaseClient.storage[CHAT_MEDIA_BUCKET].publicUrl(path)
        }.getOrNull()?.takeIf(String::isNotBlank)
    }

    private companion object {
        const val CHAT_MEDIA_BUCKET = "chat-media"
    }
}

internal fun normalizedChatMediaStoragePath(storagePath: String): String? {
    val path = storagePath.trim().removePrefix("/")
    if (path.isEmpty()) return null
    if (path.startsWith("http://") || path.startsWith("https://")) return null
    return path
}
