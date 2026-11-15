# Contratos de APIs e Interfaces: 003-service-requests (Backend)
**Ubicación:** `specs/003-service-requests/contracts.md`  
**Spec Asociada:** `specs/003-service-requests/spec.md`  
**Protocolo:** REST / JSON (`application/json; charset=UTF-8`, zona `America/Bogota` `-05:00`, ISO-8601)  
**Estado:** Aprobado  

---

## 1. Matriz de Autorización RBAC y Propiedad
Todos los endpoints de `003-service-requests` requieren `Authorization: Bearer <accessToken>` válido (`exp = 1h`, `jti` no revocado en `revoked_tokens`). Todo error utiliza el contrato transversal **`ApiErrorDto`**.

| Endpoint | Sin JWT | `SOLICITANTE` Propietario | `SOLICITANTE` Otro | `PERSONAL_ATENCION` | `COORDINADOR` |
| :--- | :---: | :---: | :---: | :---: | :---: |
| `POST /api/v1/requests` | `401` | Sí (`201`) | — | `403` | `403` |
| `GET /api/v1/requests/my` | `401` | Sí (`200`) | — | `403` | `403` |
| `GET /api/v1/requests/{code}` | `401` | Sí (`200` / `404`) | `403` | `403` | `403` |
| `POST /api/v1/requests/{code}/cancel` | `401` | Sí (`200` / `404` / `409`) | `403` | `403` | `403` |

*(Nota: Los endpoints de consulta y gestión interna para `PERSONAL_ATENCION` y `COORDINADOR`, incluyendo el historial completo con intervenciones y motivos de escalamiento, pertenecen a `004-attention-lifecycle` y `005-coordination-reports`.)*

---

## 2. Endpoints del Solicitante (`/api/v1/requests`)

### 2.1 `POST /api/v1/requests`
* **Descripción:** Registra una nueva solicitud de servicio aplicando Tabla 2, genera código único `SOL-AAAA-NNNNN`, asigna estado inicial `ABIERTA` e inserta la primera entrada del historial (`CREACION`) en una misma transacción (RF-02, RNF-05).
* **Autenticación y Rol:** JWT requerido + Rol **`SOLICITANTE`**.
* **Política de campos automáticos:** Con `ignoreUnknownKeys = false`, cualquier intento de enviar `code`, `status`, `createdAt`, `requesterId` u otro campo no permitido es rechazado con `400 Bad Request`.

#### Request Body (`CreateServiceRequestDto`)
```json
{
  "category": "TRAMITE",
  "subject": "Solicitud de certificado de notas oficial",
  "description": "Requiero el certificado oficial de calificaciones del semestre 2026-1 para trámite de beca institucional.",
  "previousRequestCode": "SOL-2026-00012"
}
```
*(Campo `previousRequestCode` es opcional — puede omitirse o enviarse como `null`.)*

#### Response `201 Created` (`ServiceRequestDetailDto`)
```json
{
  "id": "b7a1c2d3-4e5f-6789-abcd-0123456789ab",
  "code": "SOL-2026-00019",
  "category": "TRAMITE",
  "subject": "Solicitud de certificado de notas oficial",
  "description": "Requiero el certificado oficial de calificaciones del semestre 2026-1 para trámite de beca institucional.",
  "status": "ABIERTA",
  "previousRequestCode": "SOL-2026-00012",
  "responseText": null,
  "respondedAt": null,
  "closedAt": null,
  "createdAt": "2026-11-14T18:30:00-05:00",
  "updatedAt": "2026-11-14T18:30:00-05:00",
  "history": [
    {
      "id": "c1d2e3f4-5a6b-7c8d-9e0f-112233445566",
      "action": "CREACION",
      "previousState": null,
      "newState": "ABIERTA",
      "occurredAt": "2026-11-14T18:30:00-05:00",
      "comment": "Solicitud registrada por el solicitante."
    }
  ]
}
```

#### Errores Específicos
* **`400 Bad Request` (`VALIDATION_ERROR` / `MALFORMED_REQUEST`):**
  * Campos inválidos de Tabla 2 (`category` fuera de enum, `subject` fuera de `5..100`, `description` fuera de `20..2000`), JSON malformado o envío de campos prohibidos (`code`, `status`, etc.).
  * `previousRequestCode` con formato distinto a `SOL-AAAA-NNNNN`, inexistente, perteneciente a otro solicitante o cuyo estado no sea `RESUELTA` ni `CERRADA` (HU-01 criterio 6).
* **`401 Unauthorized`** / **`403 Forbidden`** (si el rol es `PERSONAL_ATENCION` o `COORDINADOR`).

---

### 2.2 `GET /api/v1/requests/my`
* **Descripción:** Retorna el listado paginado de solicitudes pertenecientes únicamente al *Usuario solicitante* autenticado, ordenadas de la más reciente a la más antigua (HU-02, RNF-07).
* **Autenticación y Rol:** JWT requerido + Rol **`SOLICITANTE`**.
* **Query Parameters:**
  * `page`: `Int` opcional (default `1`, mínimo `1`).
  * `size`: `Int` opcional (default `20`, rango `1..20`).
  * `status`: `String` opcional (`ABIERTA` | `EN_REVISION` | `ESCALADA` | `RESUELTA` | `CERRADA` | `CANCELADA`).

