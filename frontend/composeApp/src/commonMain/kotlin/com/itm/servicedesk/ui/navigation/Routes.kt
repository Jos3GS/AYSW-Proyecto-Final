package com.itm.servicedesk.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object StartupRoute

@Serializable
data object LoginRoute

@Serializable
data object RegisterRoute

@Serializable
data object ApplicantHomeRoute

@Serializable
data class CreateRequestRoute(
    val previousRequestCode: String? = null,
)

@Serializable
data class RequestDetailRoute(
    val code: String,
)

@Serializable
data object AgentHomeRoute

@Serializable
data object CoordinatorHomeRoute

@Serializable
data object StaffListRoute

@Serializable
data object CreateStaffRoute

@Serializable
data class AttentionDetailRoute(
    val code: String,
)
