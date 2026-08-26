package com.example.chatapptask.feature.profile.presentation

import android.content.ContentResolver
import android.net.Uri

data class ProfileImagePayload(
    val bytes: ByteArray,
    val mimeType: String,
    val fileExtension: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProfileImagePayload) return false
        return bytes.contentEquals(other.bytes) &&
            mimeType == other.mimeType &&
            fileExtension == other.fileExtension
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + fileExtension.hashCode()
        return result
    }
}

fun interface ProfileImageReader {
    fun read(uriString: String): ProfileImagePayload
}

class ContentResolverProfileImageReader(
    private val openUri: (Uri) -> ProfileImagePayload,
) : ProfileImageReader {
    constructor(contentResolver: ContentResolver) : this({ uri ->
        val mimeType = contentResolver.getType(uri)?.takeIf(String::isNotBlank) ?: DEFAULT_MIME_TYPE
        val bytes = contentResolver.openInputStream(uri)?.use { input -> input.readBytes() }
            ?: error("Unable to read the selected profile image.")
        require(bytes.isNotEmpty()) { "The selected profile image is empty." }
        ProfileImagePayload(
            bytes = bytes,
            mimeType = mimeType,
            fileExtension = extensionForMime(mimeType),
        )
    })

    override fun read(uriString: String): ProfileImagePayload = openUri(Uri.parse(uriString))

    private companion object {
        const val DEFAULT_MIME_TYPE = "image/jpeg"

        fun extensionForMime(mimeType: String): String =
            when (mimeType.lowercase()) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                else -> "jpg"
            }
    }
}
