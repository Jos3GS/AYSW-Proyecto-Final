package com.itm.servicedesk.core.config

import com.itm.servicedesk.config.BuildKonfig

actual fun resolveApiBaseUrl(): String =
    if (BuildKonfig.IS_RELEASE) {
        BuildKonfig.RELEASE_API_URL
    } else {
        BuildKonfig.ANDROID_DEV_API_URL
    }
