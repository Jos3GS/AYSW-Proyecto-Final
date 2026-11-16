# Especificación Funcional: Consolidado y Reportes de Coordinación (Backend)

**Feature:** `005-coordination-reports`
**Subproyecto:** Backend (Kotlin + Ktor + Exposed DSL + PostgreSQL)
**Hito del Roadmap:** `v0.3.0 (Consolidado de Coordinación)`
**Trazabilidad:** `HU-06`, `RF-06`, `RNF-03`, `RNF-06`, `RNF-07`, `RNF-10`
**Versión:** 1.0.0
**Fecha:** 2026-11-14
**Estado:** Aprobado

---

## 1. Propósito y Alcance

Esta especificación define las capacidades de **visibilidad gerencial, métricas consolidadas y consulta filtrada** del sistema **ITM Service Desk** para el rol `COORDINADOR`, permitiendo evaluar la carga operativa, el cumplimiento de SLA y el comportamiento histórico de todas las solicitudes registradas sin afectar ni mutar el ciclo de atención establecido en `003-service-requests` y `004-attention-lifecycle`.

### Dentro del Alcance
1. **Resumen consolidado de métricas** (totales por estado, categoría, prioridad, responsable, vencidas activas y cumplimiento de SLA dentro/fuera de plazo).
2. **Filtros dinámicos combinables** mediante el patrón **GoF Builder** (`ReportFilterBuilder`): rango de fechas de creación en zona horaria `America/Bogota`, estado, categoría, prioridad y responsable.
3. **Listado general paginado** (máximo `20` registros por página según `RNF-07`) consistente con los mismos filtros aplicados al resumen consolidado.
4. **Reutilización del detalle completo e historial inmutable** expuesto en `GET /api/v1/attention/requests/{code}` (`004-attention-lifecycle`) para inspección individual desde el listado general.
5. **Optimización SQL en PostgreSQL** (agregaciones en base de datos e índice dedicado si aplica) para responder en `< 2 segundos` con al menos `1.000` solicitudes (`RNF-07`).

### Fuera del Alcance (`RNF-10`)
- Mutaciones de estado, asignación o edición de solicitudes desde endpoints de reporte (operación estrictamente de solo lectura).
- Exportación a archivos binarios externos (PDF, Excel, CSV), envío por correo electrónico, WhatsApp o integraciones BI externas.

---

## 2. Actores y Control de Acceso (RBAC)

| Actor / Estado de Sesión | Acceso a Reportes de Coordinación | Comportamiento Esperado |
| :--- | :--- | :--- |
| **`COORDINADOR` autenticado** | Autorizado | Acceso completo al resumen consolidado, listado general paginado y detalle interno. |
| **`PERSONAL_ATENCION` autenticado** | Denegado | `HTTP 403 Forbidden` con mensaje estándar en español. |
| **`SOLICITANTE` autenticado** | Denegado | `HTTP 403 Forbidden` con mensaje estándar en español. |
| **No autenticado / JWT inválido o revocado** | Denegado | `HTTP 401 Unauthorized`. |

---

## 3. Requisitos Funcionales Detallados

### RF-06.1 — Resumen Consolidado de Solicitudes
Al consultar el resumen consolidado (con o sin filtros aplicados), el sistema debe calcular directamente en base de datos sobre el universo de solicitudes que cumplan los filtros:

