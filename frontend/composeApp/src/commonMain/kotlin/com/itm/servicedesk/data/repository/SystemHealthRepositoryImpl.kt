package com.itm.servicedesk.data.repository

import com.itm.servicedesk.data.mapper.ErrorMapper
import com.itm.servicedesk.data.mapper.HealthMapper
import com.itm.servicedesk.data.remote.ApiHttpException
import com.itm.servicedesk.data.remote.SystemHealthRemoteDataSource
import com.itm.servicedesk.domain.model.AppResult
import com.itm.servicedesk.domain.model.ServiceHealth
import com.itm.servicedesk.domain.repository.SystemHealthRepository
import kotlinx.coroutines.CancellationException

class SystemHealthRepositoryImpl(
    private val remoteDataSource: SystemHealthRemoteDataSource,
    private val healthMapper: HealthMapper,
    private val errorMapper: ErrorMapper,
) : SystemHealthRepository {
    override suspend fun checkHealth(): AppResult<ServiceHealth> =
        try {
            val dto = remoteDataSource.fetchHealth()
            AppResult.Success(healthMapper.toDomain(dto))
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiHttpException) {
            AppResult.Failure(errorMapper.fromHttpResponse(e.statusCode, e.responseBody))
        } catch (e: Throwable) {
            AppResult.Failure(errorMapper.fromThrowable(e))
        }
}
