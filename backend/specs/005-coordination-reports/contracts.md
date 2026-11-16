# Contratos de APIs e Interfaces: Consolidado y Reportes de Coordinación (Backend)

**Feature:** `005-coordination-reports`
**Subproyecto:** Backend (Kotlin + Ktor + Exposed DSL + PostgreSQL)
**Spec Asociada:** `specs/005-coordination-reports/spec.md`
**Protocolo:** REST / JSON (`application/json; charset=UTF-8`, zona `America/Bogota` `-05:00`, ISO-8601)
**Versión:** 1.0.0
**Fecha:** 2026-11-14
**Estado:** Aprobado

---

## 1. Matriz de Autorización RBAC

Todos los endpoints de reportes requieren `Authorization: Bearer <accessToken>` válido (`exp = 1h`, `jti` no revocado en `revoked_tokens`). Todo error responde con el contrato global `ApiErrorDto`.

| Endpoint | Sin JWT | `SOLICITANTE` | `PERSONAL_ATENCION` | `COORDINADOR` |
| :--- | :---: | :---: | :---: | :---: |
| `GET /api/v1/reports/summary` | `401` | `403` | `403` | Sí (`200` / `400`) |
| `GET /api/v1/reports/requests` | `401` | `403` | `403` | Sí (`200` / `400`) |
| `GET /api/v1/attention/requests/{code}` *(reutilizado de `004`)* | `401` | `403` | Sí *(solo asignadas)* | Sí (`200` / `404`) |

---

## 2. Endpoints de Consolidado y Listado General (`/api/v1/reports/...`)

### 2.1 `GET /api/v1/reports/summary`
- **Descripción:** Retorna el tablero consolidado de métricas calculado directamente en PostgreSQL aplicando los filtros dinámicos combinables construidos con `ReportFilterBuilder`.
- **Rol:** **`COORDINADOR`**.
- **Query Parameters (todos opcionales y combinables con `AND`):**
  - `createdFrom`: fecha calendario ISO `YYYY-MM-DD` (interpretada en `America/Bogota` desde `00:00:00`).
  - `createdTo`: fecha calendario ISO `YYYY-MM-DD` (interpretada en `America/Bogota` hasta el final del día, `<= 23:59:59.999999`).
  - `status`: `ABIERTA` | `EN_REVISION` | `ESCALADA` | `RESUELTA` | `CERRADA` | `CANCELADA`.
  - `category`: `TRAMITE` | `ORIENTACION` | `ATENCION_ADMINISTRATIVA` | `REQUERIMIENTO_SERVICIO`.
  - `priority`: `ALTA` | `MEDIA` | `BAJA`.
  - `assignedToId`: UUID válido del agente responsable.

#### Response `200 OK` (`CoordinationReportSummaryDto`)
```json
{
  "generatedAt": "2026-11-14T19:30:00-05:00",
  "totalRequests": 42,
  "byStatus": {
    "ABIERTA": 5,
    "EN_REVISION": 12,
    "ESCALADA": 3,
    "RESUELTA": 10,
    "CERRADA": 9,
    "CANCELADA": 3
  },
  "byCategory": {
    "TRAMITE": 15,
    "ORIENTACION": 8,
    "ATENCION_ADMINISTRATIVA": 11,
    "REQUERIMIENTO_SERVICIO": 8
  },
  "byPriority": {
    "ALTA": 10,
    "MEDIA": 16,
    "BAJA": 8
  },
  "unassignedPriorityCount": 8,
  "byAssignee": [
    {
      "assigneeId": "a1b2c3d4-e5f6-47a8-b9c0-112233445566",
      "assigneeName": "Carlos Andrés Mejía",
      "totalAssigned": 19,
      "overdueAssigned": 2
    }
  ],
  "unassignedCount": 8,
  "overdueCount": 4,
  "resolvedWithinSlaCount": 16,
  "resolvedOutsideSlaCount": 3
}
```

