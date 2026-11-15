package com.itm.servicedesk.data.remote

import com.itm.servicedesk.data.dto.CreateStaffRequestDto
import com.itm.servicedesk.data.dto.PagedStaffResponseDto
import com.itm.servicedesk.data.dto.UserProfileDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

interface StaffRemoteDataSource {
    suspend fun listStaff(
        page: Int,
        size: Int,
        role: String?,
    ): PagedStaffResponseDto

    suspend fun createStaff(request: CreateStaffRequestDto): UserProfileDto
}

class KtorStaffRemoteDataSource(
    private val httpClient: HttpClient,
) : StaffRemoteDataSource {
    override suspend fun listStaff(
        page: Int,
        size: Int,
        role: String?,
    ): PagedStaffResponseDto {
        val response =
            httpClient.get("/api/v1/users/staff") {
                parameter("page", page)
                parameter("size", size)
                if (!role.isNullOrBlank()) {
                    parameter("role", role)
                }
            }
        if (response.status.isSuccess()) {
            return response.body()
        }
        throw ApiHttpException(
            statusCode = response.status.value,
            responseBody = response.bodyAsText(),
        )
    }

    override suspend fun createStaff(request: CreateStaffRequestDto): UserProfileDto {
        val response =
            httpClient.post("/api/v1/users/staff") {
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
}
