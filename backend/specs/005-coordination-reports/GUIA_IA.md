# Guía de Instrucciones para la IA (Prompt Maestro y Correcciones) — `005-coordination-reports` (Backend)

**Feature:** `005-coordination-reports`
**Subproyecto:** Backend (Kotlin 2.4.x + Ktor 3.6.x + Exposed 1.6.x DSL + PostgreSQL 18)
**Versión:** 1.0.0
**Fecha:** 2026-11-14
**Estado:** Aprobado

> **Propósito de este archivo:** Contiene el **Prompt Maestro** para implementar la épica `005-coordination-reports` en el Backend (tablero consolidado de métricas gerenciales, filtros dinámicos combinables en `America/Bogota` con Patrón Builder y listado general paginado en `< 2s`) junto con el registro de correcciones críticas.

---

Actúa como un **Ingeniero Backend Senior experto en Kotlin 2.4.x, Exposed 1.6.x DSL, SQL de Agregación en PostgreSQL 18 y Patrón GoF Builder**, trabajando bajo **Spec-Driven Development (SDD)** y TDD.

Tu misión es implementar la épica **`005-coordination-reports`** del Backend de **ITM Service Desk** siguiendo exactamente `specs/005-coordination-reports/tasks.md` (`T5-BE-01` a `T5-BE-10`).

---

### VERIFICACIÓN PREVIA DE CONTINUIDAD

1. Confirma que `004-attention-lifecycle` pasa `./gradlew ktlintCheck check koverVerify`.
2. Lee `specs/005-coordination-reports/{spec,research,data-model,contracts,plan,checklist,tasks}.md`.

---

### REGLAS INNEGOCIABLES (`005-coordination-reports`)

1. **Migración `V5__add_coordination_reports_indexes.sql`:**
   - Crea únicamente los índices `idx_sr_created_desc` (`created_at DESC, id DESC`) e `idx_sr_report_filters` (`status, category, priority, assigned_to, created_at DESC`) con `CREATE INDEX IF NOT EXISTS`, sin alterar tablas ni columnas existentes.
2. **Agregaciones SQL Puras y Consistencia `REPEATABLE READ` (`RNF-07`):**
   - **PROHIBIDO** cargar todas las filas de `service_requests` a memoria Kotlin para agruparlas con `groupBy`. Todas las métricas (`totalRequests`, `byStatus`, `byCategory`, `byPriority`, `unassignedPriorityCount`, `byAssignee`, `unassignedCount`, `overdueCount`, `resolvedWithinSlaCount`, `resolvedOutsideSlaCount`) deben calcularse en **SQL/Exposed DSL** en PostgreSQL.
   - Ejecuta `ExposedReportRepository.getSummary` dentro de una transacción con aislamiento **`Connection.TRANSACTION_REPEATABLE_READ`** (`readOnly = true`) garantizando consistencia matemática entre todos los totales.
   - Comparte una única función `ServiceRequestsTable.toFilterOp(criteria)` entre `getSummary` y `findRequests` para asegurar `summary.totalRequests == pagedRequests.totalItems`.
3. **Patrón GoF Builder (`ReportFilterBuilder`) e Intervalo Semiabierto `America/Bogota`:**
   - Valida acumulativamente en español (`ValidationException` `400`): `createdFrom`/`createdTo` en formato ISO `YYYY-MM-DD`, interpretados en `ZoneId.of("America/Bogota")` como **`[startOfDay(createdFrom), startOfDay(createdTo + 1 día))`**, verificando `createdFrom <= createdTo`.
   - Valida `status`, `category`, `priority`, `assignedToId` (UUID), `page >= 1` y `size in 1..20` (default `20`).
