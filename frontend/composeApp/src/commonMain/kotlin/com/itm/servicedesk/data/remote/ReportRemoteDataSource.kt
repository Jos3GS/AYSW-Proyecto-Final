package com.itm.servicedesk.data.remote

import com.itm.servicedesk.data.dto.reports.CoordinationReportSummaryDto
import com.itm.servicedesk.data.dto.reports.PagedCoordinationReportRequestsResponseDto
import com.itm.servicedesk.domain.model.ReportFilterQuery
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

interface ReportRemoteDataSource {
    suspend fun getSummary(filter: ReportFilterQuery): CoordinationReportSummaryDto

    suspend fun getRequests(
        filter: ReportFilterQuery,
        page: Int,
        size: Int,
    ): PagedCoordinationReportRequestsResponseDto
}

class KtorReportRemoteDataSource(
    private val httpClient: HttpClient,
) : ReportRemoteDataSource {
    override suspend fun getSummary(filter: ReportFilterQuery): CoordinationReportSummaryDto {
        val response =
            httpClient.get("/api/v1/reports/summary") {
                applyReportFilterParameters(filter)
            }
        return parseOrThrow(response)
    }

    override suspend fun getRequests(
        filter: ReportFilterQuery,
        page: Int,
        size: Int,
    ): PagedCoordinationReportRequestsResponseDto {
        val response =
            httpClient.get("/api/v1/reports/requests") {
                applyReportFilterParameters(filter)
                parameter("page", page.coerceAtLeast(1))
                parameter("size", size)
            }
        return parseOrThrow(response)
    }

    private fun HttpRequestBuilder.applyReportFilterParameters(filter: ReportFilterQuery) {
        filter.createdFromIso?.takeIf { it.isNotBlank() }?.let { parameter("createdFrom", it) }
        filter.createdToIso?.takeIf { it.isNotBlank() }?.let { parameter("createdTo", it) }
        filter.status?.let { parameter("status", it.name) }
        filter.category?.let { parameter("category", it.name) }
        filter.priority?.let { parameter("priority", it.name) }
        filter.assignedToId?.takeIf { it.isNotBlank() }?.let { parameter("assignedToId", it) }
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