#### Response `200 OK` (`PagedServiceRequestsResponseDto`)
```json
{
  "items": [
    {
      "id": "b7a1c2d3-4e5f-6789-abcd-0123456789ab",
      "code": "SOL-2026-00019",
      "subject": "Solicitud de certificado de notas oficial",
      "category": "TRAMITE",
      "status": "ABIERTA",
      "previousRequestCode": "SOL-2026-00012",
      "createdAt": "2026-11-14T18:30:00-05:00"
    }
  ],
  "page": 1,
  "size": 20,
  "totalItems": 1,
  "totalPages": 1
}
```

#### Errores Específicos
* **`400 Bad Request`:** `page < 1`, `size !in 1..20`, parámetros no numéricos o `status` desconocido.
* **`401 Unauthorized`** / **`403 Forbidden`**.

---

### 2.3 `GET /api/v1/requests/{code}`
* **Descripción:** Consulta el detalle de una solicitud específica del solicitante autenticado, incluyendo su respuesta final/fecha de cierre cuando existan (`RESUELTA`/`CERRADA`) e historial cronológico de cambios de estado **sin intervenciones internas ni motivos de escalamiento** (HU-02, HU-05, RF-03).
* **Autenticación y Rol:** JWT requerido + Rol **`SOLICITANTE`** propietario.
* **Path Parameter:** `code` (ej. `SOL-2026-00019`).

#### Response `200 OK` (`ServiceRequestDetailDto`)
* Misma estructura que el `201 Created`, poblando `responseText`, `respondedAt` y `closedAt` cuando correspondan.

#### Errores Específicos
* **`401 Unauthorized`:** Sin token, expirado o revocado.
* **`403 Forbidden`:** El rol no es `SOLICITANTE` o el código pertenece a otro solicitante.
* **`404 Not Found`:** El código `{code}` no existe en el sistema:
```json
{
  "timestamp": "2026-11-14T18:30:00-05:00",
  "status": 404,
  "error": "NOT_FOUND",
  "message": "Solicitud no encontrada.",
  "path": "/api/v1/requests/SOL-2026-99999",
  "details": []
}
```

---

### 2.4 `POST /api/v1/requests/{code}/cancel`
* **Descripción:** Cancela una solicitud del solicitante autenticado siempre que esté en estado `ABIERTA` (`ABIERTA → CANCELADA`), guardando estado y entrada de historial en la misma transacción (HU-02, Tabla 4, RNF-05).
* **Autenticación y Rol:** JWT requerido + Rol **`SOLICITANTE`** propietario.
* **Request Body (`CancelServiceRequestDto` — opcional / `{}`):**
```json
{
  "reason": "Ya completé el trámite directamente en taquilla."
}
```
*(El cuerpo completo es opcional; si no se envía JSON o `reason` es `null`/omitido, la cancelación procede normalmente.)*

#### Response `200 OK` (`ServiceRequestDetailDto`)
* Retorna el detalle completo actualizado con `"status": "CANCELADA"` y la nueva entrada `CANCELACION` (`ABIERTA → CANCELADA`) al final de `history[]`.

#### Errores Específicos
* **`400 Bad Request`:** `reason` supera `500` caracteres o JSON malformado.
* **`401 Unauthorized`:** Sin token, expirado o revocado.
* **`403 Forbidden`:** Rol distinto de `SOLICITANTE` o solicitud perteneciente a otro usuario.
* **`404 Not Found`:** `"Solicitud no encontrada."`.
* **`409 Conflict`:** La solicitud no está en estado `ABIERTA` (ej. ya fue asignada a `EN_REVISION`, o está `ESCALADA`, `RESUELTA`, `CERRADA` o `CANCELADA`):
```json
{
  "timestamp": "2026-11-14T18:30:00-05:00",
  "status": 409,
  "error": "CONFLICT",
  "message": "Solo es posible cancelar una solicitud que se encuentre en estado Abierta.",
  "path": "/api/v1/requests/SOL-2026-00019/cancel",
  "details": []
}
```

---

## 3. Catálogo de DTOs Kotlin (`@Serializable` — `routing.dto.requests`)

| DTO | Tipo | Campos |
| :--- | :--- | :--- |
| `CreateServiceRequestDto` | Request | `category: String`, `subject: String`, `description: String`, `previousRequestCode: String? = null` |
| `CancelServiceRequestDto` | Request | `reason: String? = null` |
| `RequestHistoryItemDto` | Response | `id`, `action`, `previousState`, `newState`, `occurredAt`, `comment` |
| `ServiceRequestDetailDto` | Response | `id`, `code`, `category`, `subject`, `description`, `status`, `previousRequestCode`, `responseText`, `respondedAt`, `closedAt`, `createdAt`, `updatedAt`, `history: List<RequestHistoryItemDto>` |
| `ServiceRequestSummaryDto` | Response | `id`, `code`, `subject`, `category`, `status`, `previousRequestCode`, `createdAt` |
| `PagedServiceRequestsResponseDto` | Response | `items: List<ServiceRequestSummaryDto>`, `page`, `size`, `totalItems`, `totalPages` |
