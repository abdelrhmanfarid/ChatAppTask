package com.example.chatapptask.data.chat.remote

import com.example.chatapptask.core.domain.ProfileImagePublicUrlFactory
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import javax.inject.Inject

class SupabaseProfileImagePublicUrlFactory @Inject constructor(
    private val supabaseClient: SupabaseClient,
) : ProfileImagePublicUrlFactory {
    override fun publicUrlFor(profileImagePath: String?): String? {
        val path = normalizedProfileImagePath(profileImagePath) ?: return null
        return runCatching {
            supabaseClient.storage[PROFILE_IMAGES_BUCKET].publicUrl(path)
        }.getOrNull()?.takeIf(String::isNotBlank)
    }
}

internal const val PROFILE_IMAGES_BUCKET = "profile-images"

internal fun normalizedProfileImagePath(profileImagePath: String?): String? {
    if (profileImagePath == null) return null
    val path = profileImagePath.trim().removePrefix("/")
    if (path.isEmpty()) return null
    if (path.startsWith("http://") || path.startsWith("https://")) return null
    return path
}
