package com.itm.servicedesk.domain.repository

import com.itm.servicedesk.domain.model.AppResult
import com.itm.servicedesk.domain.model.CoordinationReportRequestsPage
import com.itm.servicedesk.domain.model.CoordinationReportSummary
import com.itm.servicedesk.domain.model.ReportFilterQuery

interface ReportRepository {
    suspend fun getSummary(filter: ReportFilterQuery): AppResult<CoordinationReportSummary>

    suspend fun getRequests(
        filter: ReportFilterQuery,
        page: Int = 1,
        size: Int = DEFAULT_PAGE_SIZE,
    ): AppResult<CoordinationReportRequestsPage>

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }
}
