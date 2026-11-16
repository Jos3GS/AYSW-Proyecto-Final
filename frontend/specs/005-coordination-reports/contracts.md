# Contratos de APIs, DTOs y Navegación: Consolidado y Reportes de Coordinación (Frontend)

**Feature:** `005-coordination-reports`
**Subproyecto:** Frontend (Kotlin Multiplatform + Compose Multiplatform)
**Spec Asociada:** `specs/005-coordination-reports/spec.md`
**Versión:** 1.0.0
**Fecha:** 2026-11-14
**Estado:** Aprobado

---

## 1. Endpoints Consumidos y Reglas de Cliente HTTP

| Operación | Método y Ruta | Rol Requerido | Uso en Frontend |
| :--- | :--- | :---: | :--- |
| **Resumen consolidado** | `GET /api/v1/reports/summary` | `COORDINADOR` | Tablero KPI y desgloses por estado, categoría, prioridad y responsable. |
| **Listado general paginado** | `GET /api/v1/reports/requests` | `COORDINADOR` | Tabla/lista general paginada (`size = 20`) filtrada. |
| **Catálogo de responsables** | `GET /api/v1/users/staff?page={n}&size=20&role=PERSONAL_ATENCION` | `COORDINADOR` | Reutiliza `StaffRepository` acumulando todas las páginas hasta `totalPages` para poblar el selector de responsable. |
| **Detalle completo e historial** | `GET /api/v1/attention/requests/{code}` | `COORDINADOR` | Reutilizado mediante navegación directa a `AttentionDetailRoute(code)` (`004-attention-lifecycle`). |

> **Query Parameters emitidos en `/api/v1/reports/summary` y `/api/v1/reports/requests`:**
> - `createdFrom`: `YYYY-MM-DD` *(omitido si es `null`)*
> - `createdTo`: `YYYY-MM-DD` *(omitido si es `null`)*
> - `status`: `ABIERTA | EN_REVISION | ESCALADA | RESUELTA | CERRADA | CANCELADA` *(omitido si es `null`)*
> - `category`: `TRAMITE | ORIENTACION | ATENCION_ADMINISTRATIVA | REQUERIMIENTO_SERVICIO` *(omitido si es `null`)*
> - `priority`: `ALTA | MEDIA | BAJA` *(omitido si es `null`)*
> - `assignedToId`: UUID en texto *(omitido si es `null`)*
> - Adicionales únicamente en `/requests`: `page` (`>= 1`) y `size` (`20`).

---

## 2. Contratos DTO Estrictos (`data/dto/reports/ReportDtos.kt`)

Todos los DTOs se configuran con `@Serializable` compatible con `Json { ignoreUnknownKeys = false }`:

```kotlin
@Serializable
data class AssigneeMetricDto(
    val assigneeId: String,
    val assigneeName: String,
    val totalAssigned: Long,
    val overdueAssigned: Long,
)

@Serializable
data class CoordinationReportSummaryDto(
    val generatedAt: String,
    val totalRequests: Long,
    val byStatus: Map<String, Long>,
    val byCategory: Map<String, Long>,
    val byPriority: Map<String, Long>,
    val unassignedPriorityCount: Long,
    val byAssignee: List<AssigneeMetricDto>,
    val unassignedCount: Long,
    val overdueCount: Long,
    val resolvedWithinSlaCount: Long,
    val resolvedOutsideSlaCount: Long,
)

@Serializable
data class CoordinationReportRequestItemDto(
    val id: String,
    val code: String,
    val subject: String,
    val category: String,
    val status: String,
    val priority: String? = null,
    val requesterId: String,
    val requesterName: String,
    val assignedToId: String? = null,
    val assignedToName: String? = null,
    val dueAt: String? = null,
    val isOverdue: Boolean,
    val respondedAt: String? = null,
    val respondedWithinSla: Boolean? = null,
    val closedAt: String? = null,
    val createdAt: String,
)

@Serializable
data class PagedCoordinationReportRequestsResponseDto(
    val items: List<CoordinationReportRequestItemDto>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
    val totalPages: Int,
)
```

---

## 3. Contrato UDF del `CoordinationReportsViewModel`

| Intención / Evento UI | Comportamiento Esperado |
| :--- | :--- |
| `onFromDateChanged(value)` / `onToDateChanged(value)` | Actualiza `draftFilter` y limpia el error inline del campo modificado sin emitir petición HTTP. |
| `onStatusFilterChanged(status)` / `onCategoryFilterChanged(category)` / `onPriorityFilterChanged(priority)` / `onAssigneeFilterChanged(assigneeId)` | Actualiza el selector correspondiente dentro de `draftFilter`. |
| `onApplyFilters()` | Valida `draftFilter` con `ReportFilterValidator`. Si falla, actualiza `fromDateError` / `toDateError` sin llamar red. Si es válido, actualiza `appliedFilter`, fija `currentPage = 1` y lanza en paralelo `getSummary(appliedFilter)` + `getRequests(appliedFilter, 1, 20)`. |
| `onClearFilters()` | Restaura `draftFilter = ReportFilterDraft()`, `appliedFilter = ReportFilterQuery()`, `currentPage = 1` y recarga en paralelo resumen y página 1 globales. |
| `onPageSelected(page)` | Actualiza `currentPage = page` y ejecuta únicamente `getRequests(appliedFilter, page, 20)` preservando el `summary` vigente. |
| `onRetry()` | Reintenta carga de resumen/listado según `appliedFilter` y recarga `availableAgents` si la lista está vacía. |

---

## 4. Contrato de Rutas Tipadas y Manejo de Errores

### Navegación (`Routes.kt` & `AppNavHost.kt`)
- **Nueva ruta:** `@Serializable data object CoordinationReportsRoute`
- **Protección RBAC:** `RequireRole(UserRole.COORDINADOR)`
- **Navegación entrante:** Desde `CoordinationPendingScreen` mediante acción visible `"Reportes y consolidado"`.
- **Navegación saliente:** Al pulsar cualquier fila del listado general en `CoordinationReportsScreen`, navega a `AttentionDetailRoute(code)`.

### Manejo de Errores HTTP (`ErrorMapper`)
- `400 Bad Request`: muestra `error.message` / primer error de campo en español dentro de la barra de alertas sin perder los filtros digitados.
- `401 Unauthorized`: delega al manejador global de sesión expirada para redirigir a login.
- `403 Forbidden`: muestra acceso denegado en español.
- Error de red / timeout: habilita botón `"Reintentar"` sin limpiar filtros ni resultados previos si existían.
