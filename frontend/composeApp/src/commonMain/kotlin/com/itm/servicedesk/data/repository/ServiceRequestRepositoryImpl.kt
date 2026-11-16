package com.itm.servicedesk.data.repository

import com.itm.servicedesk.data.mapper.ErrorMapper
import com.itm.servicedesk.data.mapper.RequestMapper
import com.itm.servicedesk.data.remote.ApiHttpException
import com.itm.servicedesk.data.remote.RequestRemoteDataSource
import com.itm.servicedesk.domain.model.AppResult
import com.itm.servicedesk.domain.model.CancelServiceRequestCommand
import com.itm.servicedesk.domain.model.CreateServiceRequestCommand
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.domain.model.ServiceRequestDetail
import com.itm.servicedesk.domain.model.ServiceRequestsPage
import com.itm.servicedesk.domain.repository.ServiceRequestRepository
import kotlinx.coroutines.CancellationException

class ServiceRequestRepositoryImpl(
    private val remoteDataSource: RequestRemoteDataSource,
    private val requestMapper: RequestMapper = RequestMapper(),
    private val errorMapper: ErrorMapper = ErrorMapper(),
) : ServiceRequestRepository {
    override suspend fun create(command: CreateServiceRequestCommand): AppResult<ServiceRequestDetail> =
        try {
            val dto = remoteDataSource.create(requestMapper.toCreateDto(command))
            AppResult.Success(requestMapper.toDomain(dto))
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiHttpException) {
            AppResult.Failure(errorMapper.fromHttpResponse(e.statusCode, e.responseBody))
        } catch (e: Throwable) {
            AppResult.Failure(errorMapper.fromThrowable(e))
        }

    override suspend fun getMyRequests(
        page: Int,
        size: Int,
        statusFilter: RequestStatus?,
    ): AppResult<ServiceRequestsPage> =
        try {
            val dto =
                remoteDataSource.getMyRequests(
                    page = page.coerceAtLeast(1),
                    size = size,
                    status = statusFilter?.name,
                )
            AppResult.Success(requestMapper.toDomain(dto))
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiHttpException) {
            AppResult.Failure(errorMapper.fromHttpResponse(e.statusCode, e.responseBody))
        } catch (e: Throwable) {
            AppResult.Failure(errorMapper.fromThrowable(e))
        }

    override suspend fun getDetail(code: String): AppResult<ServiceRequestDetail> =
        try {
            val dto = remoteDataSource.getDetail(code)
            AppResult.Success(requestMapper.toDomain(dto))
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiHttpException) {
            AppResult.Failure(errorMapper.fromHttpResponse(e.statusCode, e.responseBody))
        } catch (e: Throwable) {
            AppResult.Failure(errorMapper.fromThrowable(e))
        }

    override suspend fun cancel(command: CancelServiceRequestCommand): AppResult<ServiceRequestDetail> =
        try {
            val dto = remoteDataSource.cancel(command.code, requestMapper.toCancelDto(command))
            AppResult.Success(requestMapper.toDomain(dto))
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiHttpException) {
            AppResult.Failure(errorMapper.fromHttpResponse(e.statusCode, e.responseBody))
        } catch (e: Throwable) {
            AppResult.Failure(errorMapper.fromThrowable(e))
        }
}