4. **Completitud Matemática de Dimensiones y Reutilización con `004`:**
   - Inicializa siempre en `0L` las 6 claves de `RequestStatus`, 4 claves de `RequestCategory` y 3 claves de `RequestPriority` en `CoordinationReportSummary`.
   - Incluye explícitamente `unassignedPriorityCount` y `unassignedCount` para que todas las sumas cuadren con `totalRequests`.
   - Evalúa `overdueCount` en SQL con `status IN ('EN_REVISION', 'ESCALADA') AND due_at IS NOT NULL AND due_at < now` usando `TimeProvider.now()`.
   - Reutiliza `GET /api/v1/attention/requests/{code}` (`004`) para el detalle completo e historial; **PROHIBIDO** duplicar un endpoint de detalle bajo `/api/v1/reports/`.
5. **RBAC Estricto y Rendimiento (`1.000` Solicitudes `< 2s`):**
   - Monta `GET /api/v1/reports/summary` y `GET /api/v1/reports/requests` bajo `authenticate("auth-jwt")` + `withRole(UserRole.COORDINADOR)` (`401` sin JWT / revocado, `403` para `SOLICITANTE` y `PERSONAL_ATENCION`).
   - Verifica con Testcontainers PostgreSQL 18 que con `1.000` solicitudes sembradas ambos endpoints responden en **`< 2000 ms`**, y cierra con `./gradlew ktlintCheck check koverVerify` ($\ge 90\%$).

---

### METODOLOGÍA DE TRABAJO

1. Verifica que `004-attention-lifecycle` pasa al 100%.
2. Avanza en el orden estricto `T5-BE-01` -> `T5-BE-10` de `specs/005-coordination-reports/tasks.md` bajo TDD.

Responde únicamente:
*"ENTENDIDO. Verificando el estado de `004-attention-lifecycle` antes de iniciar la Fase 1 de `005-coordination-reports`."*

---

## Registro de Correcciones Críticas Frente a Desviaciones Comunes de la IA

| Desviación habitual de la IA | Por qué está mal según la Constitución / Spec | Corrección exigida a la IA |
| :--- | :--- | :--- |
| **Traer `service_requests.selectAll()` a memoria y usar Kotlin `.groupBy { ... }`** | Destruye el rendimiento (`RNF-07`) y consumo de heap al superar 1.000+ filas. | *"Reemplaza la agrupación en Kotlin por consultas agregadas SQL (`COUNT`, `GROUP BY`, `FILTER`/`CASE WHEN`) en `ExposedReportRepository` dentro de `TRANSACTION_REPEATABLE_READ`."* |
| **Omitir estados, categorías o prioridades con conteo `0` en `byStatus`/`byCategory`/`byPriority`** | Rompe los contratos `contracts.md` (`byStatus` debe traer siempre 6 claves, `byCategory` 4 y `byPriority` 3). | *"Inicializa los mapas con `entries.associateWith { 0L }` y sobrescribe con los conteos retornados por PostgreSQL."* |
| **Convertir fechas `createdFrom`/`createdTo` en UTC o usar `<= 23:59:59`** | Excluye solicitudes creadas entre `19:00` y `23:59` hora colombiana o en el último segundo del día. | *"Convierte `YYYY-MM-DD` usando `ZoneId.of(\"America/Bogota\")` con intervalo semiabierto `>= startOfDay(from)` y `< startOfNextDay(to)`."* |
| **Crear un nuevo endpoint `GET /api/v1/reports/requests/{code}` duplicando lógica de `004`** | Viola DRY y la arquitectura aprobada en `research.md`. | *"Elimina el endpoint duplicado; el detalle completo para Coordinación reutiliza `GET /api/v1/attention/requests/{code}` de `004-attention-lifecycle`."* |
| **Permitir acceso al rol `PERSONAL_ATENCION` a `/api/v1/reports/*`** | Viola la matriz RBAC de `HU-06` y `contracts.md`. | *"Protege ambos endpoints exclusivamente con `withRole(UserRole.COORDINADOR)` y verifica `403 Forbidden` para los demás roles en `ReportRoutingTest`."* |
