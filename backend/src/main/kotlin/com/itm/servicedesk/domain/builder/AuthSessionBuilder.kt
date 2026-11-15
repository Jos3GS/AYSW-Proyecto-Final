package com.itm.servicedesk.domain.builder

import com.itm.servicedesk.domain.model.AuthSession
import com.itm.servicedesk.domain.model.IssuedToken
import com.itm.servicedesk.domain.model.User
import com.itm.servicedesk.domain.model.UserProfile

class AuthSessionBuilder {
    private var token: IssuedToken? = null
    private var user: UserProfile? = null

    fun token(token: IssuedToken): AuthSessionBuilder =
        apply {
            this.token = token
        }

    fun user(user: UserProfile): AuthSessionBuilder =
        apply {
            this.user = user
        }

    fun user(user: User): AuthSessionBuilder =
        apply {
            this.user = user.toProfile()
        }

    fun build(): AuthSession {
        val resolvedToken = checkNotNull(token) { "IssuedToken es obligatorio para construir AuthSession." }
        val resolvedUser = checkNotNull(user) { "UserProfile es obligatorio para construir AuthSession." }
        return AuthSession(
            token = resolvedToken,
            user = resolvedUser,
        )
    }
}
