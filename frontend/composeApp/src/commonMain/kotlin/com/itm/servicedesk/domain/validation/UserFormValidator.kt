package com.itm.servicedesk.domain.validation

import com.itm.servicedesk.domain.model.UserRole

object UserFormValidator {
    private const val MIN_FULL_NAME_LENGTH = 3
    private const val MAX_FULL_NAME_LENGTH = 100
    private const val MIN_DOCUMENT_LENGTH = 5
    private const val MAX_DOCUMENT_LENGTH = 15
    private const val MAX_EMAIL_LENGTH = 100
    private const val MIN_PHONE_LENGTH = 7
    private const val MAX_PHONE_LENGTH = 15
    private const val MIN_PASSWORD_LENGTH = 8
    private const val MAX_BCRYPT_PASSWORD_BYTES = 72

    private val EMAIL_REGEX =
        Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    const val FIELD_FULL_NAME = "fullName"
    const val FIELD_DOCUMENT_NUMBER = "documentNumber"
    const val FIELD_EMAIL = "email"
    const val FIELD_PHONE = "phone"
    const val FIELD_PASSWORD = "password"
    const val FIELD_ROLE = "role"

    fun normalizeEmail(email: String): String = email.trim().lowercase()

    fun validateFullName(fullName: String): String? {
        val trimmed = fullName.trim()
        return if (trimmed.isEmpty()) {
            "El nombre completo es obligatorio."
        } else if (trimmed.length !in MIN_FULL_NAME_LENGTH..MAX_FULL_NAME_LENGTH) {
            "El nombre completo debe tener entre 3 y 100 caracteres."
        } else if (!trimmed.all { it == ' ' || it.isLetter() }) {
            "El nombre completo solo puede contener letras y espacios."
        } else {
            null
        }
    }

    fun validateDocumentNumber(documentNumber: String): String? {
        val trimmed = documentNumber.trim()
        return if (trimmed.isEmpty()) {
            "El número de documento es obligatorio."
        } else if (trimmed.length !in MIN_DOCUMENT_LENGTH..MAX_DOCUMENT_LENGTH || !trimmed.all { it in '0'..'9' }) {
            "El documento debe contener entre 5 y 15 dígitos numéricos."
        } else {
            null
        }
    }

    fun validateEmail(email: String): String? {
        val normalized = normalizeEmail(email)
        return if (normalized.isEmpty()) {
            "El correo electrónico es obligatorio."
        } else if (normalized.length > MAX_EMAIL_LENGTH) {
            "El correo electrónico no puede superar los 100 caracteres."
        } else if (!EMAIL_REGEX.matches(normalized) || normalized.contains("..")) {
            "Ingresa un correo electrónico válido."
        } else {
            null
        }
    }

    fun validatePhone(phone: String): String? {
        val trimmed = phone.trim()
        return if (trimmed.isEmpty()) {
            "El teléfono es obligatorio."
        } else if (trimmed.length !in MIN_PHONE_LENGTH..MAX_PHONE_LENGTH || !trimmed.all { it in '0'..'9' }) {
            "El teléfono debe contener entre 7 y 15 dígitos numéricos."
        } else {
            null
        }
    }

    fun validatePassword(password: String): String? =
        if (password.isEmpty()) {
            "La contraseña es obligatoria."
        } else if (password.length < MIN_PASSWORD_LENGTH) {
            "La contraseña debe tener al menos 8 caracteres."
        } else if (password.encodeToByteArray().size > MAX_BCRYPT_PASSWORD_BYTES) {
            "La contraseña no puede exceder los 72 bytes en UTF-8."
        } else if (!password.any { it.isLetter() } || !password.any { it.isDigit() }) {
            "La contraseña debe incluir al menos una letra y un número."
        } else {
            null
        }

    fun validateLoginPassword(password: String): String? =
        if (password.isBlank()) {
            "La contraseña es obligatoria."
        } else {
            null
        }

    fun validateStaffRole(role: UserRole): String? =
        if (!role.isInternalStaff()) {
            "El personal interno solo puede tener rol Personal de atención o Coordinador."
        } else {
            null
        }

    fun validateLogin(
        email: String,
        password: String,
    ): Map<String, String> =
        buildMap {
            validateEmail(email)?.let { put(FIELD_EMAIL, it) }
            validateLoginPassword(password)?.let { put(FIELD_PASSWORD, it) }
        }

    fun validateRegistration(
        fullName: String,
        documentNumber: String,
        email: String,
        phone: String,
        password: String,
    ): Map<String, String> =
        buildMap {
            validateFullName(fullName)?.let { put(FIELD_FULL_NAME, it) }
            validateDocumentNumber(documentNumber)?.let { put(FIELD_DOCUMENT_NUMBER, it) }
            validateEmail(email)?.let { put(FIELD_EMAIL, it) }
            validatePhone(phone)?.let { put(FIELD_PHONE, it) }
            validatePassword(password)?.let { put(FIELD_PASSWORD, it) }
        }

    fun validateCreateStaff(
        fullName: String,
        documentNumber: String,
        email: String,
        phone: String,
        password: String,
        role: UserRole,
    ): Map<String, String> =
        buildMap {
            putAll(
                validateRegistration(
                    fullName = fullName,
                    documentNumber = documentNumber,
                    email = email,
                    phone = phone,
                    password = password,
                ),
            )
            validateStaffRole(role)?.let { put(FIELD_ROLE, it) }
        }
}
