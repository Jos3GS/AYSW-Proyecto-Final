# Plan de Tareas (Tasks): Consolidado y Reportes de Coordinación (Backend)

**Feature:** `005-coordination-reports`
**Subproyecto:** Backend (Kotlin + Ktor + Exposed DSL + PostgreSQL)
**Versión:** 1.0.0
**Fecha:** 2026-11-14
**Estado:** Aprobado

---

## Fase 1: Migración Base de Datos e Infraestructura

- [ ] **T5-BE-01 — Migración Flyway `V5__add_coordination_reports_indexes.sql`**
  - **Archivo:** `src/main/resources/db/migration/V5__add_coordination_reports_indexes.sql`
  - **Detalle:** Crear los índices `idx_sr_created_desc` (`created_at DESC, id DESC`) e `idx_sr_report_filters` (`status, category, priority, assigned_to, created_at DESC`) usando `CREATE INDEX IF NOT EXISTS`.
  - **Criterio de verificación:** Flyway aplica `V5` limpiamente sobre PostgreSQL 18 en arranque e integración Testcontainers sin alterar migraciones `V1..V4`.

---

## Fase 2: Capa de Dominio y Patrón GoF Builder

- [ ] **T5-BE-02 — Modelos de Dominio de Reportes (`ReportModels.kt`)**
  - **Archivo:** `src/main/kotlin/.../domain/model/ReportModels.kt`
  - **Detalle:** Definir `ReportFilterCriteria`, `CoordinationReportPageQuery`, `AssigneeMetric`, `CoordinationReportSummary` y `CoordinationReportRequestItem`.
  - **Criterio de verificación:** Compila en `domain/` sin dependencias de Ktor ni Exposed.

- [ ] **T5-BE-03 — `ReportFilterBuilder` (Patrón GoF Builder)**
  - **Archivos:** `src/main/kotlin/.../domain/builder/ReportFilterBuilder.kt`, `src/test/kotlin/.../domain/builder/ReportFilterBuilderTest.kt`
  - **Detalle:** Implementar validación acumulativa en español para `createdFrom`/`createdTo` (`YYYY-MM-DD` convertidos en `America/Bogota` al rango `[startOfDay, nextStartOfDay)`), `createdFrom <= createdTo`, `status`, `category`, `priority`, `assignedToId` (UUID), `page >= 1` y `size in 1..20` (default `20`), lanzando `ValidationException` ante cualquier error.
  - **Criterio de verificación:** Tests unitarios cubren límites horarios `23:30-05:00`, fechas invertidas, enums inválidos, UUID inválido y rangos de paginación.

- [ ] **T5-BE-04 — Puerto `ReportRepository`**
  - **Archivo:** `src/main/kotlin/.../domain/repository/ReportRepository.kt`
  - **Detalle:** Definir los métodos `getSummary(criteria: ReportFilterCriteria, now: OffsetDateTime): CoordinationReportSummary` y `findRequests(query: CoordinationReportPageQuery, now: OffsetDateTime): PagedResult<CoordinationReportRequestItem>`.
  - **Criterio de verificación:** Contrato tipado sin referencias a SQL ni DTOs HTTP.

---

## Fase 3: Persistencia SQL (Exposed DSL) y Capa de Servicio

- [ ] **T5-BE-05 — `ExposedReportRepository` con Agregaciones SQL y `REPEATABLE READ`**
  - **Archivo:** `src/main/kotlin/.../repository/ExposedReportRepository.kt`
  - **Detalle:**
    - Extraer helper compartido `ServiceRequestsTable.toFilterOp(criteria)` usado por `getSummary` y `findRequests`.
    - Ejecutar `getSummary` dentro de transacción `Connection.TRANSACTION_REPEATABLE_READ` (`readOnly = true`) calculando en SQL: `totalRequests`, conteos agrupados por `status`, `category`, `priority` (separando `NULL`), por `assigned_to` (`LEFT JOIN`/`INNER JOIN` con `UsersTable` calculando `totalAssigned` y `overdueAssigned`, más `unassignedCount`), `overdueCount` (`status IN ('EN_REVISION', 'ESCALADA') AND due_at IS NOT NULL AND due_at < now`), `resolvedWithinSlaCount` y `resolvedOutsideSlaCount`.
    - Ejecutar `findRequests` con alias de `UsersTable` (`requester` y `assignee`), orden `created_at DESC, id DESC`, `LIMIT/OFFSET` e `isOverdue = ServiceRequest.isOverdue(now)`.
  - **Criterio de verificación:** Consultas verificadas contra PostgreSQL real validando 0 fugas N+1 y paridad exacta de `totalRequests == totalItems`.

