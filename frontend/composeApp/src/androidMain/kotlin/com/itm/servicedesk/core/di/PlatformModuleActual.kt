package com.itm.servicedesk.core.di

import com.itm.servicedesk.core.storage.AndroidSessionStorage
import com.itm.servicedesk.core.storage.SessionStorage
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module =
    module {
        single<SessionStorage> { AndroidSessionStorage(context = androidContext()) }
    }
