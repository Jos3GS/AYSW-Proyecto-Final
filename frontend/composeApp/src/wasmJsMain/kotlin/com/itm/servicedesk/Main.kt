package com.itm.servicedesk

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.itm.servicedesk.core.di.appModules
import com.itm.servicedesk.ui.navigation.ServiceDeskAppRoot
import org.koin.core.context.startKoin

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    startKoin {
        modules(appModules)
    }
    ComposeViewport {
        ServiceDeskAppRoot()
    }
}
