package com.itm.servicedesk.domain.validation

import com.itm.servicedesk.domain.model.DomainException
import com.itm.servicedesk.domain.model.UserRole
import com.itm.servicedesk.domain.model.ValidationDetail

data class ValidatedUserInput(
    val fullName: String,
    val documentNumber: String,
    val email: String,
    val phone: String,
    val password: String,
) {
    override fun toString(): String =
        "ValidatedUserInput(fullName='$fullName', documentNumber='$documentNumber', " +
            "email='$email', phone='$phone', password='***')"
}

data class ValidatedStaffInput(
    val user: ValidatedUserInput,
    val role: UserRole,
)

data class ValidatedLoginInput(
    val email: String,
    val password: String,
) {
    override fun toString(): String = "ValidatedLoginInput(email='$email', password='***')"
}

object UserValidator {
    private const val MIN_FULL_NAME_LENGTH = 3
    private const val MAX_FULL_NAME_LENGTH = 100
    private const val MAX_EMAIL_LENGTH = 100
    private const val MIN_PASSWORD_LENGTH = 8
    private const val MAX_PASSWORD_UTF8_BYTES = 72

    private val NAME_REGEX = Regex("^[\\p{L} ]+$")
    private val DOCUMENT_REGEX = Regex("^[0-9]{5,15}$")
    private val PHONE_REGEX = Regex("^[0-9]{7,15}$")
    private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    fun validateRegistration(
        fullName: String,
        documentNumber: String,
        email: String,
        phone: String,
        password: String,
    ): ValidatedUserInput {
        val details = mutableListOf<ValidationDetail>()
        val normalized = normalizeAndValidateUserFields(fullName, documentNumber, email, phone, password, details)

        if (details.isNotEmpty()) {
            throw DomainException.ValidationException(
                message = VALIDATION_ERROR_MESSAGE,
                details = details,
            )
        }

        return normalized
    }

    fun validateStaffCreation(
        fullName: String,
        documentNumber: String,
        email: String,
        phone: String,
        password: String,
        rawRole: String,
    ): ValidatedStaffInput {
        val details = mutableListOf<ValidationDetail>()
        val normalizedUser =
            normalizeAndValidateUserFields(
                fullName = fullName,
                documentNumber = documentNumber,
                email = email,
                phone = phone,
                password = password,
                details = details,
            )

        val cleanedRole = rawRole.trim()
        val resolvedRole =
            if (cleanedRole == UserRole.PERSONAL_ATENCION.name) {
                UserRole.PERSONAL_ATENCION
            } else if (cleanedRole == UserRole.COORDINADOR.name) {
                UserRole.COORDINADOR
            } else {
                details.add(
                    ValidationDetail(
                        field = "role",
                        message = "El rol de personal interno debe ser PERSONAL_ATENCION o COORDINADOR.",
                    ),
                )
                null
            }

        if (details.isNotEmpty() || resolvedRole == null) {
            throw DomainException.ValidationException(
                message = VALIDATION_ERROR_MESSAGE,
                details = details,
            )
        }

        return ValidatedStaffInput(
            user = normalizedUser,
            role = resolvedRole,
        )
    }

    fun validateLogin(
        email: String,
        password: String,
    ): ValidatedLoginInput {
        val details = mutableListOf<ValidationDetail>()
        val normalizedEmail = email.trim().lowercase()

        if (!isValidEmail(normalizedEmail)) {
            details.add(
                ValidationDetail(
                    field = "email",
                    message = "El correo electrónico debe tener un formato válido y máximo 100 caracteres.",
                ),
            )
        }

        if (password.isBlank()) {
            details.add(
                ValidationDetail(
                    field = "password",
                    message = "La contraseña es obligatoria.",
                ),
            )
        }

        if (details.isNotEmpty()) {
            throw DomainException.ValidationException(
                message = VALIDATION_ERROR_MESSAGE,
                details = details,
            )
        }

        return ValidatedLoginInput(
            email = normalizedEmail,
            password = password,
        )
    }

    private fun normalizeAndValidateUserFields(
        fullName: String,
        documentNumber: String,
        email: String,
        phone: String,
        password: String,
        details: MutableList<ValidationDetail>,
    ): ValidatedUserInput {
        val normalizedName = fullName.trim()
        val normalizedDocument = documentNumber.trim()
        val normalizedEmail = email.trim().lowercase()
        val normalizedPhone = phone.trim()

        if (normalizedName.length !in MIN_FULL_NAME_LENGTH..MAX_FULL_NAME_LENGTH ||
            !NAME_REGEX.matches(normalizedName)
        ) {
            details.add(
                ValidationDetail(
                    field = "fullName",
                    message =
                        "El nombre completo debe tener entre 3 y 100 caracteres " +
                            "y contener solo letras y espacios.",
                ),
            )
        }

        if (!DOCUMENT_REGEX.matches(normalizedDocument)) {
            details.add(
                ValidationDetail(
                    field = "documentNumber",
                    message = "El documento debe contener entre 5 y 15 dígitos numéricos.",
                ),
            )
        }

        if (!isValidEmail(normalizedEmail)) {
            details.add(
                ValidationDetail(
                    field = "email",
                    message = "El correo electrónico debe tener un formato válido y máximo 100 caracteres.",
                ),
            )
        }

        if (!PHONE_REGEX.matches(normalizedPhone)) {
            details.add(
                ValidationDetail(
                    field = "phone",
                    message = "El teléfono debe contener entre 7 y 15 dígitos numéricos.",
                ),
            )
        }

        val utf8Bytes = password.toByteArray(Charsets.UTF_8).size
        val hasLetter = password.any { it.isLetter() }
        val hasDigit = password.any { it.isDigit() }
        if (utf8Bytes > MAX_PASSWORD_UTF8_BYTES) {
            details.add(
                ValidationDetail(
                    field = "password",
                    message = "La contraseña no debe superar los 72 bytes UTF-8.",
                ),
            )
        } else if (password.length < MIN_PASSWORD_LENGTH || !hasLetter || !hasDigit) {
            details.add(
                ValidationDetail(
                    field = "password",
                    message = "La contraseña debe tener al menos 8 caracteres, una letra y un número.",
                ),
            )
        }

        return ValidatedUserInput(
            fullName = normalizedName,
            documentNumber = normalizedDocument,
            email = normalizedEmail,
            phone = normalizedPhone,
            password = password,
        )
    }

    private fun isValidEmail(email: String): Boolean =
        email.isNotBlank() &&
            email.length <= MAX_EMAIL_LENGTH &&
            !email.contains("..") &&
            EMAIL_REGEX.matches(email)

    private const val VALIDATION_ERROR_MESSAGE = "Los datos proporcionados contienen errores de validación."
}
