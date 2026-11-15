package com.itm.servicedesk.core.storage

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.js

@OptIn(ExperimentalWasmJsInterop::class)
private fun sessionStorageGetItem(key: String): String? = js("window.sessionStorage.getItem(key)")

@OptIn(ExperimentalWasmJsInterop::class)
private fun sessionStorageSetItem(
    key: String,
    value: String,
): Unit = js("window.sessionStorage.setItem(key, value)")

@OptIn(ExperimentalWasmJsInterop::class)
private fun sessionStorageRemoveItem(key: String): Unit = js("window.sessionStorage.removeItem(key)")

class WebSessionStorage : SessionStorage {
    private var memoryToken: String? = null

    override suspend fun saveToken(token: String) {
        memoryToken = token
        sessionStorageSetItem(KEY_JWT_TOKEN, token)
    }

    override suspend fun getToken(): String? {
        memoryToken?.let { return it }
        val stored = sessionStorageGetItem(KEY_JWT_TOKEN)
        memoryToken = stored
        return stored
    }

    override suspend fun clearToken() {
        memoryToken = null
        sessionStorageRemoveItem(KEY_JWT_TOKEN)
    }

    private companion object {
        const val KEY_JWT_TOKEN = "itm_sd_jwt_token"
    }
}
