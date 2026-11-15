package com.itm.servicedesk

import android.app.Application
import com.itm.servicedesk.core.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

class ServiceDeskApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (GlobalContext.getOrNull() == null) {
            startKoin {
                androidContext(this@ServiceDeskApp)
                modules(appModules)
            }
        }
    }
}
