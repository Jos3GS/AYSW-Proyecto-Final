package com.itm.servicedesk.viewmodel.attention

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itm.servicedesk.domain.model.AddInterventionCommand
import com.itm.servicedesk.domain.model.AppResult
import com.itm.servicedesk.domain.model.AssignRequestCommand
import com.itm.servicedesk.domain.model.AttentionRequestDetail
import com.itm.servicedesk.domain.model.DomainError
import com.itm.servicedesk.domain.model.EscalateRequestCommand
import com.itm.servicedesk.domain.model.ReassignRequestCommand
import com.itm.servicedesk.domain.model.RequestPriority
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.domain.model.RespondRequestCommand
import com.itm.servicedesk.domain.model.StaffMember
import com.itm.servicedesk.domain.model.UserRole
import com.itm.servicedesk.domain.repository.AttentionRepository
import com.itm.servicedesk.domain.repository.StaffRepository
import com.itm.servicedesk.domain.validation.AttentionFormValidator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AttentionDetailViewModel(
    private val attentionRepository: AttentionRepository,
    private val staffRepository: StaffRepository,
    private val validator: AttentionFormValidator = AttentionFormValidator(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AttentionDetailUiState())
    val uiState: StateFlow<AttentionDetailUiState> = _uiState.asStateFlow()

    private var currentCode: String = ""
    private var currentUserRole: UserRole? = null

    fun loadDetail(
        code: String,
        userRole: UserRole,
    ) {
        currentCode = code
        currentUserRole = userRole

        _uiState.value =
            AttentionDetailUiState(
                isLoading = true,
            )

        viewModelScope.launch(dispatcher) {
            fetchInternalDetail(code = code, userRole = userRole, preservedActionError = null)
        }
    }

    fun retryLoadDetail() {
        val role = currentUserRole ?: return
        if (currentCode.isBlank()) {
            return
        }
        loadDetail(code = currentCode, userRole = role)
    }

    fun retryLoadAgents() {
        val detail = _uiState.value.detail ?: return
        val role = currentUserRole ?: return
        if (!shouldLoadAgents(role, detail.status) || _uiState.value.isLoadingAgents) {
            return
        }
        viewModelScope.launch(dispatcher) {
            loadAllAssignableAgents()
        }
    }

    fun onSelectAgent(agentId: String) {
        _uiState.update { state ->
            state.copy(
                selectedAgentId = agentId,
                validationErrors = state.validationErrors - AttentionFormValidator.FIELD_ASSIGNEE_ID,
                actionErrorMessage = null,
                actionSuccessMessage = null,
            )
        }
    }

    fun onSelectPriority(priority: RequestPriority?) {
        _uiState.update { state ->
            state.copy(
                selectedPriority = priority,
                validationErrors = state.validationErrors - AttentionFormValidator.FIELD_PRIORITY,
                actionErrorMessage = null,
                actionSuccessMessage = null,
            )
        }
    }

    fun onReassignmentReasonChange(reason: String) {
        _uiState.update { state ->
            state.copy(
                reassignmentReasonDraft = reason,
                validationErrors = state.validationErrors - AttentionFormValidator.FIELD_REASON,
                actionErrorMessage = null,
                actionSuccessMessage = null,
            )
        }
    }

    fun onSelectAgentTab(tab: AgentActionTab) {
        _uiState.update { state ->
            state.copy(
                activeAgentTab = tab,
                actionErrorMessage = null,
                actionSuccessMessage = null,
            )
        }
    }

    fun onInterventionCommentChange(comment: String) {
        _uiState.update { state ->
            state.copy(
                interventionCommentDraft = comment,
                validationErrors = state.validationErrors - AttentionFormValidator.FIELD_COMMENT,
                actionErrorMessage = null,
                actionSuccessMessage = null,
            )
        }
    }

    fun onEscalationReasonChange(reason: String) {
        _uiState.update { state ->
            state.copy(
                escalationReasonDraft = reason,
                validationErrors = state.validationErrors - AttentionFormValidator.FIELD_REASON,
                actionErrorMessage = null,
                actionSuccessMessage = null,
            )
        }
    }

    fun onResponseTextChange(responseText: String) {
        _uiState.update { state ->
            state.copy(
                responseTextDraft = responseText,
                validationErrors = state.validationErrors - AttentionFormValidator.FIELD_RESPONSE_TEXT,
                actionErrorMessage = null,
                actionSuccessMessage = null,
            )
        }
    }

    fun requestAssignConfirmation() {
        val state = _uiState.value
        val detail = state.detail ?: return
        if (state.isSubmitting || detail.status != RequestStatus.ABIERTA) {
            return
        }

        val errors = validator.validateAssign(state.selectedAgentId, state.selectedPriority)
        if (errors.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    validationErrors = it.validationErrors + errors,
                    pendingConfirmation = null,
                )
            }
            return
        }

        val normalizedAgentId = state.selectedAgentId.trim()
        val priority = state.selectedPriority ?: return
        val agentName = resolveAgentName(state.availableAgents, normalizedAgentId)

        _uiState.update {
            it.copy(
                validationErrors = emptyMap(),
                actionErrorMessage = null,
                actionSuccessMessage = null,
                pendingConfirmation =
                    PendingConfirmationAction.Assign(
                        command = AssignRequestCommand(assigneeId = normalizedAgentId, priority = priority),
                        agentName = agentName,
                    ),
            )
        }
    }

    fun requestReassignConfirmation() {
        val state = _uiState.value
        val detail = state.detail ?: return
        if (state.isSubmitting || detail.status != RequestStatus.ESCALADA) {
            return
        }

        val errors =
            validator.validateReassign(
                assigneeId = state.selectedAgentId,
                reason = state.reassignmentReasonDraft,
                priority = state.selectedPriority,
            )
        if (errors.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    validationErrors = it.validationErrors + errors,
                    pendingConfirmation = null,
                )
            }
            return
        }

        val normalizedAgentId = state.selectedAgentId.trim()
        val agentName = resolveAgentName(state.availableAgents, normalizedAgentId)

        _uiState.update {
            it.copy(
                validationErrors = emptyMap(),
                actionErrorMessage = null,
                actionSuccessMessage = null,
                pendingConfirmation =
                    PendingConfirmationAction.Reassign(
                        command =
                            ReassignRequestCommand(
                                assigneeId = normalizedAgentId,
                                reason = state.reassignmentReasonDraft.trim(),
                                priority = state.selectedPriority,
                            ),
                        agentName = agentName,
                    ),
            )
        }
    }

    fun requestEscalateConfirmation() {
        val state = _uiState.value
        val detail = state.detail ?: return
        if (state.isSubmitting || detail.status != RequestStatus.EN_REVISION) {
            return
        }

        val errors = validator.validateEscalate(state.escalationReasonDraft)
        if (errors.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    validationErrors = it.validationErrors + errors,
                    pendingConfirmation = null,
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                validationErrors = it.validationErrors - AttentionFormValidator.FIELD_REASON,
                actionErrorMessage = null,
                actionSuccessMessage = null,
                pendingConfirmation =
                    PendingConfirmationAction.Escalate(
                        command = EscalateRequestCommand(reason = state.escalationReasonDraft.trim()),
                    ),
            )
        }
    }

    fun requestRespondConfirmation() {
        val state = _uiState.value
        val detail = state.detail ?: return
        if (state.isSubmitting || detail.status != RequestStatus.EN_REVISION) {
            return
        }

        val errors = validator.validateRespond(state.responseTextDraft)
        if (errors.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    validationErrors = it.validationErrors + errors,
                    pendingConfirmation = null,
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                validationErrors = it.validationErrors - AttentionFormValidator.FIELD_RESPONSE_TEXT,
                actionErrorMessage = null,
                actionSuccessMessage = null,
                pendingConfirmation =
                    PendingConfirmationAction.Respond(
                        command = RespondRequestCommand(responseText = state.responseTextDraft.trim()),
                    ),
            )
        }
    }

    fun dismissConfirmation() {
        _uiState.update { state ->
            if (state.isSubmitting) {
                state
            } else {
                state.copy(pendingConfirmation = null)
            }
        }
    }

    fun confirmPendingAction() {
        val state = _uiState.value
        val pending = state.pendingConfirmation ?: return
        val code = state.detail?.code ?: return
        if (state.isSubmitting) {
            return
        }

        _uiState.update {
            it.copy(
                isSubmitting = true,
                actionErrorMessage = null,
                actionSuccessMessage = null,
            )
        }

        viewModelScope.launch(dispatcher) {
            val result =
                when (pending) {
                    is PendingConfirmationAction.Assign -> {
                        attentionRepository.assignRequest(code = code, command = pending.command)
                    }

                    is PendingConfirmationAction.Reassign -> {
                        attentionRepository.reassignRequest(code = code, command = pending.command)
                    }

                    is PendingConfirmationAction.Escalate -> {
                        attentionRepository.escalateRequest(code = code, command = pending.command)
                    }

                    is PendingConfirmationAction.Respond -> {
                        attentionRepository.respondRequest(code = code, command = pending.command)
                    }
                }

            when (result) {
                is AppResult.Success -> {
                    val updated = result.data
                    _uiState.update { current ->
                        when (pending) {
                            is PendingConfirmationAction.Assign -> {
                                current.copy(
                                    detail = updated,
                                    isSubmitting = false,
                                    pendingConfirmation = null,
                                    selectedAgentId = "",
                                    selectedPriority = null,
                                    availableAgents = emptyList(),
                                    validationErrors = emptyMap(),
                                    actionErrorMessage = null,
                                    actionSuccessMessage = "Solicitud asignada correctamente.",
                                )
                            }

                            is PendingConfirmationAction.Reassign -> {
                                current.copy(
                                    detail = updated,
                                    isSubmitting = false,
                                    pendingConfirmation = null,
                                    selectedAgentId = "",
                                    selectedPriority = null,
                                    reassignmentReasonDraft = "",
                                    availableAgents = emptyList(),
                                    validationErrors = emptyMap(),
                                    actionErrorMessage = null,
                                    actionSuccessMessage = "Solicitud reasignada correctamente.",
                                )
                            }

                            is PendingConfirmationAction.Escalate -> {
                                current.copy(
                                    detail = updated,
                                    isSubmitting = false,
                                    pendingConfirmation = null,
                                    escalationReasonDraft = "",
                                    validationErrors = current.validationErrors - AttentionFormValidator.FIELD_REASON,
                                    actionErrorMessage = null,
                                    actionSuccessMessage = "Solicitud escalada correctamente.",
                                )
                            }

                            is PendingConfirmationAction.Respond -> {
                                current.copy(
                                    detail = updated,
                                    isSubmitting = false,
                                    pendingConfirmation = null,
                                    responseTextDraft = "",
                                    validationErrors =
                                        current.validationErrors - AttentionFormValidator.FIELD_RESPONSE_TEXT,
                                    actionErrorMessage = null,
                                    actionSuccessMessage = "Respuesta oficial registrada correctamente.",
                                )
                            }
                        }
                    }
                }

                is AppResult.Failure -> {
                    handleMutationFailure(code = code, error = result.error)
                }
            }
        }
    }

    fun submitIntervention() {
        val state = _uiState.value
        val code = state.detail?.code ?: return
        if (state.isSubmitting || state.detail.status != RequestStatus.EN_REVISION) {
            return
        }

        val errors = validator.validateIntervention(state.interventionCommentDraft)
        if (errors.isNotEmpty()) {
            _uiState.update {
                it.copy(validationErrors = it.validationErrors + errors)
            }
            return
        }

        _uiState.update {
            it.copy(
                isSubmitting = true,
                actionErrorMessage = null,
                actionSuccessMessage = null,
                validationErrors = it.validationErrors - AttentionFormValidator.FIELD_COMMENT,
            )
        }

        viewModelScope.launch(dispatcher) {
            val command = AddInterventionCommand(comment = state.interventionCommentDraft.trim())
            when (val result = attentionRepository.addIntervention(code = code, command = command)) {
                is AppResult.Success -> {
                    _uiState.update { current ->
                        current.copy(
                            detail = result.data,
                            isSubmitting = false,
                            interventionCommentDraft = "",
                            validationErrors = current.validationErrors - AttentionFormValidator.FIELD_COMMENT,
                            actionErrorMessage = null,
                            actionSuccessMessage = "Intervención registrada correctamente.",
                        )
                    }
                }

                is AppResult.Failure -> {
                    handleMutationFailure(code = code, error = result.error)
                }
            }
        }
    }

    private suspend fun handleMutationFailure(
        code: String,
        error: DomainError,
    ) {
        when (error) {
            is DomainError.Conflict -> {
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        pendingConfirmation = null,
                        actionErrorMessage = error.userMessage,
                        actionSuccessMessage = null,
                    )
                }
                val role = currentUserRole ?: return
                fetchInternalDetail(
                    code = code,
                    userRole = role,
                    preservedActionError = error.userMessage,
                )
            }

            is DomainError.BadRequest -> {
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        pendingConfirmation = null,
                        validationErrors = it.validationErrors + error.fieldErrors,
                        actionErrorMessage = if (error.fieldErrors.isEmpty()) error.userMessage else null,
                    )
                }
            }

            else -> {
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        pendingConfirmation = null,
                        actionErrorMessage = error.userMessage,
                    )
                }
            }
        }
    }

    private suspend fun fetchInternalDetail(
        code: String,
        userRole: UserRole,
        preservedActionError: String?,
    ) {
        when (val result = attentionRepository.getInternalDetail(code)) {
            is AppResult.Success -> {
                val detail = result.data
                _uiState.update { current ->
                    current.copy(
                        detail = detail,
                        isLoading = false,
                        loadErrorMessage = null,
                        isNotFound = false,
                        isForbidden = false,
                        actionErrorMessage = preservedActionError,
                    )
                }
                if (shouldLoadAgents(userRole, detail.status)) {
                    loadAllAssignableAgents()
                } else {
                    _uiState.update {
                        it.copy(
                            availableAgents = emptyList(),
                            isLoadingAgents = false,
                            agentsLoadError = null,
                        )
                    }
                }
            }

            is AppResult.Failure -> {
                val error = result.error
                _uiState.update { current ->
                    current.copy(
                        detail = if (preservedActionError == null) null else current.detail,
                        isLoading = false,
                        loadErrorMessage = error.userMessage,
                        isNotFound = error is DomainError.NotFound,
                        isForbidden = error is DomainError.Forbidden,
                        actionErrorMessage = preservedActionError,
                    )
                }
            }
        }
    }

    private suspend fun loadAllAssignableAgents() {
        _uiState.update {
            it.copy(
                isLoadingAgents = true,
                agentsLoadError = null,
            )
        }

        val accumulated = mutableListOf<StaffMember>()
        val seenIds = mutableSetOf<String>()
        var page = 1
        var totalPages = 1

        while (page <= totalPages) {
            when (
                val result =
                    staffRepository.listStaff(
                        page = page,
                        size = StaffRepository.DEFAULT_PAGE_SIZE,
                        roleFilter = UserRole.PERSONAL_ATENCION,
                    )
            ) {
                is AppResult.Success -> {
                    val staffPage = result.data
                    staffPage.items.forEach { member ->
                        if (member.role == UserRole.PERSONAL_ATENCION && seenIds.add(member.id)) {
                            accumulated.add(member)
                        }
                    }
                    totalPages = staffPage.totalPages.coerceAtLeast(1)
                    if (staffPage.items.isEmpty()) {
                        break
                    }
                    page += 1
                }

                is AppResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            availableAgents = emptyList(),
                            isLoadingAgents = false,
                            agentsLoadError = result.error.userMessage,
                        )
                    }
                    return
                }
            }
        }

        _uiState.update {
            it.copy(
                availableAgents = accumulated,
                isLoadingAgents = false,
                agentsLoadError = null,
            )
        }
    }

    private fun shouldLoadAgents(
        userRole: UserRole,
        status: RequestStatus,
    ): Boolean =
        userRole == UserRole.COORDINADOR &&
            (status == RequestStatus.ABIERTA || status == RequestStatus.ESCALADA)

    private fun resolveAgentName(
        agents: List<StaffMember>,
        agentId: String,
    ): String = agents.firstOrNull { it.id == agentId }?.fullName ?: agentId
}
