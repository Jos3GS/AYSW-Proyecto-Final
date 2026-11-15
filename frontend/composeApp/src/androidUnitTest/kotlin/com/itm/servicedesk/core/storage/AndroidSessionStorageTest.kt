package com.itm.servicedesk.core.storage

import android.app.Application
import android.content.Context
import com.itm.servicedesk.core.config.resolveApiBaseUrl
import com.itm.servicedesk.core.di.platformModule
import com.itm.servicedesk.core.network.createPlatformHttpClientEngine
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AndroidSessionStorageTest {
    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `saves loads and clears token with in-memory cache and persistence`() =
        runTest {
            val context = RuntimeEnvironment.getApplication()
            val prefs = context.getSharedPreferences("test_session_prefs", Context.MODE_PRIVATE)
            prefs.edit().clear().commit()

            val storage =
                AndroidSessionStorage(
                    context = context,
                    sharedPreferencesProvider = { prefs },
                )

            assertNull(storage.getToken())

            storage.saveToken("jwt-abc-123")
            assertEquals("jwt-abc-123", storage.getToken())

            val freshStorage =
                AndroidSessionStorage(
                    context = context,
                    sharedPreferencesProvider = { prefs },
                )
            assertEquals("jwt-abc-123", freshStorage.getToken())

            freshStorage.clearToken()
            assertNull(freshStorage.getToken())
            assertNull(prefs.getString("jwt_token", null))
        }

    @Test
    fun `resolves android api url and OkHttp engine and platformModule`() {
        val baseUrl = resolveApiBaseUrl()
        assertTrue(baseUrl.startsWith("http"))

        val engine = createPlatformHttpClientEngine()
        assertNotNull(engine)
        engine.close()

        stopKoin()
        val koinApp =
            startKoin {
                androidContext(RuntimeEnvironment.getApplication())
                modules(platformModule())
            }
        assertNotNull(koinApp.koin.get<SessionStorage>())
    }
}
