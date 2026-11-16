package com.itm.servicedesk.viewmodel.attention

import com.itm.servicedesk.domain.model.AssignRequestCommand
import com.itm.servicedesk.domain.model.AttentionRequestDetail
import com.itm.servicedesk.domain.model.EscalateRequestCommand
import com.itm.servicedesk.domain.model.ReassignRequestCommand
import com.itm.servicedesk.domain.model.RequestPriority
import com.itm.servicedesk.domain.model.RespondRequestCommand
import com.itm.servicedesk.domain.model.StaffMember

enum class AgentActionTab {
    INTERVENTION,
    ESCALATE,
    RESPOND,
}

sealed interface PendingConfirmationAction {
    data class Assign(
        val command: AssignRequestCommand,
        val agentName: String,
    ) : PendingConfirmationAction

    data class Reassign(
        val command: ReassignRequestCommand,
        val agentName: String,
    ) : PendingConfirmationAction

    data class Escalate(
        val command: EscalateRequestCommand,
    ) : PendingConfirmationAction

    data class Respond(
        val command: RespondRequestCommand,
    ) : PendingConfirmationAction
}

data class AttentionDetailUiState(
    val detail: AttentionRequestDetail? = null,
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val loadErrorMessage: String? = null,
    val isNotFound: Boolean = false,
    val isForbidden: Boolean = false,
    val actionErrorMessage: String? = null,
    val actionSuccessMessage: String? = null,
    val availableAgents: List<StaffMember> = emptyList(),
    val isLoadingAgents: Boolean = false,
    val agentsLoadError: String? = null,
    val selectedAgentId: String = "",
    val selectedPriority: RequestPriority? = null,
    val reassignmentReasonDraft: String = "",
    val activeAgentTab: AgentActionTab = AgentActionTab.INTERVENTION,
    val interventionCommentDraft: String = "",
    val escalationReasonDraft: String = "",
    val responseTextDraft: String = "",
    val validationErrors: Map<String, String> = emptyMap(),
    val pendingConfirmation: PendingConfirmationAction? = null,
)
