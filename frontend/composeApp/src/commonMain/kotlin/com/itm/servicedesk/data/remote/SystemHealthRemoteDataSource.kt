package com.itm.servicedesk.data.remote

import com.itm.servicedesk.data.dto.HealthResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

class ApiHttpException(
    val statusCode: Int,
    val responseBody: String?,
) : Exception("HTTP $statusCode")

interface SystemHealthRemoteDataSource {
    suspend fun fetchHealth(): HealthResponseDto
}

class KtorSystemHealthRemoteDataSource(
    private val httpClient: HttpClient,
) : SystemHealthRemoteDataSource {
    override suspend fun fetchHealth(): HealthResponseDto {
        val response = httpClient.get("/api/v1/health")
        if (response.status.isSuccess()) {
            return response.body()
        }
        throw ApiHttpException(
            statusCode = response.status.value,
            responseBody = response.bodyAsText(),
        )
    }
}
