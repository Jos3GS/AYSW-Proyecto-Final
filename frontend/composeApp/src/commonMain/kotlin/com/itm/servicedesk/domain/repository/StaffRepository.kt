package com.itm.servicedesk.domain.repository

import com.itm.servicedesk.domain.model.AppResult
import com.itm.servicedesk.domain.model.CreateStaffCommand
import com.itm.servicedesk.domain.model.StaffPage
import com.itm.servicedesk.domain.model.UserProfile
import com.itm.servicedesk.domain.model.UserRole

interface StaffRepository {
    suspend fun listStaff(
        page: Int = 1,
        size: Int = DEFAULT_PAGE_SIZE,
        roleFilter: UserRole? = null,
    ): AppResult<StaffPage>

    suspend fun createStaff(command: CreateStaffCommand): AppResult<UserProfile>

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }
}
