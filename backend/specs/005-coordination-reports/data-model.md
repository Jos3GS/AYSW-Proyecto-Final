# Modelo de Datos y Entidades: Consolidado y Reportes de Coordinación (Backend)

**Feature:** `005-coordination-reports`
**Subproyecto:** Backend (Kotlin + Ktor + Exposed DSL + PostgreSQL)
**Spec Asociada:** `specs/005-coordination-reports/spec.md`
**Versión:** 1.0.0
**Fecha:** 2026-11-14
**Estado:** Aprobado

---

## 1. Esquema Relacional y Migración Flyway `V5`

Las tablas `users` (`V2`), `service_requests` (`V3` + `V4`) y `request_history` (`V3`) ya contienen todas las columnas de negocio requeridas (`status`, `category`, `priority`, `assigned_to`, `due_at`, `responded_at`, `responded_within_sla`, `closed_at`, `created_at`).
Por lo tanto, `005-coordination-reports` no altera columnas ni constraints existentes y añade únicamente una migración incremental de índices para garantizar tiempos de respuesta `< 2 segundos` (`RNF-07`):

### Migración `V5__add_coordination_reports_indexes.sql`
```sql
CREATE INDEX IF NOT EXISTS idx_sr_created_desc
    ON service_requests (created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_sr_report_filters
    ON service_requests (status, category, priority, assigned_to, created_at DESC);
```

---

## 2. Modelos Puros de Dominio (`domain/model/`)

| Modelo | Campos y Tipos Kotlin | Propósito |
| :--- | :--- | :--- |
| **`ReportFilterCriteria`** | `createdFromInclusive: OffsetDateTime?`, `createdToExclusive: OffsetDateTime?`, `status: RequestStatus?`, `category: RequestCategory?`, `priority: RequestPriority?`, `assignedToId: UUID?` | Criterio inmutable de filtrado compartido entre resumen consolidado y listado general. |
| **`CoordinationReportPageQuery`** | `criteria: ReportFilterCriteria`, `page: Int`, `size: Int` | Parámetros validados para consulta paginada (`page >= 1`, `size in 1..20`). |
| **`AssigneeMetric`** | `assigneeId: UUID`, `assigneeName: String`, `totalAssigned: Long`, `overdueAssigned: Long` | Carga asignada y vencidas activas por cada responsable (`PERSONAL_ATENCION`). |
| **`CoordinationReportSummary`** | `totalRequests: Long`, `byStatus: Map<RequestStatus, Long>`, `byCategory: Map<RequestCategory, Long>`, `byPriority: Map<RequestPriority, Long>`, `unassignedPriorityCount: Long`, `byAssignee: List<AssigneeMetric>`, `unassignedCount: Long`, `overdueCount: Long`, `resolvedWithinSlaCount: Long`, `resolvedOutsideSlaCount: Long` | Tablero consolidado de métricas calculadas en base de datos para `now = timeProvider.now()`. |
| **`CoordinationReportRequestItem`** | `id: UUID`, `code: String`, `subject: String`, `category: RequestCategory`, `status: RequestStatus`, `priority: RequestPriority?`, `requesterId: UUID`, `requesterName: String`, `assignedToId: UUID?`, `assignedToName: String?`, `dueAt: OffsetDateTime?`, `isOverdue: Boolean`, `respondedAt: OffsetDateTime?`, `respondedWithinSla: Boolean?`, `closedAt: OffsetDateTime?`, `createdAt: OffsetDateTime` | Ítem del listado general paginado con visibilidad completa del estado, responsable y SLA. |

---

## 3. Patrón GoF Builder (`domain/builder/ReportFilterBuilder.kt`)

`ReportFilterBuilder` encapsula el parseo, validación acumulativa y normalización horaria (`America/Bogota`) de los parámetros de consulta antes de alcanzar la capa de repositorio:

