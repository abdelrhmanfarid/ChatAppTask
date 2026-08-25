package com.example.chatapptask.data.chat.local

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

interface OutgoingMediaStore {
    /**
     * Copies [sourceUri] into durable app-owned storage and returns a file URI for that copy.
     */
    fun copyIncoming(
        sourceUri: String,
        messageId: UUID,
        mediaId: UUID,
        mimeType: String,
    ): String

    fun deleteCopiedMedia(messageId: UUID)
}

class FileOutgoingMediaStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : OutgoingMediaStore {
    override fun copyIncoming(
        sourceUri: String,
        messageId: UUID,
        mediaId: UUID,
        mimeType: String,
    ): String {
        val extension = fileExtensionFor(mimeType, sourceUri)
        val messageDirectory = messageDirectory(messageId)
        if (!messageDirectory.exists() && !messageDirectory.mkdirs()) {
            error("Unable to create outgoing media storage.")
        }
        val destination = File(messageDirectory, "$mediaId.$extension")
        val uri = Uri.parse(sourceUri)
        context.contentResolver.openInputStream(uri)?.use { input ->
            destination.outputStream().use { output ->
                val copiedBytes = input.copyTo(output)
                if (copiedBytes == 0L) {
                    destination.delete()
                    error("Selected media is empty.")
                }
            }
        } ?: error("Unable to read selected media.")
        return destination.toURI().toString()
    }

    override fun deleteCopiedMedia(messageId: UUID) {
        messageDirectory(messageId).deleteRecursively()
    }

    private fun messageDirectory(messageId: UUID): File =
        File(File(context.filesDir, OUTGOING_MEDIA_DIRECTORY), messageId.toString())

    private companion object {
        const val OUTGOING_MEDIA_DIRECTORY = "outgoing-media"
    }
}

internal fun fileExtensionFor(mimeType: String, sourceUri: String): String {
    mimeTypeExtension(mimeType)?.let { return it }
    uriExtension(sourceUri)?.let { return it }
    return "bin"
}

private fun mimeTypeExtension(mimeType: String): String? =
    when (mimeType.substringBefore(';').trim().lowercase()) {
        "image/jpeg", "image/jpg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        "video/mp4" -> "mp4"
        "video/quicktime" -> "mov"
        "video/webm" -> "webm"
        else -> null
    }

private fun uriExtension(sourceUri: String): String? {
    val fileName = sourceUri.substringAfterLast('/').substringBefore('?')
    val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")
        .trim()
        .lowercase()
    return extension.takeIf { value -> value.matches(FILE_EXTENSION_PATTERN) }
}

private val FILE_EXTENSION_PATTERN = Regex("[a-z0-9]+")