- [ ] **T5-BE-06 — `ReportService`**
  - **Archivos:** `src/main/kotlin/.../service/ReportService.kt`, `src/test/kotlin/.../service/ReportServiceTest.kt`
  - **Detalle:** Inyectar `ReportRepository` y `TimeProvider`; garantizar presencia de los 6 `RequestStatus`, 4 `RequestCategory` y 3 `RequestPriority` inicializados en `0L`; delegar validación en `ReportFilterBuilder`.
  - **Criterio de verificación:** Tests unitarios validan normalización de mapas con ceros, invariantes matemáticos e inyección determinista de `TimeProvider`.

---

## Fase 4: Capa HTTP Ktor, Serialización y Registro en `Application.kt`

- [ ] **T5-BE-07 — DTOs `@Serializable` y `ReportRouting`**
  - **Archivos:** `src/main/kotlin/.../routing/dto/reports/ReportDtos.kt`, `src/main/kotlin/.../routing/ReportRouting.kt`
  - **Detalle:** Implementar DTOs estrictos (`CoordinationReportSummaryDto`, `AssigneeMetricDto`, `CoordinationReportRequestItemDto`, `PagedCoordinationReportRequestsResponseDto`) y montar `GET /api/v1/reports/summary` y `GET /api/v1/reports/requests` protegidos bajo `withRole(UserRole.COORDINADOR)`.
  - **Criterio de verificación:** Payloads serializan exactamente según `contracts.md` con timestamps ISO-8601 en `-05:00`.

- [ ] **T5-BE-08 — Registro de Dependencias en `Application.kt`**
  - **Archivo:** `src/main/kotlin/.../Application.kt`
  - **Detalle:** Instanciar `ExposedReportRepository`, `ReportService` (con `TimeProvider`) y registrar `ReportRouting`.
  - **Criterio de verificación:** La aplicación inicia y monta las rutas `/api/v1/reports/*` sin errores.

---

## Fase 5: Pruebas de Integración, Rendimiento (`RNF-07`) y Puertas de Calidad

- [ ] **T5-BE-09 — Suite de Integración y Rendimiento (`ReportRoutingTest.kt` + Testcontainers PostgreSQL 18)**
  - **Archivo:** `src/test/kotlin/.../routing/ReportRoutingTest.kt`
  - **Detalle:**
    - Escenario consolidado global con todos los estados, categorías, prioridades, vencidas activas, `resolvedWithinSlaCount` y `resolvedOutsideSlaCount`.
    - Escenario de filtros combinados (`createdFrom`, `createdTo`, `status`, `category`, `priority`, `assignedToId`) verificando igualdad matemática `/summary` vs `/requests` e inclusión de caso frontera `23:30-05:00`.
    - Escenarios negativos: `400 Bad Request` (fechas invertidas, formato inválido, UUID inválido, `size > 20`), `401 Unauthorized` y `403 Forbidden` para `SOLICITANTE` y `PERSONAL_ATENCION`.
    - Escenario de carga **`RNF-07`**: sembrar `1.000` solicitudes y verificar respuesta `< 2000 ms` tanto para `/api/v1/reports/summary` como para `/api/v1/reports/requests?page=1&size=20`.
  - **Criterio de verificación:** Todos los tests pasan contra PostgreSQL 18 real en contenedor.

- [ ] **T5-BE-10 — Verificación Final de Calidad (Style & Coverage)**
  - **Comandos:**
    - `./gradlew ktlintCheck`
    - `./gradlew check koverVerify`
  - **Criterio de verificación:** `ktlintCheck` sin violaciones y cobertura Kover $\ge 90\%$ verificada.
