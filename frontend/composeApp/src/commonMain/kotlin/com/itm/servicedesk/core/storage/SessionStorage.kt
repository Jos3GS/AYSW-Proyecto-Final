package com.itm.servicedesk.core.storage

interface SessionStorage {
    suspend fun saveToken(token: String)

    suspend fun getToken(): String?

    suspend fun clearToken()
}