> **Invariantes del Payload:**
> - `byStatus` contiene siempre las 6 claves enum (con `0` si no existen registros).
> - `byCategory` contiene siempre las 4 claves enum (con `0` si no existen registros).
> - `byPriority` contiene siempre las 3 claves enum (con `0` si no existen registros) y `unassignedPriorityCount` contabiliza las solicitudes con `priority == null`.
> - `byAssignee` se ordena por `totalAssigned DESC, assigneeName ASC` y `unassignedCount` contabiliza las solicitudes con `assignedToId == null`.

---

### 2.2 `GET /api/v1/reports/requests`
- **Descripción:** Retorna el listado general paginado de todas las solicitudes que coinciden con los filtros activos, ordenado de forma determinística por `created_at DESC, id DESC`.
- **Rol:** **`COORDINADOR`**.
- **Query Parameters:**
  - Los mismos 6 filtros opcionales de `GET /api/v1/reports/summary` (`createdFrom`, `createdTo`, `status`, `category`, `priority`, `assignedToId`).
  - `page` (opcional, default `1`, entero `>= 1`).
  - `size` (opcional, default `20`, entero en `1..20`).

#### Response `200 OK` (`PagedCoordinationReportRequestsResponseDto`)
```json
{
  "items": [
    {
      "id": "b7a1c2d3-4e5f-6789-abcd-0123456789ab",
      "code": "SOL-2026-00019",
      "subject": "Solicitud de certificado de notas oficial",
      "category": "TRAMITE",
      "status": "EN_REVISION",
      "priority": "ALTA",
      "requesterId": "550e8400-e29b-41d4-a716-446655440000",
      "requesterName": "Laura Gómez Pérez",
      "assignedToId": "a1b2c3d4-e5f6-47a8-b9c0-112233445566",
      "assignedToName": "Carlos Andrés Mejía",
      "dueAt": "2026-11-15T18:45:00-05:00",
      "isOverdue": false,
      "respondedAt": null,
      "respondedWithinSla": null,
      "closedAt": null,
      "createdAt": "2026-11-14T18:30:00-05:00"
    }
  ],
  "page": 1,
  "size": 20,
  "totalItems": 42,
  "totalPages": 3
}
```

> **Consistencia:** Ante los mismos parámetros de filtro (`createdFrom`, `createdTo`, `status`, `category`, `priority`, `assignedToId`), `totalRequests` en `/api/v1/reports/summary` es igual a `totalItems` en `/api/v1/reports/requests`.

---

## 3. Reutilización del Detalle Completo e Historial Inmutable

Para consultar el detalle completo y el historial cronológico inmutable de cualquier solicitud seleccionada en el listado general (`RF-06`), el cliente consume directamente:

- **`GET /api/v1/attention/requests/{code}`** *(definido y aprobado en `004-attention-lifecycle/contracts.md`, sección 2.4)*, el cual autoriza al `COORDINADOR` y retorna `AttentionRequestDetailDto` con descripción, respuesta oficial, métricas SLA y todas las entradas de `request_history`.

---

## 4. Contrato de Errores (`ApiErrorDto`)

| HTTP Status | Código Interno | Causa Principal |
| :---: | :--- | :--- |
| `400` | `VALIDATION_ERROR` | `createdFrom` o `createdTo` sin formato `YYYY-MM-DD`, `createdFrom > createdTo`, enum desconocido (`status`/`category`/`priority`), `assignedToId` no UUID, `page < 1` o `size !in 1..20`. |
| `401` | `UNAUTHORIZED` | Ausencia de token Bearer, JWT expirado o `jti` revocado. |
| `403` | `FORBIDDEN` | Usuario autenticado con rol distinto a `COORDINADOR` (`SOLICITANTE` o `PERSONAL_ATENCION`). |

#### Ejemplo `400 Bad Request`
```json
{
  "timestamp": "2026-11-14T19:32:00-05:00",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "Los parámetros de filtro contienen errores.",
  "errors": [
    {
      "field": "createdTo",
      "message": "La fecha final no puede ser anterior a la fecha inicial."
    }
  ]
}
```
