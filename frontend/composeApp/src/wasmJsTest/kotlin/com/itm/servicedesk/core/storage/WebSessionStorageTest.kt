package com.itm.servicedesk.core.storage

import com.itm.servicedesk.core.config.resolveApiBaseUrl
import com.itm.servicedesk.core.di.platformModule
import com.itm.servicedesk.core.network.createPlatformHttpClientEngine
import kotlinx.coroutines.test.runTest
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.js
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalWasmJsInterop::class)
private fun readBrowserSessionStorage(key: String): String? = js("window.sessionStorage.getItem(key)")

@OptIn(ExperimentalWasmJsInterop::class)
private fun clearBrowserSessionStorage(): Unit = js("window.sessionStorage.clear()")

class WebSessionStorageTest {
    @AfterTest
    fun tearDown() {
        clearBrowserSessionStorage()
        stopKoin()
    }

    @Test
    fun `stores and clears jwt token in memory and browser sessionStorage`() =
        runTest {
            clearBrowserSessionStorage()
            val storage = WebSessionStorage()

            assertNull(storage.getToken())

            storage.saveToken("wasm-jwt-token")
            assertEquals("wasm-jwt-token", storage.getToken())
            assertEquals("wasm-jwt-token", readBrowserSessionStorage("itm_sd_jwt_token"))

            val freshStorage = WebSessionStorage()
            assertEquals("wasm-jwt-token", freshStorage.getToken())

            freshStorage.clearToken()
            assertNull(freshStorage.getToken())
            assertNull(readBrowserSessionStorage("itm_sd_jwt_token"))
        }

    @Test
    fun `resolves web dev api url and js engine and platformModule`() {
        val baseUrl = resolveApiBaseUrl()
        assertTrue(baseUrl.startsWith("http"))

        val engine = createPlatformHttpClientEngine()
        assertNotNull(engine)
        engine.close()

        stopKoin()
        val koinApp =
            startKoin {
                modules(platformModule())
            }
        assertNotNull(koinApp.koin.get<SessionStorage>())
    }
}
