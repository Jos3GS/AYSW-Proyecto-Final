package com.itm.servicedesk.data.remote

import com.itm.servicedesk.data.dto.attention.AddInterventionRequestDto
import com.itm.servicedesk.data.dto.attention.AssignRequestDto
import com.itm.servicedesk.data.dto.attention.AttentionRequestDetailDto
import com.itm.servicedesk.data.dto.attention.EscalateRequestDto
import com.itm.servicedesk.data.dto.attention.PagedAttentionRequestsResponseDto
import com.itm.servicedesk.data.dto.attention.ReassignRequestDto
import com.itm.servicedesk.data.dto.attention.RespondRequestDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess

interface AttentionRemoteDataSource {
    suspend fun getCoordinationPending(
        page: Int,
        size: Int,
        status: String?,
    ): PagedAttentionRequestsResponseDto

    suspend fun getMyAssigned(
        page: Int,
        size: Int,
        status: String?,
    ): PagedAttentionRequestsResponseDto

    suspend fun getInternalDetail(code: String): AttentionRequestDetailDto

    suspend fun assignRequest(
        code: String,
        request: AssignRequestDto,
    ): AttentionRequestDetailDto

    suspend fun reassignRequest(
        code: String,
        request: ReassignRequestDto,
    ): AttentionRequestDetailDto

    suspend fun addIntervention(
        code: String,
        request: AddInterventionRequestDto,
    ): AttentionRequestDetailDto

    suspend fun escalateRequest(
        code: String,
        request: EscalateRequestDto,
    ): AttentionRequestDetailDto

    suspend fun respondRequest(
        code: String,
        request: RespondRequestDto,
    ): AttentionRequestDetailDto
}

class KtorAttentionRemoteDataSource(
    private val httpClient: HttpClient,
) : AttentionRemoteDataSource {
    override suspend fun getCoordinationPending(
        page: Int,
        size: Int,
        status: String?,
    ): PagedAttentionRequestsResponseDto {
        val response =
            httpClient.get("/api/v1/attention/coordination/pending") {
                parameter("page", page)
                parameter("size", size)
                if (!status.isNullOrBlank()) {
                    parameter("status", status)
                }
            }
        return parseOrThrow(response)
    }

    override suspend fun getMyAssigned(
        page: Int,
        size: Int,
        status: String?,
    ): PagedAttentionRequestsResponseDto {
        val response =
            httpClient.get("/api/v1/attention/my-assigned") {
                parameter("page", page)
                parameter("size", size)
                if (!status.isNullOrBlank()) {
                    parameter("status", status)
                }
            }
        return parseOrThrow(response)
    }

    override suspend fun getInternalDetail(code: String): AttentionRequestDetailDto {
        val encodedCode = code.trim().encodeURLPathPart()
        val response = httpClient.get("/api/v1/attention/requests/$encodedCode")
        return parseOrThrow(response)
    }

    override suspend fun assignRequest(
        code: String,
        request: AssignRequestDto,
    ): AttentionRequestDetailDto {
        val encodedCode = code.trim().encodeURLPathPart()
        val response =
            httpClient.post("/api/v1/attention/requests/$encodedCode/assign") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        return parseOrThrow(response)
    }

    override suspend fun reassignRequest(
        code: String,
        request: ReassignRequestDto,
    ): AttentionRequestDetailDto {
        val encodedCode = code.trim().encodeURLPathPart()
        val response =
            httpClient.post("/api/v1/attention/requests/$encodedCode/reassign") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        return parseOrThrow(response)
    }

    override suspend fun addIntervention(
        code: String,
        request: AddInterventionRequestDto,
    ): AttentionRequestDetailDto {
        val encodedCode = code.trim().encodeURLPathPart()
        val response =
            httpClient.post("/api/v1/attention/requests/$encodedCode/interventions") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        return parseOrThrow(response)
    }

    override suspend fun escalateRequest(
        code: String,
        request: EscalateRequestDto,
    ): AttentionRequestDetailDto {
        val encodedCode = code.trim().encodeURLPathPart()
        val response =
            httpClient.post("/api/v1/attention/requests/$encodedCode/escalate") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        return parseOrThrow(response)
    }

    override suspend fun respondRequest(
        code: String,
        request: RespondRequestDto,
    ): AttentionRequestDetailDto {
        val encodedCode = code.trim().encodeURLPathPart()
        val response =
            httpClient.post("/api/v1/attention/requests/$encodedCode/respond") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        return parseOrThrow(response)
    }

    private suspend inline fun <reified T> parseOrThrow(response: HttpResponse): T {
        if (response.status.isSuccess()) {
            return response.body()
        }
        throw ApiHttpException(
            statusCode = response.status.value,
            responseBody = response.bodyAsText(),
        )
    }
}
