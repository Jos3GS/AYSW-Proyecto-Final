package com.itm.servicedesk.service

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopping
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class AutoCloseScheduler(
    private val autoCloseService: AutoCloseService,
    private val intervalSeconds: Long,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private var job: Job? = null

    fun registerLifecycle(application: Application) {
        application.monitor.subscribe(ApplicationStarted) {
            start(application)
        }
        application.monitor.subscribe(ApplicationStopping) {
            stop()
        }
    }

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) {
            return
        }
        val intervalMillis = intervalSeconds.coerceAtLeast(1L) * MILLIS_PER_SECOND
        job =
            scope.launch(dispatcher) {
                while (isActive) {
                    runSingleCycle()
                    delay(intervalMillis)
                }
            }
    }

    internal fun runSingleCycle(): Int =
        try {
            val closed = autoCloseService.closeEligibleRequests()
            if (closed > 0) {
                logger.info("AutoCloseScheduler cerró {} solicitudes vencidas tras 24h.", closed)
            }
            closed
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Throwable) {
            logger.error("Error transitorio en ciclo de AutoCloseScheduler: {}", ex.message)
            0
        }

    fun stop() {
        job?.cancel()
        job = null
    }

    companion object {
        private const val MILLIS_PER_SECOND = 1000L
        private val logger = LoggerFactory.getLogger(AutoCloseScheduler::class.java)
    }
}
