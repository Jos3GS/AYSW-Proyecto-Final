# Contratos de APIs e Interfaces: 004-attention-lifecycle (Backend)
**Ubicación en Repositorio:** `backend/specs/004-attention-lifecycle/contracts.md`  
**Spec Asociada:** `backend/specs/004-attention-lifecycle/spec.md`  
**Protocolo:** REST / JSON (`application/json; charset=UTF-8`, zona `America/Bogota` `-05:00`, ISO-8601)  
**Estado:** Aprobado  

---

## 1. Matriz de Autorización RBAC y Propiedad
Todos los endpoints de `004-attention-lifecycle` requieren `Authorization: Bearer <accessToken>` válido (`exp = 1h`, `jti` no revocado en `revoked_tokens`). Todo error utiliza el contrato global **`ApiErrorDto`**.

| Endpoint | Sin JWT | `SOLICITANTE` | `PERSONAL_ATENCION` Asignado | `PERSONAL_ATENCION` Otro | `COORDINADOR` |
| :--- | :---: | :---: | :---: | :---: | :---: |
| `GET /api/v1/attention/coordination/pending` | `401` | `403` | `403` | `403` | Sí (`200` / `400`) |
| `POST /api/v1/attention/requests/{code}/assign` | `401` | `403` | `403` | `403` | Sí (`200` / `400` / `404` / `409`) |
| `POST /api/v1/attention/requests/{code}/reassign` | `401` | `403` | `403` | `403` | Sí (`200` / `400` / `404` / `409`) |
| `GET /api/v1/attention/my-assigned` | `401` | `403` | Sí (`200` / `400`) | Sí *(solo su bandeja)* | `403` |
| `GET /api/v1/attention/requests/{code}` | `401` | `403` | Sí (`200` / `404`) | `403` | Sí (`200` / `404`) |
| `POST /api/v1/attention/requests/{code}/interventions` | `401` | `403` | Sí (`200` / `400` / `409`) | `403` | `403` |
| `POST /api/v1/attention/requests/{code}/escalate` | `401` | `403` | Sí (`200` / `400` / `409`) | `403` | `403` |
| `POST /api/v1/attention/requests/{code}/respond` | `401` | `403` | Sí (`200` / `400` / `409`) | `403` | `403` |

---

## 2. Endpoints de Coordinación (`/api/v1/attention/...`)

### 2.1 `GET /api/v1/attention/coordination/pending`
* **Descripción:** Retorna la bandeja paginada del *Coordinador* con solicitudes en estado `ABIERTA` o `ESCALADA`, ordenadas estrictamente de la más antigua a la más reciente (`created_at ASC, id ASC`) (HU-03.1, RNF-07).
* **Rol:** **`COORDINADOR`**.
* **Query Parameters:** `page` (default `1`, `>= 1`), `size` (default/máx `20`, `1..20`), `status` (opcional: `ABIERTA` o `ESCALADA`; cualquier otro valor → `400 Bad Request`).

#### Response `200 OK` (`PagedAttentionRequestsResponseDto`)
```json
{
  "items": [
    {
      "id": "b7a1c2d3-4e5f-6789-abcd-0123456789ab",
      "code": "SOL-2026-00019",
      "subject": "Solicitud de certificado de notas oficial",
      "category": "TRAMITE",
      "status": "ABIERTA",
      "priority": null,
      "requesterId": "550e8400-e29b-41d4-a716-446655440000",
      "requesterName": "Laura Gómez Pérez",
      "assignedToId": null,
      "assignedToName": null,
      "firstAssignedAt": null,
      "dueAt": null,
      "isOverdue": false,
      "createdAt": "2026-11-14T18:30:00-05:00"
    }
  ],
  "page": 1,
  "size": 20,
  "totalItems": 1,
  "totalPages": 1
}
```

---

