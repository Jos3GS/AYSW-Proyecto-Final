package com.itm.servicedesk.data.repository

import com.itm.servicedesk.data.mapper.ErrorMapper
import com.itm.servicedesk.data.mapper.ReportMapper
import com.itm.servicedesk.data.remote.ApiHttpException
import com.itm.servicedesk.data.remote.ReportRemoteDataSource
import com.itm.servicedesk.domain.model.AppResult
import com.itm.servicedesk.domain.model.CoordinationReportRequestsPage
import com.itm.servicedesk.domain.model.CoordinationReportSummary
import com.itm.servicedesk.domain.model.ReportFilterQuery
import com.itm.servicedesk.domain.repository.ReportRepository
import kotlinx.coroutines.CancellationException

class ReportRepositoryImpl(
    private val remoteDataSource: ReportRemoteDataSource,
    private val reportMapper: ReportMapper = ReportMapper(),
    private val errorMapper: ErrorMapper = ErrorMapper(),
) : ReportRepository {
    override suspend fun getSummary(filter: ReportFilterQuery): AppResult<CoordinationReportSummary> =
        executeCall {
            reportMapper.toDomain(remoteDataSource.getSummary(filter))
        }

    override suspend fun getRequests(
        filter: ReportFilterQuery,
        page: Int,
        size: Int,
    ): AppResult<CoordinationReportRequestsPage> =
        executeCall {
            val dto =
                remoteDataSource.getRequests(
                    filter = filter,
                    page = page.coerceAtLeast(1),
                    size = size,
                )
            reportMapper.toDomain(dto)
        }

    private suspend inline fun <T> executeCall(block: () -> T): AppResult<T> =
        try {
            AppResult.Success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiHttpException) {
            AppResult.Failure(errorMapper.fromHttpResponse(e.statusCode, e.responseBody))
        } catch (e: Throwable) {
            AppResult.Failure(errorMapper.fromThrowable(e))
        }
}
