package com.itm.servicedesk

import com.itm.servicedesk.domain.builder.AttentionPageQuery
import com.itm.servicedesk.domain.model.AttentionRequestDetail
import com.itm.servicedesk.domain.model.AttentionRequestSummary
import com.itm.servicedesk.domain.model.AttentionTransition
import com.itm.servicedesk.domain.model.PagedResult
import com.itm.servicedesk.domain.model.RequestStatus
import com.itm.servicedesk.domain.model.ServiceRequest
import com.itm.servicedesk.domain.repository.AttentionRepository
import com.itm.servicedesk.service.AutoCloseScheduler
import com.itm.servicedesk.service.AutoCloseService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AutoCloseSchedulerTest {
    private val bogotaOffset = ZoneOffset.of("-05:00")

    private class StubAttentionRepository(
        private val onAutoClose: () -> Int,
    ) : AttentionRepository {
        override fun findPendingForCoordination(
            query: AttentionPageQuery,
            now: OffsetDateTime,
        ): PagedResult<AttentionRequestSummary> = error("Not used")

        override fun findAssignedToAgent(
            query: AttentionPageQuery,
            now: OffsetDateTime,
        ): PagedResult<AttentionRequestSummary> = error("Not used")

        override fun findRequestByCode(code: String): ServiceRequest? = error("Not used")

        override fun findInternalDetailByCode(
            code: String,
            now: OffsetDateTime,
        ): AttentionRequestDetail? = error("Not used")

        override fun applyTransitionWithHistory(
            expectedCurrentStatus: RequestStatus,
            transition: AttentionTransition,
            historyEntryId: UUID,
        ): ServiceRequest? = error("Not used")

        override fun autoCloseEligibleRequests(
            cutoff: OffsetDateTime,
            now: OffsetDateTime,
            batchSize: Int,
        ): Int = onAutoClose()
    }

    @Test
    fun `runSingleCycle returns closed count and survives transient exceptions`() {
        val fakeClock = FakeTimeProvider(OffsetDateTime.of(2026, 11, 15, 10, 0, 0, 0, bogotaOffset))

        val okService = AutoCloseService(StubAttentionRepository { 3 }, fakeClock)
        val okScheduler = AutoCloseScheduler(okService, intervalSeconds = 60L, dispatcher = Dispatchers.Unconfined)
        assertEquals(3, okScheduler.runSingleCycle())

        val failingService =
            AutoCloseService(
                StubAttentionRepository { throw IllegalStateException("Transient DB error") },
                fakeClock,
            )
        val failingScheduler =
            AutoCloseScheduler(failingService, intervalSeconds = 60L, dispatcher = Dispatchers.Unconfined)
        assertEquals(0, failingScheduler.runSingleCycle())

        val cancellingService =
            AutoCloseService(
                StubAttentionRepository { throw CancellationException("Shutdown") },
                fakeClock,
            )
        val cancellingScheduler =
            AutoCloseScheduler(cancellingService, intervalSeconds = 60L, dispatcher = Dispatchers.Unconfined)
        assertFailsWith<CancellationException> {
            cancellingScheduler.runSingleCycle()
        }
    }

    @Test
    fun `start is idempotent and stop cancels running coroutine cleanly`() =
        runBlocking {
            val fakeClock = FakeTimeProvider(OffsetDateTime.of(2026, 11, 15, 10, 0, 0, 0, bogotaOffset))
            val cycles = AtomicInteger(0)
            val service = AutoCloseService(StubAttentionRepository { cycles.incrementAndGet() }, fakeClock)
            val scheduler = AutoCloseScheduler(service, intervalSeconds = 60L, dispatcher = Dispatchers.Default)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

            try {
                scheduler.start(scope)
                scheduler.start(scope)
                var waited = 0
                while (cycles.get() == 0 && waited < 20) {
                    delay(25)
                    waited++
                }
                assertTrue(cycles.get() >= 1)
                scheduler.stop()
                scheduler.stop()
            } finally {
                scheduler.stop()
                scope.cancel()
            }
        }
}