### 2.2 `POST /api/v1/attention/requests/{code}/assign`
* **Descripción:** Asigna inicialmente una solicitud en estado `ABIERTA` a un integrante de `PERSONAL_ATENCION`, definiendo prioridad (`ALTA` = `24h`, `MEDIA` = `72h`, `BAJA` = `120h`), fijando `firstAssignedAt`, `assignedAt` y `dueAt`, y cambiando a `EN_REVISION` en transacción única.
* **Rol:** **`COORDINADOR`**.
* **Request Body (`AssignRequestDto`):**
```json
{
  "assigneeId": "a1b2c3d4-e5f6-47a8-b9c0-112233445566",
  "priority": "ALTA"
}
```
* **Response `200 OK` (`AttentionRequestDetailDto`):**
```json
{
  "id": "b7a1c2d3-4e5f-6789-abcd-0123456789ab",
  "code": "SOL-2026-00019",
  "category": "TRAMITE",
  "subject": "Solicitud de certificado de notas oficial",
  "description": "Requiero el certificado oficial de calificaciones del semestre 2026-1 para trámite de beca institucional.",
  "status": "EN_REVISION",
  "previousRequestCode": null,
  "requesterId": "550e8400-e29b-41d4-a716-446655440000",
  "requesterName": "Laura Gómez Pérez",
  "assignedToId": "a1b2c3d4-e5f6-47a8-b9c0-112233445566",
  "assignedToName": "Carlos Andrés Mejía",
  "priority": "ALTA",
  "firstAssignedAt": "2026-11-14T18:45:00-05:00",
  "assignedAt": "2026-11-14T18:45:00-05:00",
  "dueAt": "2026-11-15T18:45:00-05:00",
  "isOverdue": false,
  "responseText": null,
  "respondedAt": null,
  "respondedWithinSla": null,
  "closedAt": null,
  "createdAt": "2026-11-14T18:30:00-05:00",
  "updatedAt": "2026-11-14T18:45:00-05:00",
  "history": [
    {
      "id": "c1d2e3f4-5a6b-7c8d-9e0f-112233445566",
      "action": "CREACION",
      "previousState": null,
      "newState": "ABIERTA",
      "actorId": "550e8400-e29b-41d4-a716-446655440000",
      "actorName": "Laura Gómez Pérez",
      "actorRole": "SOLICITANTE",
      "comment": "Solicitud registrada por el solicitante.",
      "occurredAt": "2026-11-14T18:30:00-05:00"
    },
    {
      "id": "d2e3f4a5-6b7c-8d9e-0f1a-223344556677",
      "action": "ASIGNACION",
      "previousState": "ABIERTA",
      "newState": "EN_REVISION",
      "actorId": "99887766-5544-3322-1100-aabbccddeeff",
      "actorName": "Coordinador General",
      "actorRole": "COORDINADOR",
      "comment": "Asignada a Carlos Andrés Mejía con prioridad Alta.",
      "occurredAt": "2026-11-14T18:45:00-05:00"
    }
  ]
}
```
* **Errores:** `400` (`assigneeId` no existe o no tiene rol `PERSONAL_ATENCION`, o `priority` inválida), `404` (`"Solicitud no encontrada."`), `409` (`status != ABIERTA`).

---

### 2.3 `POST /api/v1/attention/requests/{code}/reassign`
* **Descripción:** Reasigna una solicitud en estado `ESCALADA` a un `PERSONAL_ATENCION` con motivo obligatorio (`10..500` chars) y prioridad opcional; recalcula `dueAt` desde `firstAssignedAt` sin reiniciar el SLA (`ESCALADA -> EN_REVISION`).
* **Rol:** **`COORDINADOR`**.
* **Request Body (`ReassignRequestDto`):**
```json
{
  "assigneeId": "f6e5d4c3-b2a1-4098-8765-abcdef123456",
  "reason": "Se asigna al especialista en registro académico para verificación final.",
  "priority": "ALTA"
}
```
*(Campo `priority` es opcional; si es `null`/omitido conserva la prioridad vigente.)*
* **Response `200 OK`:** `AttentionRequestDetailDto` actualizado con `status: "EN_REVISION"`, `firstAssignedAt` intacto y nueva entrada `REASIGNACION` en `history[]`.
* **Errores:** `400` (`assigneeId` inválido o no `PERSONAL_ATENCION`, `reason` fuera de `10..500` chars, `priority` inválida), `404`, `409` (`status != ESCALADA`).

---

## 3. Endpoints de Personal de Atención y Detalle Interno

### 3.1 `GET /api/v1/attention/my-assigned`
* **Descripción:** Lista paginada (`20/pág`) de solicitudes asignadas al agente autenticado (`assigned_to == actorId`), indicando `isOverdue` para casos vencidos.
* **Rol:** **`PERSONAL_ATENCION`**.
* **Query Parameters:** `page` (`>= 1`), `size` (`1..20`), `status` (opcional: `EN_REVISION` | `ESCALADA` | `RESUELTA` | `CERRADA`).
* **Response `200 OK`:** `PagedAttentionRequestsResponseDto`.

---

### 3.2 `GET /api/v1/attention/requests/{code}`
* **Descripción:** Retorna el detalle interno completo de una solicitud, incluyendo todas las entradas de `request_history` (`CREACION`, `CANCELACION`, `ASIGNACION`, `INTERVENCION`, `ESCALAMIENTO`, `REASIGNACION`, `RESPUESTA`, `CIERRE_AUTOMATICO`), datos de asignación, `isOverdue` y `respondedWithinSla`.
* **Rol y Acceso:** **`COORDINADOR`** (cualquier solicitud) o **`PERSONAL_ATENCION`** (únicamente si `assignedToId == actorId`; de lo contrario `403 Forbidden`).
* **Response `200 OK`:** `AttentionRequestDetailDto`.
* **Errores:** `403 Forbidden`, `404 Not Found` (`"Solicitud no encontrada."`).

