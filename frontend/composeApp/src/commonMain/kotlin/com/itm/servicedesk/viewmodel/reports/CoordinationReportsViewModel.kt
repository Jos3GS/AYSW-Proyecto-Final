package com.itm.servicedesk.viewmodel.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itm.servicedesk.data.mapper.ReportUiMapper
import com.itm.servicedesk.domain.model.AppResult
import com.itm.servicedesk.domain.model.ReportFilterQuery
import com.itm.servicedesk.domain.model.RequestCategory
import com.itm.servicedesk.domain.model.RequestPriority
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.domain.model.StaffMember
import com.itm.servicedesk.domain.model.UserRole
import com.itm.servicedesk.domain.repository.ReportRepository
import com.itm.servicedesk.domain.repository.StaffRepository
import com.itm.servicedesk.domain.validation.ReportFilterValidationResult
import com.itm.servicedesk.domain.validation.ReportFilterValidator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CoordinationReportsViewModel(
    private val reportRepository: ReportRepository,
    private val staffRepository: StaffRepository,
    private val validator: ReportFilterValidator = ReportFilterValidator(),
    private val uiMapper: ReportUiMapper = ReportUiMapper(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CoordinationReportsUiState())
    val uiState: StateFlow<CoordinationReportsUiState> = _uiState.asStateFlow()

    private var hasLoadedInitial = false
    private var reportLoadJob: Job? = null
    private var agentsLoadJob: Job? = null

    fun loadInitial() {
        val current = _uiState.value
        if (hasLoadedInitial && (current.summary != null || current.requestsPage != null)) {
            return
        }
        hasLoadedInitial = true
        loadDashboardAndRequests(
            filter = current.appliedFilter,
            page = 1,
            reloadAgents = current.availableAgents.isEmpty(),
        )
    }

    fun onFromDateChanged(value: String) {
        _uiState.update { state ->
            state.copy(
                draftFilter =
                    state.draftFilter.copy(
                        fromDateInput = value,
                        fromDateError = null,
                    ),
            )
        }
    }

    fun onToDateChanged(value: String) {
        _uiState.update { state ->
            state.copy(
                draftFilter =
                    state.draftFilter.copy(
                        toDateInput = value,
                        toDateError = null,
                    ),
            )
        }
    }

    fun onStatusFilterChanged(status: RequestStatus?) {
        _uiState.update { state ->
            state.copy(
                draftFilter = state.draftFilter.copy(selectedStatus = status),
            )
        }
    }

    fun onCategoryFilterChanged(category: RequestCategory?) {
        _uiState.update { state ->
            state.copy(
                draftFilter = state.draftFilter.copy(selectedCategory = category),
            )
        }
    }

    fun onPriorityFilterChanged(priority: RequestPriority?) {
        _uiState.update { state ->
            state.copy(
                draftFilter = state.draftFilter.copy(selectedPriority = priority),
            )
        }
    }

    fun onAssigneeFilterChanged(assigneeId: String?) {
        _uiState.update { state ->
            state.copy(
                draftFilter = state.draftFilter.copy(selectedAssigneeId = assigneeId),
            )
        }
    }

    fun onApplyFilters() {
        val draft = _uiState.value.draftFilter
        when (
            val validationResult =
                validator.validate(
                    fromDateInput = draft.fromDateInput,
                    toDateInput = draft.toDateInput,
                    status = draft.selectedStatus,
                    category = draft.selectedCategory,
                    priority = draft.selectedPriority,
                    assignedToId = draft.selectedAssigneeId,
                )
        ) {
            is ReportFilterValidationResult.Invalid -> {
                _uiState.update { state ->
                    state.copy(
                        draftFilter =
                            state.draftFilter.copy(
                                fromDateError = validationResult.fromDateError,
                                toDateError = validationResult.toDateError,
                            ),
                    )
                }
            }

            is ReportFilterValidationResult.Valid -> {
                _uiState.update { state ->
                    state.copy(
                        draftFilter =
                            state.draftFilter.copy(
                                fromDateError = null,
                                toDateError = null,
                            ),
                        appliedFilter = validationResult.query,
                        currentPage = 1,
                    )
                }
                loadDashboardAndRequests(
                    filter = validationResult.query,
                    page = 1,
                    reloadAgents = false,
                )
            }
        }
    }

    fun onClearFilters() {
        val clearedQuery = ReportFilterQuery()
        _uiState.update { state ->
            state.copy(
                draftFilter = ReportFilterDraft(),
                appliedFilter = clearedQuery,
                currentPage = 1,
            )
        }
        loadDashboardAndRequests(
            filter = clearedQuery,
            page = 1,
            reloadAgents = false,
        )
    }

    fun onPageSelected(page: Int) {
        val targetPage = page.coerceAtLeast(1)
        val current = _uiState.value
        if (current.isLoadingRequests) {
            return
        }
        loadRequestsOnly(filter = current.appliedFilter, page = targetPage)
    }

    fun onPreviousPage() {
        val current = _uiState.value
        if (!current.isLoading && current.currentPage > 1) {
            onPageSelected(current.currentPage - 1)
        }
    }

    fun onNextPage() {
        val current = _uiState.value
        val totalPages = current.requestsPage?.totalPages ?: 1
        if (!current.isLoading && current.currentPage < totalPages) {
            onPageSelected(current.currentPage + 1)
        }
    }

    fun onRetry() {
        val current = _uiState.value
        val needSummary = current.summary == null || current.currentPage == 1
        val shouldReloadAgents = current.availableAgents.isEmpty()

        if (needSummary) {
            loadDashboardAndRequests(
                filter = current.appliedFilter,
                page = current.currentPage,
                reloadAgents = shouldReloadAgents,
            )
        } else {
            loadRequestsOnly(filter = current.appliedFilter, page = current.currentPage)
            if (shouldReloadAgents) {
                launchAgentsLoad()
            }
        }
    }

    private fun loadDashboardAndRequests(
        filter: ReportFilterQuery,
        page: Int,
        reloadAgents: Boolean,
    ) {
        reportLoadJob?.cancel()
        _uiState.update { state ->
            state.copy(
                isLoadingSummary = true,
                isLoadingRequests = true,
                errorMessage = null,
            )
        }

        if (reloadAgents) {
            launchAgentsLoad()
        }

        reportLoadJob =
            viewModelScope.launch(dispatcher) {
                coroutineScope {
                    val summaryDeferred = async { reportRepository.getSummary(filter) }
                    val requestsDeferred =
                        async {
                            reportRepository.getRequests(
                                filter = filter,
                                page = page,
                                size = ReportRepository.DEFAULT_PAGE_SIZE,
                            )
                        }

                    val summaryResult = summaryDeferred.await()
                    val requestsResult = requestsDeferred.await()

                    _uiState.update { state ->
                        val nextSummary =
                            when (summaryResult) {
                                is AppResult.Success -> uiMapper.toSummaryUi(summaryResult.data)
                                is AppResult.Failure -> state.summary
                            }
                        val nextRequestsPage =
                            when (requestsResult) {
                                is AppResult.Success -> uiMapper.toRequestsPageUi(requestsResult.data)
                                is AppResult.Failure -> state.requestsPage
                            }
                        val nextPage =
                            when (requestsResult) {
                                is AppResult.Success -> requestsResult.data.page
                                is AppResult.Failure -> state.currentPage
                            }
                        val nextError =
                            when {
                                summaryResult is AppResult.Failure -> summaryResult.error.userMessage
                                requestsResult is AppResult.Failure -> requestsResult.error.userMessage
                                else -> null
                            }

                        state.copy(
                            isLoadingSummary = false,
                            isLoadingRequests = false,
                            summary = nextSummary,
                            requestsPage = nextRequestsPage,
                            currentPage = nextPage,
                            errorMessage = nextError,
                        )
                    }
                }
            }
    }

    private fun loadRequestsOnly(
        filter: ReportFilterQuery,
        page: Int,
    ) {
        reportLoadJob?.cancel()
        _uiState.update { state ->
            state.copy(
                isLoadingRequests = true,
                currentPage = page,
                errorMessage = null,
            )
        }

        reportLoadJob =
            viewModelScope.launch(dispatcher) {
                when (
                    val result =
                        reportRepository.getRequests(
                            filter = filter,
                            page = page,
                            size = ReportRepository.DEFAULT_PAGE_SIZE,
                        )
                ) {
                    is AppResult.Success -> {
                        val pageUi = uiMapper.toRequestsPageUi(result.data)
                        _uiState.update { state ->
                            state.copy(
                                isLoadingRequests = false,
                                requestsPage = pageUi,
                                currentPage = pageUi.page,
                                errorMessage = null,
                            )
                        }
                    }

                    is AppResult.Failure -> {
                        _uiState.update { state ->
                            state.copy(
                                isLoadingRequests = false,
                                errorMessage = result.error.userMessage,
                            )
                        }
                    }
                }
            }
    }

    private fun launchAgentsLoad() {
        agentsLoadJob?.cancel()
        _uiState.update { it.copy(isLoadingAgents = true) }
        agentsLoadJob =
            viewModelScope.launch(dispatcher) {
                val agents = fetchAllAttentionAgents()
                _uiState.update { state ->
                    state.copy(
                        isLoadingAgents = false,
                        availableAgents = agents ?: state.availableAgents,
                    )
                }
            }
    }

    private suspend fun fetchAllAttentionAgents(): List<StaffMember>? {
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
                    return null
                }
            }
        }
        return accumulated
    }
}
