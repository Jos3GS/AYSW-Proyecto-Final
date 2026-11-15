package com.itm.servicedesk.viewmodel

import com.itm.servicedesk.domain.model.AuthSession

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isPasswordVisible: Boolean = false,
    val fieldErrors: Map<String, String> = emptyMap(),
    val generalError: String? = null,
    val isSubmitting: Boolean = false,
) {
    override fun toString(): String =
        "LoginUiState(email=$email, password=***, isPasswordVisible=$isPasswordVisible, " +
            "fieldErrors=$fieldErrors, generalError=$generalError, isSubmitting=$isSubmitting)"
}

sealed interface LoginEvent {
    data class Authenticated(
        val session: AuthSession,
    ) : LoginEvent
}
