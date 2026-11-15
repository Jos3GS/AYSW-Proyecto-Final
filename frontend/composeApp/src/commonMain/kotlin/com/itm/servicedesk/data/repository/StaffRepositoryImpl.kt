package com.itm.servicedesk.data.repository

import com.itm.servicedesk.data.mapper.AuthMapper
import com.itm.servicedesk.data.mapper.ErrorMapper
import com.itm.servicedesk.data.mapper.StaffMapper
import com.itm.servicedesk.data.remote.ApiHttpException
import com.itm.servicedesk.data.remote.StaffRemoteDataSource
import com.itm.servicedesk.domain.model.AppResult
import com.itm.servicedesk.domain.model.CreateStaffCommand
import com.itm.servicedesk.domain.model.StaffPage
import com.itm.servicedesk.domain.model.UserProfile
import com.itm.servicedesk.domain.model.UserRole
import com.itm.servicedesk.domain.repository.StaffRepository
import kotlinx.coroutines.CancellationException

class StaffRepositoryImpl(
    private val remoteDataSource: StaffRemoteDataSource,
    private val staffMapper: StaffMapper = StaffMapper(),
    private val authMapper: AuthMapper = AuthMapper(),
    private val errorMapper: ErrorMapper = ErrorMapper(),
) : StaffRepository {
    override suspend fun listStaff(
        page: Int,
        size: Int,
        roleFilter: UserRole?,
    ): AppResult<StaffPage> =
        try {
            val dto =
                remoteDataSource.listStaff(
                    page = page.coerceAtLeast(1),
                    size = size,
                    role = roleFilter?.name,
                )
            AppResult.Success(staffMapper.toDomain(dto))
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiHttpException) {
            AppResult.Failure(errorMapper.fromHttpResponse(e.statusCode, e.responseBody))
        } catch (e: Throwable) {
            AppResult.Failure(errorMapper.fromThrowable(e))
        }

    override suspend fun createStaff(command: CreateStaffCommand): AppResult<UserProfile> =
        try {
            val dto = remoteDataSource.createStaff(staffMapper.toCreateStaffRequestDto(command))
            AppResult.Success(authMapper.toDomain(dto))
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiHttpException) {
            AppResult.Failure(errorMapper.fromHttpResponse(e.statusCode, e.responseBody))
        } catch (e: Throwable) {
            AppResult.Failure(errorMapper.fromThrowable(e))
        }
}
