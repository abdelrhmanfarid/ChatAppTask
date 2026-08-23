package com.example.chatapptask.core.common.identity

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.UUID

private const val USER_IDENTITY_DATA_STORE_NAME = "user_identity"
private val USER_ID_KEY = stringPreferencesKey("user_id")

private val Context.userIdentityDataStore by preferencesDataStore(
    name = USER_IDENTITY_DATA_STORE_NAME,
)

class DataStoreUserIdentityStore(
    context: Context,
) : UserIdentityStore {
    private val dataStore = context.applicationContext.userIdentityDataStore

    override suspend fun getOrCreateUserId(): UUID {
        lateinit var userId: UUID

        // DataStore serializes edits, so concurrent first calls observe one committed identity.
        dataStore.edit { preferences ->
            val persistedUserId = preferences[USER_ID_KEY]
            userId = persistedUserId?.toUuidOrNull() ?: UUID.randomUUID().also { generatedId ->
                // A missing or malformed identity is replaced deliberately with a valid UUID.
                preferences[USER_ID_KEY] = generatedId.toString()
            }
        }

        return userId
    }
}

private fun String.toUuidOrNull(): UUID? =
    try {
        UUID.fromString(this).takeIf { parsedId ->
            parsedId.toString().equals(this, ignoreCase = true)
        }
    } catch (_: IllegalArgumentException) {
        null
    }
