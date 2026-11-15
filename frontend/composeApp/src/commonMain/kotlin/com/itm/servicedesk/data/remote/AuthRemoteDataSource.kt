package com.itm.servicedesk.data.remote

import com.itm.servicedesk.data.dto.AuthResponseDto
import com.itm.servicedesk.data.dto.LoginRequestDto
import com.itm.servicedesk.data.dto.RegisterRequestDto
import com.itm.servicedesk.data.dto.UserProfileDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

interface AuthRemoteDataSource {
    suspend fun register(request: RegisterRequestDto): AuthResponseDto

    suspend fun login(request: LoginRequestDto): AuthResponseDto

    suspend fun getCurrentUser(): UserProfileDto

    suspend fun logout()
}

class KtorAuthRemoteDataSource(
    private val httpClient: HttpClient,
) : AuthRemoteDataSource {
    override suspend fun register(request: RegisterRequestDto): AuthResponseDto {
        val response =
            httpClient.post("/api/v1/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        if (response.status.isSuccess()) {
            return response.body()
        }
        throw ApiHttpException(
            statusCode = response.status.value,
            responseBody = response.bodyAsText(),
        )
    }

    override suspend fun login(request: LoginRequestDto): AuthResponseDto {
        val response =
            httpClient.post("/api/v1/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        if (response.status.isSuccess()) {
            return response.body()
        }
        throw ApiHttpException(
            statusCode = response.status.value,
            responseBody = response.bodyAsText(),
        )
    }

    override suspend fun getCurrentUser(): UserProfileDto {
        val response = httpClient.get("/api/v1/auth/me")
        if (response.status.isSuccess()) {
            return response.body()
        }
        throw ApiHttpException(
            statusCode = response.status.value,
            responseBody = response.bodyAsText(),
        )
    }

    override suspend fun logout() {
        val response = httpClient.post("/api/v1/auth/logout")
        if (!response.status.isSuccess()) {
            throw ApiHttpException(
                statusCode = response.status.value,
                responseBody = response.bodyAsText(),
            )
        }
    }
}
