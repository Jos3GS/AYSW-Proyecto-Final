# Investigación Técnica y Decisiones Arquitectónicas: Consolidado y Reportes de Coordinación (Backend)

**Feature:** `005-coordination-reports`
**Subproyecto:** Backend (Kotlin + Ktor + Exposed DSL + PostgreSQL)
**Spec Asociada:** `specs/005-coordination-reports/spec.md`
**Versión:** 1.0.0
**Fecha:** 2026-11-14
**Estado:** Aprobado

---

## 1. Decisiones Arquitectónicas y Análisis de Alternativas

### D1. Agregación SQL en PostgreSQL vs. Agrupación en Memoria JVM (`RNF-07`)
- **Contexto:** `RF-06` exige totales por estado, categoría, prioridad, responsable, vencidas activas y cumplimiento SLA; `RNF-07` exige responder en `< 2 segundos` con al menos `1.000` solicitudes.
- **Alternativas evaluadas:**
  1. *Carga de entidades en memoria JVM y agrupación con `groupBy` de Kotlin:* Simple de programar, pero degrada memoria heap y tiempo de serialización/deserialización JDBC al crecer el histórico.
  2. *Consultas agregadas SQL directamente en Exposed 1.6.x DSL sobre PostgreSQL:* Ejecuta `COUNT`, `GROUP BY` y predicados condicionales en el motor relacional y transfiere únicamente los contadores finales (~20 filas agregadas en total).
- **Decisión:** Adoptar **agregaciones SQL puras en Exposed DSL**. Adicionalmente, ejecutar las lecturas del resumen consolidado dentro de una transacción con nivel de aislamiento **`TRANSACTION_REPEATABLE_READ`** (`readOnly = true`) para garantizar que `totalRequests` coincida matemáticamente con la suma de cada desglose aunque ocurran inserciones o transiciones concurrentes.

### D2. Patrón GoF Builder y Paridad de Filtros entre Resumen y Listado
- **Contexto:** Tanto la Constitución Backend como `version_map.md` indican el uso del patrón **Builder** para construir los filtros dinámicos de `005-coordination-reports`, manteniendo coherencia estricta entre el resumen consolidado y el listado general paginado.
- **Decisión:**
  - Crear `ReportFilterBuilder` en `domain/builder/ReportFilterBuilder.kt` para validar acumulativamente (`createdFrom`, `createdTo`, `status`, `category`, `priority`, `assignedToId`) y producir un criterio inmutable `ReportFilterCriteria`.
  - Crear un único constructor de predicado SQL (`ServiceRequestsTable.toFilterOp(criteria)`) compartido por las consultas del resumen y la consulta paginada, garantizando que `summary.totalRequests == pagedRequests.totalItems` para cualquier combinación de filtros.

### D3. Manejo de Rango de Fechas en `America/Bogota` con Intervalo Semiabierto
- **Contexto:** Las columnas `TIMESTAMPTZ` en PostgreSQL almacenan instantes absolutos, pero un coordinador filtra por fechas calendario colombianas (`YYYY-MM-DD`, `America/Bogota`, UTC-05:00).
- **Decisión:** Interpretar cada fecha en `ZoneId.of("America/Bogota")` usando un rango semiabierto **`[inicioDelDiaFrom, inicioDelDiaSiguienteTo)`**:
  - `createdFrom = 2026-11-14` → `created_at >= 2026-11-14T00:00:00-05:00`
  - `createdTo = 2026-11-14` → `created_at < 2026-11-15T00:00:00-05:00`
- **Beneficio:** Evita pérdida de solicitudes en el último segundo del día (`23:59:59.999999-05:00`), resuelve diferencias de día frente a UTC (`19:00..23:59` Bogotá) y aprovecha ordenamiento por índice B-Tree sobre `created_at`.

### D4. Coherencia de Vencimiento (`overdueCount`) y Cumplimiento SLA
- **Contexto:** El vencimiento de SLA es una propiedad dinámica que depende del reloj actual (`TimeProvider.now()`), no de un flag estático en base de datos.
- **Decisión:** Pasar `now = timeProvider.now()` desde `ReportService` al repositorio para evaluar en SQL la misma regla exacta de `ServiceRequest.isOverdue(now)`:
  - Vencida activa: `status IN ('EN_REVISION', 'ESCALADA') AND due_at IS NOT NULL AND due_at < now`.
  - Cumplimiento de resolución: `responded_within_sla = TRUE` (dentro de plazo) vs `responded_within_sla = FALSE` (fuera de plazo).
- **Ventaja:** Garantiza cero discrepancias entre las métricas del tablero, las tarjetas del listado y el badge `"Vencida"` mostrado en el detalle.

### D5. Migración Incremental `V5` para Rendimiento Sostenido (`RNF-07`)
- **Contexto:** Las migraciones `V3` y `V4` crearon índices orientados a `requester_id`, `assigned_to` y `RESUELTA` (`idx_sr_autoclose`), pero no cubren el orden global `created_at DESC, id DESC` ni combinaciones multi-filtro globales.
- **Decisión:** Introducir la migración `V5__add_coordination_reports_indexes.sql` con dos índices no bloqueantes en diseño estándar:
  1. `idx_sr_created_desc ON service_requests (created_at DESC, id DESC)`: acelera el listado general sin filtro y rangos de fechas.
  2. `idx_sr_report_filters ON service_requests (status, category, priority, assigned_to, created_at DESC)`: optimiza filtros combinados y agregaciones `GROUP BY`.

### D6. Diseño de Rutas y Reutilización del Detalle de `004`
- **Contexto:** `004-attention-lifecycle` ya expone `GET /api/v1/attention/requests/{code}` autorizado para `COORDINADOR` con el detalle completo e historial inmutable.
- **Decisión:** Exponer bajo `/api/v1/reports` dos rutas especializadas de lectura exclusiva para `COORDINADOR`:
  - `GET /api/v1/reports/summary` (resumen consolidado filtrado).
  - `GET /api/v1/reports/requests` (listado general paginado filtrado, máx. `20/pág`).
  - Reutilizar `GET /api/v1/attention/requests/{code}` para consultar el detalle individual e historial completo, evitando duplicar DTOs o endpoints equivalentes.

---

## 2. Riesgos Identificados y Mitigaciones

| Riesgo | Impacto | Mitigación Planificada |
| :--- | :--- | :--- |
| Conteos incompletos cuando un estado, categoría o prioridad tiene 0 solicitudes en el filtro | Gráficos o tarjetas omiten dimensiones (`RF-06`) | El repositorio/servicio inicializa en `0` todas las claves de `RequestStatus` (6), `RequestCategory` (4) y `RequestPriority` (3) al armar el modelo de dominio. |
| Desfase entre la suma de `byAssignee` y `totalRequests` por solicitudes `ABIERTA`/`CANCELADA` | Inconsistencia numérica visible para Coordinación | Devolver explícitamente `unassignedCount` (además de `byAssignee`) y `unassignedPriorityCount` (además de `byPriority`). |
| Consultas lentas con > 1.000 registros bajo múltiples filtros | Incumplimiento de `RNF-07` (`< 2s`) | Migración `V5`, agregaciones SQL puras y prueba de rendimiento con 1.000 solicitudes reales en Testcontainers PostgreSQL. |
| Acceso indebido de `PERSONAL_ATENCION` o `SOLICITANTE` a reportes globales | Fuga de información gerencial | Guardia RBAC obligatoria `requireRoles(UserRole.COORDINADOR)` al inicio del bloque de rutas `/api/v1/reports` y pruebas automatizadas `403`. |
