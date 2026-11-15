package com.itm.servicedesk.viewmodel

import com.itm.servicedesk.domain.model.UserProfile
import com.itm.servicedesk.domain.model.UserRole

data class CreateStaffUiState(
    val fullName: String = "",
    val documentNumber: String = "",
    val email: String = "",
    val phone: String = "",
    val password: String = "",
    val isPasswordVisible: Boolean = false,
    val selectedRole: UserRole = UserRole.PERSONAL_ATENCION,
    val fieldErrors: Map<String, String> = emptyMap(),
    val generalError: String? = null,
    val isConfirmDialogOpen: Boolean = false,
    val isSubmitting: Boolean = false,
    val successMessage: String? = null,
) {
    override fun toString(): String =
        "CreateStaffUiState(fullName=$fullName, documentNumber=$documentNumber, " +
            "email=$email, phone=$phone, password=***, isPasswordVisible=$isPasswordVisible, " +
            "selectedRole=$selectedRole, fieldErrors=$fieldErrors, generalError=$generalError, " +
            "isConfirmDialogOpen=$isConfirmDialogOpen, isSubmitting=$isSubmitting, " +
            "successMessage=$successMessage)"
}

sealed interface CreateStaffEvent {
    data class StaffCreated(
        val profile: UserProfile,
    ) : CreateStaffEvent
}
