package com.example.chatapptask.feature.chat.presentation

import android.content.ContentResolver
import android.net.Uri
import com.example.chatapptask.core.domain.model.MediaType

/**
 * Maps Photo Picker URIs to [ComposerAttachment] using ContentResolver MIME metadata.
 * Unsupported types are skipped (caller may surface a user-visible error).
 */
fun resolveComposerAttachments(
    uris: List<Uri>,
    contentResolver: ContentResolver,
): ResolvedComposerAttachments {
    val attachments = ArrayList<ComposerAttachment>(uris.size)
    var skippedUnsupported = 0
    for (uri in uris) {
        val attachment = resolveComposerAttachment(uri, contentResolver)
        if (attachment == null) {
            skippedUnsupported += 1
        } else {
            attachments += attachment
        }
    }
    return ResolvedComposerAttachments(
        attachments = attachments,
        skippedUnsupportedCount = skippedUnsupported,
    )
}

fun resolveComposerAttachment(
    uri: Uri,
    contentResolver: ContentResolver,
): ComposerAttachment? {
    val mimeType = contentResolver.getType(uri)?.takeIf(String::isNotBlank) ?: return null
    val mediaType = mediaTypeForMime(mimeType) ?: return null
    return ComposerAttachment(
        uri = uri.toString(),
        mediaType = mediaType,
        mimeType = mimeType,
    )
}

fun mediaTypeForMime(mimeType: String): MediaType? {
    val normalized = mimeType.lowercase()
    return when {
        normalized.startsWith("image/") -> MediaType.IMAGE
        normalized.startsWith("video/") -> MediaType.VIDEO
        else -> null
    }
}

data class ResolvedComposerAttachments(
    val attachments: List<ComposerAttachment>,
    val skippedUnsupportedCount: Int,
)
