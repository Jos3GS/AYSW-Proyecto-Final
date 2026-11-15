package com.itm.servicedesk.domain.repository

import com.itm.servicedesk.domain.model.AppResult
import com.itm.servicedesk.domain.model.AuthSession
import com.itm.servicedesk.domain.model.LoginCommand
import com.itm.servicedesk.domain.model.RegisterCommand
import com.itm.servicedesk.domain.model.UserProfile

interface AuthRepository {
    suspend fun register(command: RegisterCommand): AppResult<AuthSession>

    suspend fun login(command: LoginCommand): AppResult<AuthSession>

    suspend fun getCurrentUser(): AppResult<UserProfile>

    suspend fun logout(): AppResult<Unit>
}
