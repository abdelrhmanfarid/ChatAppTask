package com.example.chatapptask.core.domain

/**
 * Turns a profile-image Storage-relative path into a displayable HTTP URL.
 *
 * [profileImagePath] is bucket-relative, e.g. `{userId}/avatar.jpg`.
 * Implementations live in the data layer so feature UI never depends on Supabase.
 */
fun interface ProfileImagePublicUrlFactory {
    fun publicUrlFor(profileImagePath: String?): String?
}