1. **Parámetros admitidos:**
   - `createdFrom(raw: String?)`: formato estricto `YYYY-MM-DD`; se convierte al inicio del día en `ZoneId.of("America/Bogota")` (`00:00:00-05:00`).
   - `createdTo(raw: String?)`: formato estricto `YYYY-MM-DD`; se convierte al inicio del día siguiente en `ZoneId.of("America/Bogota")` (`plusDays(1)` a las `00:00:00-05:00`) para predicado exclusivo `< createdToExclusive`.
   - `status(raw: String?)`: debe pertenecer a `RequestStatus`.
   - `category(raw: String?)`: debe pertenecer a `RequestCategory`.
   - `priority(raw: String?)`: debe pertenecer a `RequestPriority`.
   - `assignedToId(raw: String?)`: debe ser un `UUID` sintácticamente válido.
   - `page(raw: Int?)` / `size(raw: Int?)`: usados en `buildPageQuery()`, con valores por defecto `page = 1`, `size = 20` y restricción `page >= 1`, `size in 1..20`.
2. **Validación cruzada:** Si `createdFrom` y `createdTo` son válidos y `createdFrom > createdTo`, acumula error en campo `"createdTo"` (`"La fecha final no puede ser anterior a la fecha inicial."`).
3. **Salida:** `buildCriteria(): ReportFilterCriteria` y `buildPageQuery(): CoordinationReportPageQuery`; ante cualquier error acumulado lanza `ValidationException` (`HTTP 400`).

---

## 4. Puerto de Persistencia y Traducción SQL (`domain/repository/ReportRepository.kt`)

### Contrato de Repositorio
- `fun getSummary(criteria: ReportFilterCriteria, now: OffsetDateTime): CoordinationReportSummary`
- `fun findRequests(query: CoordinationReportPageQuery, now: OffsetDateTime): PagedResult<CoordinationReportRequestItem>`

### Predicado Dinámico Compartido (`ServiceRequestsTable.toFilterOp`)
Ambas operaciones reutilizan la misma función de construcción de predicados Exposed 1.6.x DSL sobre `ServiceRequestsTable`:
- `createdFromInclusive != null` $\rightarrow$ `ServiceRequestsTable.createdAt greaterEq criteria.createdFromInclusive`
- `createdToExclusive != null` $\rightarrow$ `ServiceRequestsTable.createdAt less criteria.createdToExclusive`
- `status != null` $\rightarrow$ `ServiceRequestsTable.status eq criteria.status.name`
- `category != null` $\rightarrow$ `ServiceRequestsTable.category eq criteria.category.name`
- `priority != null` $\rightarrow$ `ServiceRequestsTable.priority eq criteria.priority.name`
- `assignedToId != null` $\rightarrow$ `ServiceRequestsTable.assignedTo eq criteria.assignedToId`

### Invariantes Matemáticos del Consolidado (`getSummary`)
Dentro de una única transacción `TRANSACTION_REPEATABLE_READ` (`readOnly = true`), todas las dimensiones del resumen deben cumplir:
- $\sum_{s \in \text{RequestStatus}} \text{byStatus}[s] = \text{totalRequests}$ (las 6 claves siempre presentes).
- $\sum_{c \in \text{RequestCategory}} \text{byCategory}[c] = \text{totalRequests}$ (las 4 claves siempre presentes).
- $\sum_{p \in \text{RequestPriority}} \text{byPriority}[p] + \text{unassignedPriorityCount} = \text{totalRequests}$ (las 3 prioridades siempre presentes).
- $\sum_{a \in \text{byAssignee}} a.\text{totalAssigned} + \text{unassignedCount} = \text{totalRequests}$.
- $\sum_{a \in \text{byAssignee}} a.\text{overdueAssigned} = \text{overdueCount}$ (toda solicitud en `EN_REVISION` o `ESCALADA` posee `assigned_to IS NOT NULL`).
- `byAssignee` ordenado de forma determinística por `totalAssigned DESC, assigneeName ASC`.
