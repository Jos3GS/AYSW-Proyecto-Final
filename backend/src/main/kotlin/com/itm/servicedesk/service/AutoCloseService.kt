package com.itm.servicedesk.service

import com.itm.servicedesk.domain.repository.AttentionRepository
import com.itm.servicedesk.domain.time.TimeProvider

class AutoCloseService(
    private val attentionRepository: AttentionRepository,
    private val timeProvider: TimeProvider,
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
) {
    fun closeEligibleRequests(): Int {
        val now = timeProvider.now()
        val cutoff = now.minusHours(AUTO_CLOSE_HOURS)
        var totalClosed = 0
        while (true) {
            val batchClosed =
                attentionRepository.autoCloseEligibleRequests(
                    cutoff = cutoff,
                    now = now,
                    batchSize = batchSize,
                )
            totalClosed += batchClosed
            if (batchClosed < batchSize) {
                break
            }
        }
        return totalClosed
    }

    companion object {
        const val DEFAULT_BATCH_SIZE = 100
        private const val AUTO_CLOSE_HOURS = 24L
    }
}
