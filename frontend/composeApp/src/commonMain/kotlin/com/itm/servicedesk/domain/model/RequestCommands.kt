package com.itm.servicedesk.domain.model

data class CreateServiceRequestCommand(
    val category: RequestCategory,
    val subject: String,
    val description: String,
    val previousRequestCode: String? = null,
)

data class CancelServiceRequestCommand(
    val code: String,
    val reason: String? = null,
)
