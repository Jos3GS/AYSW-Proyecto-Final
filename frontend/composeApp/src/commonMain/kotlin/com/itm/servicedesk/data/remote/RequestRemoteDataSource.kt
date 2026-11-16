package com.itm.servicedesk.data.remote

import com.itm.servicedesk.data.dto.requests.CancelServiceRequestDto
import com.itm.servicedesk.data.dto.requests.CreateServiceRequestDto
import com.itm.servicedesk.data.dto.requests.PagedServiceRequestsResponseDto
import com.itm.servicedesk.data.dto.requests.ServiceRequestDetailDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess

interface RequestRemoteDataSource {
    suspend fun create(request: CreateServiceRequestDto): ServiceRequestDetailDto

    suspend fun getMyRequests(
        page: Int,
        size: Int,
        status: String?,
    ): PagedServiceRequestsResponseDto

    suspend fun getDetail(code: String): ServiceRequestDetailDto

    suspend fun cancel(
        code: String,
        request: CancelServiceRequestDto,
    ): ServiceRequestDetailDto
}

class KtorRequestRemoteDataSource(
    private val httpClient: HttpClient,
) : RequestRemoteDataSource {
    override suspend fun create(request: CreateServiceRequestDto): ServiceRequestDetailDto {
        val response =
            httpClient.post("/api/v1/requests") {
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

    override suspend fun getMyRequests(
        page: Int,
        size: Int,
        status: String?,
    ): PagedServiceRequestsResponseDto {
        val response =
            httpClient.get("/api/v1/requests/my") {
                parameter("page", page)
                parameter("size", size)
                if (!status.isNullOrBlank()) {
                    parameter("status", status)
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

    override suspend fun getDetail(code: String): ServiceRequestDetailDto {
        val encodedCode = code.trim().encodeURLPathPart()
        val response = httpClient.get("/api/v1/requests/$encodedCode")
        if (response.status.isSuccess()) {
            return response.body()
        }
        throw ApiHttpException(
            statusCode = response.status.value,
            responseBody = response.bodyAsText(),
        )
    }

    override suspend fun cancel(
        code: String,
        request: CancelServiceRequestDto,
    ): ServiceRequestDetailDto {
        val encodedCode = code.trim().encodeURLPathPart()
        val response =
            httpClient.post("/api/v1/requests/$encodedCode/cancel") {
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
