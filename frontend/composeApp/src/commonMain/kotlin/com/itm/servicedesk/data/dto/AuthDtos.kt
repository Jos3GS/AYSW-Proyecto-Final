package com.itm.servicedesk.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequestDto(
    val fullName: String,
    val documentNumber: String,
    val email: String,
    val phone: String,
    val password: String,
) {
    override fun toString(): String =
        "RegisterRequestDto(fullName=$fullName, documentNumber=$documentNumber, " +
            "email=$email, phone=$phone, password=***)"
}

@Serializable
data class LoginRequestDto(
    val email: String,
    val password: String,
) {
    override fun toString(): String = "LoginRequestDto(email=$email, password=***)"
}

@Serializable
data class UserProfileDto(
    val id: String,
    val fullName: String,
    val documentNumber: String,
    val email: String,
    val phone: String,
    val role: String,
    val createdAt: String,
)

@Serializable
data class AuthResponseDto(
    val accessToken: String,
    val tokenType: String,
    val expiresInSeconds: Long,
    val expiresAt: String,
    val user: UserProfileDto,
) {
    override fun toString(): String =
        "AuthResponseDto(accessToken=***, tokenType=$tokenType, " +
            "expiresInSeconds=$expiresInSeconds, expiresAt=$expiresAt, user=$user)"
}
