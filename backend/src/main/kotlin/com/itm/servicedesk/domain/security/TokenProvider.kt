package com.itm.servicedesk.domain.security

import com.itm.servicedesk.domain.model.IssuedToken
import com.itm.servicedesk.domain.model.User

interface TokenProvider {
    fun issueToken(user: User): IssuedToken
}
