package com.itm.servicedesk.data.mapper

import com.itm.servicedesk.core.time.DateFormatter
import com.itm.servicedesk.domain.model.StaffMember
import com.itm.servicedesk.domain.model.UserRole
import com.itm.servicedesk.viewmodel.StaffItemUiModel

class StaffUiMapper {
    fun toUiModel(member: StaffMember): StaffItemUiModel =
        StaffItemUiModel(
            id = member.id,
            fullName = member.fullName,
            email = member.email,
            role = member.role,
            roleLabel = roleLabel(member.role),
            formattedCreatedAt = DateFormatter.formatBogotaDateTime(member.createdAt),
        )

    fun roleLabel(role: UserRole): String =
        when (role) {
            UserRole.SOLICITANTE -> "Solicitante"
            UserRole.PERSONAL_ATENCION -> "Personal de atención"
            UserRole.COORDINADOR -> "Coordinador"
        }
}
