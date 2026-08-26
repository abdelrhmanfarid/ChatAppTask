package com.example.chatapptask.feature.chat.presentation

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.example.chatapptask.core.domain.model.MAX_MEDIA_ITEM_BYTES
import com.example.chatapptask.core.domain.model.MediaType

/**
 * Maps Photo Picker URIs to [ComposerAttachment] using ContentResolver MIME metadata.
 * Unsupported types and oversized items are skipped (caller may surface a user-visible error).
 */
fun resolveComposerAttachments(
    uris: List<Uri>,
    contentResolver: ContentResolver,
): ResolvedComposerAttachments {
    val attachments = ArrayList<ComposerAttachment>(uris.size)
    var skippedUnsupported = 0
    var skippedOversized = 0
    for (uri in uris) {
        val mimeType = contentResolver.getType(uri)?.takeIf(String::isNotBlank)
        val mediaType = mimeType?.let(::mediaTypeForMime)
        if (mimeType == null || mediaType == null) {
            skippedUnsupported += 1
            continue
        }
        val sizeBytes = mediaSizeBytes(contentResolver, uri)
        if (isOversizedMedia(sizeBytes)) {
            skippedOversized += 1
            continue
        }
        attachments += ComposerAttachment(
            uri = uri.toString(),
            mediaType = mediaType,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
        )
    }
    return ResolvedComposerAttachments(
        attachments = attachments,
        skippedUnsupportedCount = skippedUnsupported,
        skippedOversizedCount = skippedOversized,
    )
}

fun resolveComposerAttachment(
    uri: Uri,
    contentResolver: ContentResolver,
): ComposerAttachment? =
    resolveComposerAttachments(listOf(uri), contentResolver).attachments.singleOrNull()

fun mediaTypeForMime(mimeType: String): MediaType? {
    val normalized = mimeType.lowercase()
    return when {
        normalized.startsWith("image/") -> MediaType.IMAGE
        normalized.startsWith("video/") -> MediaType.VIDEO
        else -> null
    }
}

internal fun isOversizedMedia(sizeBytes: Long?): Boolean =
    sizeBytes != null && sizeBytes > MAX_MEDIA_ITEM_BYTES

internal fun resolvedMediaSizeBytes(
    openableColumnSize: Long?,
    assetFileLength: Long?,
): Long? {
    openableColumnSize?.takeIf { size -> size >= 0L }?.let { return it }
    assetFileLength?.takeIf { size -> size >= 0L }?.let { return it }
    return null
}

private fun mediaSizeBytes(
    contentResolver: ContentResolver,
    uri: Uri,
): Long? {
    val openableSize = runCatching { queryOpenableSize(contentResolver, uri) }.getOrNull()
    val assetLength = runCatching { assetFileDescriptorLength(contentResolver, uri) }.getOrNull()
    return resolvedMediaSizeBytes(openableSize, assetLength)
}

private fun queryOpenableSize(
    contentResolver: ContentResolver,
    uri: Uri,
): Long? =
    contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val index = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (index < 0 || cursor.isNull(index)) {
            null
        } else {
            cursor.getLong(index)
        }
    }

private fun assetFileDescriptorLength(
    contentResolver: ContentResolver,
    uri: Uri,
): Long? =
    contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
        descriptor.length
    }

data class ResolvedComposerAttachments(
    val attachments: List<ComposerAttachment>,
    val skippedUnsupportedCount: Int,
    val skippedOversizedCount: Int = 0,
)
