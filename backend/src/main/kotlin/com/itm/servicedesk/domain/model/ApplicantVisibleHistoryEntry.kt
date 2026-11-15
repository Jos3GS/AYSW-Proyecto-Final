package com.itm.servicedesk.domain.model

import java.time.OffsetDateTime
import java.util.UUID

data class ApplicantVisibleHistoryEntry(
    val id: UUID,
    val action: HistoryAction,
    val previousState: RequestStatus?,
    val newState: RequestStatus,
    val occurredAt: OffsetDateTime,
    val comment: String?,
) {
    companion object {
        fun fromRaw(
            id: UUID,
            action: HistoryAction,
            previousState: RequestStatus?,
            newState: RequestStatus,
            occurredAt: OffsetDateTime,
            rawComment: String?,
        ): ApplicantVisibleHistoryEntry? {
            if (action == HistoryAction.INTERVENCION) {
                return null
            }
            val visibleComment =
                if (action == HistoryAction.ESCALAMIENTO || action == HistoryAction.REASIGNACION) {
                    null
                } else {
                    rawComment
                }
            return ApplicantVisibleHistoryEntry(
                id = id,
                action = action,
                previousState = previousState,
                newState = newState,
                occurredAt = occurredAt,
                comment = visibleComment,
            )
        }
    }
}
