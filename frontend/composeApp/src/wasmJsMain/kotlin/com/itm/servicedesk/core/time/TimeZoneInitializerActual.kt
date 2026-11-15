package com.itm.servicedesk.core.time

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsModule

@OptIn(ExperimentalWasmJsInterop::class)
@JsModule("@js-joda/timezone")
private external object JsJodaTimeZoneModule : JsAny

@OptIn(ExperimentalWasmJsInterop::class)
private val jsJodaTimeZoneRef: JsAny = JsJodaTimeZoneModule

@OptIn(ExperimentalWasmJsInterop::class)
internal actual fun ensureTimeZoneDatabaseInitialized() {
    jsJodaTimeZoneRef.hashCode()
}
