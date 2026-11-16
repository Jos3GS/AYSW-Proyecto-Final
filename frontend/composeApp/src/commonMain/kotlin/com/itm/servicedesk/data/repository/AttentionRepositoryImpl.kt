package com.itm.servicedesk.data.repository

import com.itm.servicedesk.data.mapper.AttentionMapper
import com.itm.servicedesk.data.mapper.ErrorMapper
import com.itm.servicedesk.data.remote.ApiHttpException
import com.itm.servicedesk.data.remote.AttentionRemoteDataSource
import com.itm.servicedesk.domain.model.AddInterventionCommand
import com.itm.servicedesk.domain.model.AppResult
import com.itm.servicedesk.domain.model.AssignRequestCommand
import com.itm.servicedesk.domain.model.AttentionRequestDetail
import com.itm.servicedesk.domain.model.AttentionRequestsPage
import com.itm.servicedesk.domain.model.EscalateRequestCommand
import com.itm.servicedesk.domain.model.ReassignRequestCommand
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.domain.model.RespondRequestCommand
import com.itm.servicedesk.domain.repository.AttentionRepository
import kotlinx.coroutines.CancellationException

class AttentionRepositoryImpl(
    private val remoteDataSource: AttentionRemoteDataSource,
    private val attentionMapper: AttentionMapper = AttentionMapper(),
    private val errorMapper: ErrorMapper = ErrorMapper(),
) : AttentionRepository {
    override suspend fun getCoordinationPending(
        page: Int,
        size: Int,
        status: RequestStatus?,
    ): AppResult<AttentionRequestsPage> =
        executeCall {
            val dto =
                remoteDataSource.getCoordinationPending(
                    page = page.coerceAtLeast(1),
                    size = size,
                    status = status?.name,
                )
            attentionMapper.toDomain(dto)
        }

    override suspend fun getMyAssigned(
        page: Int,
        size: Int,
        status: RequestStatus?,
    ): AppResult<AttentionRequestsPage> =
        executeCall {
            val dto =
                remoteDataSource.getMyAssigned(
                    page = page.coerceAtLeast(1),
                    size = size,
                    status = status?.name,
                )
            attentionMapper.toDomain(dto)
        }

    override suspend fun getInternalDetail(code: String): AppResult<AttentionRequestDetail> =
        executeCall {
            attentionMapper.toDomain(remoteDataSource.getInternalDetail(code))
        }

    override suspend fun assignRequest(
        code: String,
        command: AssignRequestCommand,
    ): AppResult<AttentionRequestDetail> =
        executeCall {
            val dto = remoteDataSource.assignRequest(code, attentionMapper.toAssignDto(command))
            attentionMapper.toDomain(dto)
        }

    override suspend fun reassignRequest(
        code: String,
        command: ReassignRequestCommand,
    ): AppResult<AttentionRequestDetail> =
        executeCall {
            val dto = remoteDataSource.reassignRequest(code, attentionMapper.toReassignDto(command))
            attentionMapper.toDomain(dto)
        }

    override suspend fun addIntervention(
        code: String,
        command: AddInterventionCommand,
    ): AppResult<AttentionRequestDetail> =
        executeCall {
            val dto = remoteDataSource.addIntervention(code, attentionMapper.toInterventionDto(command))
            attentionMapper.toDomain(dto)
        }

    override suspend fun escalateRequest(
        code: String,
        command: EscalateRequestCommand,
    ): AppResult<AttentionRequestDetail> =
        executeCall {
            val dto = remoteDataSource.escalateRequest(code, attentionMapper.toEscalateDto(command))
            attentionMapper.toDomain(dto)
        }

    override suspend fun respondRequest(
        code: String,
        command: RespondRequestCommand,
    ): AppResult<AttentionRequestDetail> =
        executeCall {
            val dto = remoteDataSource.respondRequest(code, attentionMapper.toRespondDto(command))
            attentionMapper.toDomain(dto)
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
