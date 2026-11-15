package com.itm.servicedesk.core.network

import io.ktor.client.engine.HttpClientEngine

expect fun createPlatformHttpClientEngine(): HttpClientEngine
