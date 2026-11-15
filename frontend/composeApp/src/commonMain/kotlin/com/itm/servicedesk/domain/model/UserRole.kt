package com.itm.servicedesk.domain.model

enum class UserRole {
    SOLICITANTE,
    PERSONAL_ATENCION,
    COORDINADOR,
    ;

    fun isInternalStaff(): Boolean = this == PERSONAL_ATENCION || this == COORDINADOR
}
