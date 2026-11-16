package com.itm.servicedesk.viewmodel.reports

import com.itm.servicedesk.domain.model.ReportFilterQuery
import com.itm.servicedesk.domain.model.RequestCategory
import com.itm.servicedesk.domain.model.RequestPriority
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.domain.model.StaffMember

data class ReportFilterDraft(
    val fromDateInput: String = "",
    val toDateInput: String = "",
    val selectedStatus: RequestStatus? = null,
    val selectedCategory: RequestCategory? = null,
    val selectedPriority: RequestPriority? = null,
    val selectedAssigneeId: String? = null,
    val fromDateError: String? = null,
    val toDateError: String? = null,
)

data class CoordinationReportsUiState(
    val isLoadingSummary: Boolean = false,
    val isLoadingRequests: Boolean = false,
    val isLoadingAgents: Boolean = false,
    val summary: CoordinationReportSummaryUi? = null,
    val requestsPage: CoordinationReportRequestsPageUi? = null,
    val availableAgents: List<StaffMember> = emptyList(),
    val draftFilter: ReportFilterDraft = ReportFilterDraft(),
    val appliedFilter: ReportFilterQuery = ReportFilterQuery(),
    val currentPage: Int = 1,
    val errorMessage: String? = null,
) {
    val isLoading: Boolean
        get() = isLoadingSummary || isLoadingRequests
}
