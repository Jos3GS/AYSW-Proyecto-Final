package com.itm.servicedesk.core.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AndroidSessionStorage(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val sharedPreferencesProvider: (() -> SharedPreferences)? = null,
) : SessionStorage {
    private val mutex = Mutex()
    private var initialized = false
    private var cachedToken: String? = null
    private var encryptedPrefs: SharedPreferences? = null

    @Suppress("DEPRECATION")
    private fun getOrCreatePrefs(): SharedPreferences {
        encryptedPrefs?.let { return it }
        sharedPreferencesProvider?.let { provider ->
            return provider().also { encryptedPrefs = it }
        }
        val masterKey =
            MasterKey
                .Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
        return EncryptedSharedPreferences
            .create(
                context,
                PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            ).also { encryptedPrefs = it }
    }

    override suspend fun saveToken(token: String) {
        mutex.withLock {
            withContext(ioDispatcher) {
                getOrCreatePrefs()
                    .edit()
                    .putString(KEY_JWT_TOKEN, token)
                    .commit()
                cachedToken = token
                initialized = true
            }
        }
    }

    override suspend fun getToken(): String? =
        mutex.withLock {
            if (initialized) {
                return@withLock cachedToken
            }
            withContext(ioDispatcher) {
                val loaded = getOrCreatePrefs().getString(KEY_JWT_TOKEN, null)
                cachedToken = loaded
                initialized = true
                loaded
            }
        }

    override suspend fun clearToken() {
        mutex.withLock {
            withContext(ioDispatcher) {
                getOrCreatePrefs()
                    .edit()
                    .remove(KEY_JWT_TOKEN)
                    .commit()
                cachedToken = null
                initialized = true
            }
        }
    }

    private companion object {
        const val PREFS_FILE_NAME = "itm_service_desk_secure_session"
        const val KEY_JWT_TOKEN = "jwt_token"
    }
}
