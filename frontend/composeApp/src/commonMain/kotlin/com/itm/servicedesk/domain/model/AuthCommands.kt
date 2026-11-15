package com.itm.servicedesk.domain.model

data class RegisterCommand(
    val fullName: String,
    val documentNumber: String,
    val email: String,
    val phone: String,
    val password: String,
) {
    override fun toString(): String =
        "RegisterCommand(fullName=$fullName, documentNumber=$documentNumber, " +
            "email=$email, phone=$phone, password=***)"
}

data class LoginCommand(
    val email: String,
    val password: String,
) {
    override fun toString(): String = "LoginCommand(email=$email, password=***)"
}

data class CreateStaffCommand(
    val fullName: String,
    val documentNumber: String,
    val email: String,
    val phone: String,
    val password: String,
    val role: UserRole,
) {
    override fun toString(): String =
        "CreateStaffCommand(fullName=$fullName, documentNumber=$documentNumber, " +
            "email=$email, phone=$phone, password=***, role=$role)"
}
