package com.example.chatapptask.core.domain.model

/**
 * Per-attachment upload cap matching the Supabase Free plan's 50 MB file-size limit.
 *
 * Uses 50 MiB (`50 * 1024 * 1024`) rather than 50,000,000 so the app limit matches
 * the typical binary storage quota. Files equal to this value are accepted (`<=`).
 * Exact-boundary HTTP overhead that still returns Storage 400 is treated as a
 * permanent upload failure instead of shrinking this cap further.
 */
const val MAX_MEDIA_ITEM_BYTES = 50L * 1024 * 1024