---

### 3.3 `POST /api/v1/attention/requests/{code}/interventions`
* **Descripción:** Registra una nota interna de seguimiento (`10..1000` chars) por parte del agente asignado sin modificar el estado (`EN_REVISION -> EN_REVISION`).
* **Rol y Acceso:** **`PERSONAL_ATENCION`** asignado al caso (`assigned_to == actorId`).
* **Request Body (`AddInterventionRequestDto`):**
```json
{
  "comment": "Se validó el histórico académico 2026-1 con admisiones y quedó pendiente firma digital."
}
```
* **Response `200 OK`:** `AttentionRequestDetailDto` con la nueva fila `INTERVENCION` añadida al final de `history[]`.
* **Errores:** `400` (`comment` fuera de `10..1000`), `403` (si no es el responsable asignado o es `COORDINADOR`/`SOLICITANTE`), `404`, `409` (`status != EN_REVISION`).

---

### 3.4 `POST /api/v1/attention/requests/{code}/escalate`
* **Descripción:** Escala la solicitud devolviéndola al Coordinador con motivo obligatorio (`10..500` chars), cambiando de `EN_REVISION` a `ESCALADA` sin alterar `firstAssignedAt` ni `dueAt`.
* **Rol y Acceso:** **`PERSONAL_ATENCION`** asignado al caso.
* **Request Body (`EscalateRequestDto`):**
```json
{
  "reason": "Requiere autorización excepcional de decanatura antes de emitir el certificado."
}
```
* **Response `200 OK`:** `AttentionRequestDetailDto` con `status: "ESCALADA"` y entrada `ESCALAMIENTO` en `history[]`.
* **Errores:** `400` (`reason` fuera de `10..500`), `403`, `404`, `409` (`status != EN_REVISION`).

---

### 3.5 `POST /api/v1/attention/requests/{code}/respond`
* **Descripción:** Registra la respuesta definitiva e inmutable al solicitante (`20..2000` chars), cambia a `RESUELTA`, fija `respondedAt = now` y calcula `respondedWithinSla = (now <= dueAt)`.
* **Rol y Acceso:** **`PERSONAL_ATENCION`** asignado al caso.
* **Request Body (`RespondRequestDto`):**
```json
{
  "responseText": "Estimado estudiante, su certificado oficial de notas fue emitido y se encuentra disponible para descarga en el portal académico institucional."
}
```
* **Response `200 OK`:** `AttentionRequestDetailDto` con `status: "RESUELTA"`, `respondedWithinSla` calculado y entrada `RESPUESTA` en `history[]`.
* **Errores:** `400` (`responseText` fuera de `20..2000`), `403`, `404`, `409` (`status != EN_REVISION`).

---

## 4. Catálogo de DTOs Kotlin (`@Serializable` — `routing.dto.attention`)

| DTO | Tipo | Campos |
| :--- | :--- | :--- |
| `AssignRequestDto` | Request | `assigneeId: String`, `priority: String` |
| `ReassignRequestDto` | Request | `assigneeId: String`, `reason: String`, `priority: String? = null` |
| `AddInterventionRequestDto` | Request | `comment: String` |
| `EscalateRequestDto` | Request | `reason: String` |
| `RespondRequestDto` | Request | `responseText: String` |
| `AttentionRequestSummaryDto` | Response | `id`, `code`, `subject`, `category`, `status`, `priority`, `requesterId`, `requesterName`, `assignedToId`, `assignedToName`, `firstAssignedAt`, `dueAt`, `isOverdue`, `createdAt` |
| `PagedAttentionRequestsResponseDto` | Response | `items`, `page`, `size`, `totalItems`, `totalPages` |
| `InternalRequestHistoryItemDto` | Response | `id`, `action`, `previousState`, `newState`, `actorId`, `actorName`, `actorRole`, `comment`, `occurredAt` |
| `AttentionRequestDetailDto` | Response | `id`, `code`, `category`, `subject`, `description`, `status`, `previousRequestCode`, `requesterId`, `requesterName`, `assignedToId`, `assignedToName`, `priority`, `firstAssignedAt`, `assignedAt`, `dueAt`, `isOverdue`, `responseText`, `respondedAt`, `respondedWithinSla`, `closedAt`, `createdAt`, `updatedAt`, `history` |
