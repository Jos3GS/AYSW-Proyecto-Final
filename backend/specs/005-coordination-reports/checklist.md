# Checklist de Verificación: Consolidado y Reportes de Coordinación (Backend)

**Feature:** `005-coordination-reports`
**Subproyecto:** Backend (Kotlin + Ktor + Exposed DSL + PostgreSQL)
**Versión:** 1.0.0
**Fecha:** 2026-11-14
**Estado:** Aprobado

---

## 1. Cumplimiento Constitucional y Arquitectónico

- [x] **C1.1** La arquitectura respeta estrictamente la separación en capas (`routing -> service -> domain` y `service -> repository -> domain`), sin dependencias de Ktor ni Exposed dentro de `domain/`.
- [x] **C1.2** Se utiliza exclusivamente **Exposed 1.6.x DSL** (cero DAO, cero librerías de scheduling externas, cero caché externa).
- [x] **C1.3** El patrón **GoF Builder** (`ReportFilterBuilder`) centraliza la construcción y validación acumulativa de todos los filtros dinámicos.
- [x] **C1.4** Todos los cálculos de fecha límite y vencimiento (`overdueCount`) utilizan `TimeProvider.now()` en zona horaria `America/Bogota` (`-05:00`).

---

## 2. Base de Datos, Migración `V5` y Agregaciones SQL

- [x] **C2.1** La migración `V5__add_coordination_reports_indexes.sql` crea los índices `idx_sr_created_desc` y `idx_sr_report_filters` de forma idempotente (`IF NOT EXISTS`) sin alterar tablas ni restricciones existentes.
- [x] **C2.2** Todas las métricas de `ExposedReportRepository.getSummary` (`totalRequests`, `byStatus`, `byCategory`, `byPriority`, `unassignedPriorityCount`, `byAssignee`, `unassignedCount`, `overdueCount`, `resolvedWithinSlaCount`, `resolvedOutsideSlaCount`) se calculan directamente en PostgreSQL sin cargar filas completas a memoria JVM.
- [x] **C2.3** `getSummary` ejecuta sus lecturas dentro de una única transacción con aislamiento **`TRANSACTION_REPEATABLE_READ`** (`readOnly = true`) garantizando consistencia matemática entre todos los desgloses.
- [x] **C2.4** El predicado dinámico `ServiceRequestsTable.toFilterOp(criteria)` es único y compartido por `getSummary` y `findRequests`.
- [x] **C2.5** Se cumplen los invariantes matemáticos del resumen para cualquier combinación de filtros:
  - Suma de `byStatus` (6 estados siempre presentes) == `totalRequests`.
  - Suma de `byCategory` (4 categorías siempre presentes) == `totalRequests`.
  - Suma de `byPriority` (3 prioridades siempre presentes) + `unassignedPriorityCount` == `totalRequests`.
  - Suma de `byAssignee.totalAssigned` + `unassignedCount` == `totalRequests`.
  - Suma de `byAssignee.overdueAssigned` == `overdueCount`.

---

## 3. Reglas de Filtrado, Horario Bogotá y Contratos API

- [x] **C3.1** `createdFrom` y `createdTo` aceptan únicamente formato ISO `YYYY-MM-DD` y se traducen en `America/Bogota` al intervalo semiabierto `[startOfDay(createdFrom), startOfDay(createdTo + 1 día))`, incluyendo solicitudes creadas hasta las `23:59:59.999999-05:00` del día final.
- [x] **C3.2** Cualquier parámetro inválido (`createdFrom > createdTo`, fecha mal formada, enum inválido en `status`/`category`/`priority`, `assignedToId` no UUID, `page < 1`, `size !in 1..20`) retorna `HTTP 400 Bad Request` (`ApiErrorDto` en español con detalle por campo).
- [x] **C3.3** `GET /api/v1/reports/requests` pagina por defecto con `size = 20` (máximo `20`) y orden determinístico `created_at DESC, id DESC`.
- [x] **C3.4** Todos los DTOs de respuesta (`CoordinationReportSummaryDto`, `PagedCoordinationReportRequestsResponseDto`) usan `@Serializable` compatible con `ignoreUnknownKeys = false` y fechas ISO-8601 con offset `-05:00`.
- [x] **C3.5** El acceso al detalle completo e historial inmutable desde el listado general reutiliza `GET /api/v1/attention/requests/{code}` (`004-attention-lifecycle`) sin duplicación.

---

## 4. Seguridad RBAC, Rendimiento (`RNF-07`) y Calidad

- [x] **C4.1** `ReportRouting` aplica `withRole(UserRole.COORDINADOR)` en ambos endpoints (`/api/v1/reports/summary` y `/api/v1/reports/requests`): devuelve `401` sin JWT o con token revocado, y `403` para `SOLICITANTE` y `PERSONAL_ATENCION`.
- [x] **C4.2** La prueba de integración contra Testcontainers PostgreSQL 18 con **1.000 solicitudes** sembradas demuestra que `/api/v1/reports/summary` y `/api/v1/reports/requests?page=1&size=20` responden en **< 2 segundos**.
- [x] **C4.3** Toda la suite de pruebas (`ReportFilterBuilderTest`, `ReportServiceTest`, `ReportRoutingTest`) pasa en verde.
- [x] **C4.4** `./gradlew ktlintCheck` pasa con 0 errores y `./gradlew koverVerify` supera el **90%** de cobertura.
