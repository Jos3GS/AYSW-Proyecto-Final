package com.itm.servicedesk.domain.validation

import com.itm.servicedesk.domain.model.RequestCategory
import com.itm.servicedesk.domain.model.RequestPriority
import com.itm.servicedesk.domain.model.RequestStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ReportFilterValidatorTest {
    private val validator = ReportFilterValidator()

    @Test
    fun `accepts blank dates and normalizes valid dd_mm_yyyy dates to ISO`() {
        val emptyResult = validator.validate(fromDateInput = "", toDateInput = "   ")
        val emptyValid = assertIs<ReportFilterValidationResult.Valid>(emptyResult)
        assertNull(emptyValid.query.createdFromIso)
        assertNull(emptyValid.query.createdToIso)

        val validResult =
            validator.validate(
                fromDateInput = " 01/11/2026 ",
                toDateInput = "14/11/2026",
                status = RequestStatus.EN_REVISION,
                category = RequestCategory.TRAMITE,
                priority = RequestPriority.ALTA,
                assignedToId = " agent-1 ",
            )
        val valid = assertIs<ReportFilterValidationResult.Valid>(validResult)
        assertEquals("2026-11-01", valid.query.createdFromIso)
        assertEquals("2026-11-14", valid.query.createdToIso)
        assertEquals(RequestStatus.EN_REVISION, valid.query.status)
        assertEquals(RequestCategory.TRAMITE, valid.query.category)
        assertEquals(RequestPriority.ALTA, valid.query.priority)
        assertEquals("agent-1", valid.query.assignedToId)
    }

    @Test
    fun `accepts valid leap day and equal from-to dates`() {
        val leapResult = validator.validate(fromDateInput = "29/02/2024", toDateInput = "29/02/2024")
        val valid = assertIs<ReportFilterValidationResult.Valid>(leapResult)
        assertEquals("2024-02-29", valid.query.createdFromIso)
        assertEquals("2024-02-29", valid.query.createdToIso)
    }

    @Test
    fun `rejects non-existent Gregorian dates and non-matching formats`() {
        val feb31 =
            assertIs<ReportFilterValidationResult.Invalid>(
                validator.validate(fromDateInput = "31/02/2026", toDateInput = ""),
            )
        assertEquals(ReportFilterValidator.INVALID_DATE_MESSAGE, feb31.fromDateError)
        assertNull(feb31.toDateError)

        val nonLeapFeb29 =
            assertIs<ReportFilterValidationResult.Invalid>(
                validator.validate(fromDateInput = "", toDateInput = "29/02/2026"),
            )
        assertNull(nonLeapFeb29.fromDateError)
        assertEquals(ReportFilterValidator.INVALID_DATE_MESSAGE, nonLeapFeb29.toDateError)

        val isoFormat =
            assertIs<ReportFilterValidationResult.Invalid>(
                validator.validate(fromDateInput = "2026-11-14", toDateInput = "14/11/26"),
            )
        assertEquals(ReportFilterValidator.INVALID_DATE_MESSAGE, isoFormat.fromDateError)
        assertEquals(ReportFilterValidator.INVALID_DATE_MESSAGE, isoFormat.toDateError)

        val yearZero =
            assertIs<ReportFilterValidationResult.Invalid>(
                validator.validate(fromDateInput = "01/01/0000", toDateInput = "1/11/2026"),
            )
        assertEquals(ReportFilterValidator.INVALID_DATE_MESSAGE, yearZero.fromDateError)
        assertEquals(ReportFilterValidator.INVALID_DATE_MESSAGE, yearZero.toDateError)
    }

    @Test
    fun `rejects inverted range when fromDate is after toDate`() {
        val invalidRange =
            assertIs<ReportFilterValidationResult.Invalid>(
                validator.validate(fromDateInput = "20/11/2026", toDateInput = "10/11/2026"),
            )
        assertNull(invalidRange.fromDateError)
        assertEquals(ReportFilterValidator.INVALID_RANGE_MESSAGE, invalidRange.toDateError)
    }
}