1. **Total general (`totalRequests`):** Número total de solicitudes coincidentes.
2. **Totales por estado (`byStatus`):** Mapa o desglose garantizando la presencia de los 6 estados del sistema (`ABIERTA`, `EN_REVISION`, `ESCALADA`, `RESUELTA`, `CERRADA`, `CANCELADA`), reportando `0` cuando no existan registros en un estado.
3. **Totales por categoría (`byCategory`):** Desglose garantizando las 4 categorías institucionales (`TRAMITE`, `ORIENTACION`, `ATENCION_ADMINISTRATIVA`, `REQUERIMIENTO_SERVICIO`), reportando `0` cuando no existan registros.
4. **Totales por prioridad (`byPriority`):** Desglose garantizando las 3 prioridades (`ALTA`, `MEDIA`, `BAJA`), más el conteo explícito `unassignedPriorityCount` para solicitudes que aún no tienen prioridad asignada (`priority IS NULL`, ej. `ABIERTA` o `CANCELADA` antes de asignar).
5. **Totales por responsable (`byAssignee` + `unassignedCount`):**
   - Lista de responsables (`PERSONAL_ATENCION` que tengan solicitudes asignadas dentro del filtro o total asignado > 0) con `assigneeId`, `assigneeName`, `totalAssigned` y `overdueAssigned`.
   - Conteo explícito `unassignedCount` para solicitudes sin responsable asignado (`assigned_to IS NULL`), de modo que la suma de `totalAssigned` más `unassignedCount` iguale exactamente a `totalRequests`.
6. **Solicitudes vencidas activas (`overdueCount`):**
   - Cuenta exclusivamente solicitudes cuyo estado actual sea `EN_REVISION` o `ESCALADA`, tengan `due_at IS NOT NULL` y cumplan `due_at < now` (evaluado con `TimeProvider` en zona `America/Bogota`, coherente con `ServiceRequest.isOverdue(now)`).
   - Las solicitudes `RESUELTA`, `CERRADA`, `ABIERTA` o `CANCELADA` no incrementan `overdueCount`.
7. **Cumplimiento de SLA de resolución:**
   - `resolvedWithinSlaCount`: solicitudes respondidas con `responded_within_sla = TRUE` (estados `RESUELTA` o `CERRADA`).
   - `resolvedOutsideSlaCount`: solicitudes respondidas con `responded_within_sla = FALSE` (estados `RESUELTA` o `CERRADA`).

### RF-06.2 — Filtros Dinámicos Combinables (`ReportFilterBuilder`)
Todos los parámetros de filtro son opcionales y completamente combinables entre sí (conjunción lógica `AND`), aplicándose de manera idéntica tanto al resumen consolidado como al listado general paginado:

1. **Rango de fechas de creación (`createdFrom`, `createdTo`):**
   - Formato de fecha calendario ISO `YYYY-MM-DD` (ejemplo: `2026-11-01`).
   - Interpretación estricta en zona horaria **`America/Bogota` (`-05:00`)**:
     - `createdFrom` incluye desde las `00:00:00` del día indicado (`created_at >= startOfDayBogota`).
     - `createdTo` incluye hasta el final del día indicado en Bogotá (`created_at < startOfNextDayBogota`).
   - Si ambos se envían y `createdFrom > createdTo`, el sistema rechaza la consulta con `HTTP 400 Bad Request` (`ValidationException`).
   - Puede enviarse solo `createdFrom` (límite inferior abierto hacia adelante) o solo `createdTo` (hasta esa fecha inclusive).
2. **Estado (`status`):** Cualquiera de los 6 valores de `RequestStatus`.
3. **Categoría (`category`):** Cualquiera de los 4 valores de `RequestCategory`.
4. **Prioridad (`priority`):** Cualquiera de los 3 valores de `RequestPriority` (`ALTA`, `MEDIA`, `BAJA`).
5. **Responsable (`assignedToId`):** UUID válido correspondiente al agente responsable asignado (`assigned_to = assignedToId`).
   - Valores fuera de dominio (fecha con formato inválido, rango invertido, enum inexistente, UUID mal formado) generan `HTTP 400 Bad Request` con detalle de campo en español.

