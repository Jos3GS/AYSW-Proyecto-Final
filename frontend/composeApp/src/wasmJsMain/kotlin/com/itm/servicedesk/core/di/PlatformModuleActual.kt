package com.itm.servicedesk.core.di

import com.itm.servicedesk.core.storage.SessionStorage
import com.itm.servicedesk.core.storage.WebSessionStorage
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module =
    module {
        single<SessionStorage> { WebSessionStorage() }
    }
