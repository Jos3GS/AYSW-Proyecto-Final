package com.itm.servicedesk

import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ServiceDeskApp::class)
class AndroidEntryPointsTest {
    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `ServiceDeskApp initializes Koin idempotently and MainActivity launches`() {
        val app = RuntimeEnvironment.getApplication() as ServiceDeskApp
        assertNotNull(GlobalContext.getOrNull())
        app.onCreate()
        assertNotNull(GlobalContext.getOrNull())

        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        assertNotNull(activity)
    }
}