### RF-06.3 — Listado General Paginado
1. Devuelve todas las solicitudes que satisfacen los filtros activos, ordenadas de forma determinística por fecha de creación descendente y desempate estable (`created_at DESC, id DESC`).
2. Respeta estrictamente la paginación de máximo `20` elementos por página (`page >= 1`, `size in 1..20`, valor por defecto `size = 20`).
3. Cada registro incluye información suficiente para inspección gerencial directa: `id`, `code`, `subject`, `category`, `status`, `priority`, `requesterId`, `requesterName`, `assignedToId`, `assignedToName`, `dueAt`, `isOverdue`, `respondedAt`, `respondedWithinSla`, `closedAt` y `createdAt`.

### RF-06.4 — Acceso al Detalle Completo e Historial Inmutable
Desde cualquier código `SOL-AAAA-NNNNN` retornado en el listado general, el `COORDINADOR` accede al detalle completo (descripción, respuesta oficial inmutable, cálculo SLA e historial completo cronológico con actor `"Sistema"`, intervenciones y motivos internos) mediante el endpoint ya provisto en `004-attention-lifecycle`: `GET /api/v1/attention/requests/{code}`.

---

## 4. Criterios de Aceptación (Escenarios BDD)

### Escenario 1: Resumen consolidado global sin filtros (`HU-06`)
- **Dado** que existen solicitudes en estados `ABIERTA`, `EN_REVISION` (una dentro de plazo y una vencida), `ESCALADA`, `RESUELTA` (dentro de plazo), `CERRADA` (fuera de plazo) y `CANCELADA`,
- **Cuando** un `COORDINADOR` consulta el resumen consolidado sin parámetros de filtro,
- **Entonces** el sistema retorna `HTTP 200 OK` con `totalRequests` exacto, los 6 estados, 4 categorías y 3 prioridades (incluyendo ceros si alguno no tiene casos), los conteos por responsable + `unassignedCount`, `overdueCount = 1`, `resolvedWithinSlaCount = 1` y `resolvedOutsideSlaCount = 1`.

### Escenario 2: Combinación de filtros entre resumen y listado general (`RF-06`)
- **Dado** un conjunto heterogéneo de solicitudes creadas en distintas fechas, categorías y responsables,
- **Cuando** el `COORDINADOR` aplica `createdFrom`, `createdTo`, `category = REQUERIMIENTO_SERVICIO`, `priority = ALTA` y `assignedToId` tanto al resumen como al listado general (`page=1&size=20`),
- **Entonces** `totalRequests` del resumen coincide exactamente con `totalItems` del listado paginado y todos los elementos retornados cumplen todos los filtros simultáneamente.

### Escenario 3: Frontera horaria `America/Bogota` en filtro por fecha (`RNF-06`)
- **Dado** una solicitud creada a las `2026-11-14T23:30:00-05:00` (que corresponde a `2026-11-15T04:30:00Z` en UTC),
- **Cuando** el `COORDINADOR` filtra con `createdFrom=2026-11-14&createdTo=2026-11-14`,
- **Entonces** la solicitud sí aparece incluida en los resultados del `14/11/2026`.

### Escenario 4: Validación de parámetros inválidos
- **Dado** un `COORDINADOR` autenticado,
- **Cuando** envía `createdFrom=2026-11-20&createdTo=2026-11-10`, un `assignedToId` no UUID o `size=25`,
- **Entonces** el sistema responde `HTTP 400 Bad Request` detallando cada campo inválido en español.

### Escenario 5: Control de acceso estricto (`RBAC`)
- **Dado** un usuario autenticado con rol `PERSONAL_ATENCION` o `SOLICITANTE`,
- **Cuando** intenta consultar el resumen consolidado o el listado general de coordinación,
- **Entonces** el sistema rechaza la solicitud con `HTTP 403 Forbidden`.

### Escenario 6: Desempeño con volumen objetivo (`RNF-07`)
- **Dado** una base de datos PostgreSQL con al menos `1.000` solicitudes distribuidas en distintos estados y fechas,
- **Cuando** un `COORDINADOR` consulta el resumen consolidado y la primera página (`20` elementos) del listado general,
- **Entonces** ambas respuestas se completan en menos de `2 segundos`.
