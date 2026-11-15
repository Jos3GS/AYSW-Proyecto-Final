package com.itm.servicedesk.viewmodel

import com.itm.servicedesk.domain.model.AuthSession

data class RegisterUiState(
    val fullName: String = "",
    val documentNumber: String = "",
    val email: String = "",
    val phone: String = "",
    val password: String = "",
    val isPasswordVisible: Boolean = false,
    val fieldErrors: Map<String, String> = emptyMap(),
    val generalError: String? = null,
    val isSubmitting: Boolean = false,
) {
    override fun toString(): String =
        "RegisterUiState(fullName=$fullName, documentNumber=$documentNumber, " +
            "email=$email, phone=$phone, password=***, isPasswordVisible=$isPasswordVisible, " +
            "fieldErrors=$fieldErrors, generalError=$generalError, isSubmitting=$isSubmitting)"
}

sealed interface RegisterEvent {
    data class RegisteredAndAuthenticated(
        val session: AuthSession,
    ) : RegisterEvent
}
