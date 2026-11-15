package com.itm.servicedesk.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itm.servicedesk.data.mapper.StaffUiMapper
import com.itm.servicedesk.domain.model.AppResult
import com.itm.servicedesk.domain.model.CreateStaffCommand
import com.itm.servicedesk.domain.model.DomainError
import com.itm.servicedesk.domain.model.UserRole
import com.itm.servicedesk.domain.repository.StaffRepository
import com.itm.servicedesk.domain.validation.UserFormValidator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CreateStaffViewModel(
    private val staffRepository: StaffRepository,
    private val staffUiMapper: StaffUiMapper = StaffUiMapper(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CreateStaffUiState())
    val uiState: StateFlow<CreateStaffUiState> = _uiState.asStateFlow()

    private val _events = Channel<CreateStaffEvent>(Channel.BUFFERED)
    val events: Flow<CreateStaffEvent> = _events.receiveAsFlow()

    fun onFullNameChange(value: String) {
        _uiState.update {
            it.copy(
                fullName = value,
                fieldErrors = it.fieldErrors - UserFormValidator.FIELD_FULL_NAME,
                generalError = null,
            )
        }
    }

    fun onDocumentNumberChange(value: String) {
        _uiState.update {
            it.copy(
                documentNumber = value,
                fieldErrors = it.fieldErrors - UserFormValidator.FIELD_DOCUMENT_NUMBER,
                generalError = null,
            )
        }
    }

    fun onEmailChange(value: String) {
        _uiState.update {
            it.copy(
                email = value,
                fieldErrors = it.fieldErrors - UserFormValidator.FIELD_EMAIL,
                generalError = null,
            )
        }
    }

    fun onPhoneChange(value: String) {
        _uiState.update {
            it.copy(
                phone = value,
                fieldErrors = it.fieldErrors - UserFormValidator.FIELD_PHONE,
                generalError = null,
            )
        }
    }

    fun onPasswordChange(value: String) {
        _uiState.update {
            it.copy(
                password = value,
                fieldErrors = it.fieldErrors - UserFormValidator.FIELD_PASSWORD,
                generalError = null,
            )
        }
    }

    fun togglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun onRoleSelected(role: UserRole) {
        if (!role.isInternalStaff()) {
            return
        }
        _uiState.update {
            it.copy(
                selectedRole = role,
                fieldErrors = it.fieldErrors - UserFormValidator.FIELD_ROLE,
                generalError = null,
            )
        }
    }

    fun requestSubmit() {
        val snapshot = _uiState.value
        if (snapshot.isSubmitting) {
            return
        }

        val validationErrors =
            UserFormValidator.validateCreateStaff(
                fullName = snapshot.fullName,
                documentNumber = snapshot.documentNumber,
                email = snapshot.email,
                phone = snapshot.phone,
                password = snapshot.password,
                role = snapshot.selectedRole,
            )
        if (validationErrors.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    fieldErrors = validationErrors,
                    generalError = null,
                    isConfirmDialogOpen = false,
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                fieldErrors = emptyMap(),
                generalError = null,
                isConfirmDialogOpen = true,
            )
        }
    }

    fun dismissConfirmDialog() {
        _uiState.update { it.copy(isConfirmDialogOpen = false) }
    }

    fun confirmSubmit() {
        val snapshot = _uiState.value
        if (snapshot.isSubmitting || !snapshot.selectedRole.isInternalStaff()) {
            return
        }

        _uiState.update {
            it.copy(
                isConfirmDialogOpen = false,
                isSubmitting = true,
                fieldErrors = emptyMap(),
                generalError = null,
                successMessage = null,
            )
        }

        viewModelScope.launch(dispatcher) {
            val command =
                CreateStaffCommand(
                    fullName = snapshot.fullName.trim(),
                    documentNumber = snapshot.documentNumber.trim(),
                    email = UserFormValidator.normalizeEmail(snapshot.email),
                    phone = snapshot.phone.trim(),
                    password = snapshot.password,
                    role = snapshot.selectedRole,
                )
            when (val result = staffRepository.createStaff(command)) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(
                            password = "",
                            isPasswordVisible = false,
                            fieldErrors = emptyMap(),
                            generalError = null,
                            isSubmitting = false,
                            successMessage = "Personal registrado correctamente.",
                        )
                    }
                    _events.send(CreateStaffEvent.StaffCreated(result.data))
                }

                is AppResult.Failure -> {
                    val error = result.error
                    _uiState.update { state ->
                        if (error is DomainError.BadRequest) {
                            state.copy(
                                fieldErrors = error.fieldErrors,
                                generalError = if (error.fieldErrors.isEmpty()) error.userMessage else null,
                                isSubmitting = false,
                            )
                        } else {
                            state.copy(
                                generalError = error.userMessage,
                                isSubmitting = false,
                            )
                        }
                    }
                }
            }
        }
    }

    fun confirmationRoleLabel(): String = staffUiMapper.roleLabel(_uiState.value.selectedRole)
}
