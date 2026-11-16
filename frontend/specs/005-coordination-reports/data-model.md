# Modelo de Datos y Estado UI: Consolidado y Reportes de Coordinación (Frontend)

**Feature:** `005-coordination-reports`
**Subproyecto:** Frontend (Kotlin Multiplatform + Compose Multiplatform)
**Spec Asociada:** `specs/005-coordination-reports/spec.md`
**Versión:** 1.0.0
**Fecha:** 2026-11-14
**Estado:** Aprobado

---

## 1. Modelos Puros de Dominio (`domain/model/ReportModels.kt`)

| Tipo | Campos Principales | Descripción |
| :--- | :--- | :--- |
| **`ReportFilterQuery`** | `createdFromIso: String?` (`YYYY-MM-DD`), `createdToIso: String?` (`YYYY-MM-DD`), `status: RequestStatus?`, `category: RequestCategory?`, `priority: RequestPriority?`, `assignedToId: String?` | Criterio normalizado listo para serializarse como query parameters hacia `/api/v1/reports/*`. |
| **`AssigneeMetric`** | `assigneeId: String`, `assigneeName: String`, `totalAssigned: Long`, `overdueAssigned: Long` | Métricas individuales de carga y vencimientos por agente. |
| **`CoordinationReportSummary`** | `generatedAt: Instant`, `totalRequests: Long`, `byStatus: Map<RequestStatus, Long>`, `byCategory: Map<RequestCategory, Long>`, `byPriority: Map<RequestPriority, Long>`, `unassignedPriorityCount: Long`, `byAssignee: List<AssigneeMetric>`, `unassignedCount: Long`, `overdueCount: Long`, `resolvedWithinSlaCount: Long`, `resolvedOutsideSlaCount: Long` | Modelo de dominio del tablero consolidado devuelto por `/api/v1/reports/summary`. |
| **`CoordinationReportRequestItem`** | `id: String`, `code: String`, `subject: String`, `category: RequestCategory`, `status: RequestStatus`, `priority: RequestPriority?`, `requesterId: String`, `requesterName: String`, `assignedToId: String?`, `assignedToName: String?`, `dueAt: Instant?`, `isOverdue: Boolean`, `respondedAt: Instant?`, `respondedWithinSla: Boolean?`, `closedAt: Instant?`, `createdAt: Instant` | Fila del listado general devuelta por `/api/v1/reports/requests`. |
| **`CoordinationReportRequestsPage`** | `items: List<CoordinationReportRequestItem>`, `page: Int`, `size: Int`, `totalItems: Long`, `totalPages: Int` | Página de resultados (máximo `size = 20`). |

---

## 2. Validador de Filtros (`domain/validation/ReportFilterValidator.kt`)

Encapsula la validación en español del borrador de filtros antes de emitir llamadas HTTP:

- **Formato UI permitido en fechas (`fromDateInput` / `toDateInput`):** vacío/en blanco (`null`) o patrón estricto `dd/mm/aaaa` verificado contra calendario gregoriano real mediante `kotlinx.datetime.LocalDate` (ej. rechaza `31/02/2026` o `2026-11-14`).
- **Consistencia temporal:** si ambas fechas son válidas y `fromDate > toDate`, asigna `toDateError = "La fecha final no puede ser anterior a la fecha inicial."`.
- **Conversión:** si la validación es exitosa, transforma `dd/mm/aaaa` a `YYYY-MM-DD` dentro de `ReportFilterQuery`.

---

## 3. Contrato de Repositorio (`domain/repository/ReportRepository.kt`)

- `suspend fun getSummary(filter: ReportFilterQuery): Result<CoordinationReportSummary>`
- `suspend fun getRequests(filter: ReportFilterQuery, page: Int, size: Int = 20): Result<CoordinationReportRequestsPage>`

---

## 4. Estado UI Inmutable (`viewmodel/reports/CoordinationReportsUiState.kt`)

```kotlin
data class ReportFilterDraft(
    val fromDateInput: String = "",
    val toDateInput: String = "",
    val selectedStatus: RequestStatus? = null,
    val selectedCategory: RequestCategory? = null,
    val selectedPriority: RequestPriority? = null,
    val selectedAssigneeId: String? = null,
    val fromDateError: String? = null,
    val toDateError: String? = null,
)

data class CoordinationReportsUiState(
    val isLoadingSummary: Boolean = false,
    val isLoadingRequests: Boolean = false,
    val isLoadingAgents: Boolean = false,
    val summary: CoordinationReportSummaryUi? = null,
    val requestsPage: CoordinationReportRequestsPageUi? = null,
    val availableAgents: List<StaffMember> = emptyList(),
    val draftFilter: ReportFilterDraft = ReportFilterDraft(),
    val appliedFilter: ReportFilterQuery = ReportFilterQuery(),
    val currentPage: Int = 1,
    val errorMessage: String? = null,
)
```

---

## 5. Modelos de Presentación en Español (`ReportUiMapper.kt`)

| Modelo UI | Campos Formateados | Reglas de Formateo (`America/Bogota`) |
| :--- | :--- | :--- |
| **`BreakdownRowUi`** | `label: String`, `count: Long`, `ratio: Float` | Proporción `count.toFloat() / totalRequests.coerceAtLeast(1L)`; etiquetas en español para los 6 estados, 4 categorías y 3 prioridades + `"Sin prioridad"`. |
| **`AssigneeBreakdownRowUi`** | `assigneeName: String`, `totalAssigned: Long`, `overdueAssigned: Long`, `ratio: Float` | Incluye agentes ordenados por el backend más entrada `"Sin responsable"` (`unassignedCount`). |
| **`CoordinationReportSummaryUi`** | `generatedAtFormatted: String`, `totalRequests: Long`, `overdueCount: Long`, `resolvedWithinSlaCount: Long`, `resolvedOutsideSlaCount: Long`, `statusRows`, `categoryRows`, `priorityRows`, `assigneeRows`, `unassignedCount: Long` | `generatedAtFormatted` en formato `dd/mm/aaaa hh:mm`. |
| **`CoordinationReportRequestItemUi`** | `code`, `subject`, `categoryLabel`, `status`, `statusLabel`, `priorityLabel`, `requesterName`, `assignedToName`, `dueAtFormatted: String?`, `isOverdue: Boolean`, `respondedAtFormatted: String?`, `slaResultLabel: String?`, `createdAtFormatted: String` | Prioridad `"Alta (24 h)"` / `"Media (72 h)"` / `"Baja (120 h)"` / `"Sin asignar"`; `slaResultLabel` = `"Dentro del plazo"` / `"Fuera del plazo"` cuando `respondedWithinSla != null`; todas las fechas `dd/mm/aaaa hh:mm` vía `DateFormatter`. |
