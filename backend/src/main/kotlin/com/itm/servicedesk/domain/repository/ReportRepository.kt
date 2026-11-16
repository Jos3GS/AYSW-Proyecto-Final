package com.itm.servicedesk.domain.repository

import com.itm.servicedesk.domain.model.CoordinationReportPageQuery
import com.itm.servicedesk.domain.model.CoordinationReportRequestItem
import com.itm.servicedesk.domain.model.CoordinationReportSummary
import com.itm.servicedesk.domain.model.PagedResult
import com.itm.servicedesk.domain.model.ReportFilterCriteria
import java.time.OffsetDateTime

interface ReportRepository {
    fun getSummary(
        criteria: ReportFilterCriteria,
        now: OffsetDateTime,
    ): CoordinationReportSummary

    fun findRequests(
        query: CoordinationReportPageQuery,
        now: OffsetDateTime,
    ): PagedResult<CoordinationReportRequestItem>
}
