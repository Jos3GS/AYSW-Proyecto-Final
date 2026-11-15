# Contratos de APIs e Interfaces: 003-service-requests (Frontend)
**Ubicación:** `specs/003-service-requests/contracts.md`  
**Spec Asociada:** `specs/003-service-requests/spec.md`  
**Protocolo:** REST / JSON vía Ktor Client 3.6.x (`kotlinx.serialization.json`, `API_BASE_URL` vía `BuildKonfig`)  
**Estado:** Aprobado  

---

## 1. Endpoints Backend Consumidos (`003-service-requests`)

### 1.1 `POST {API_BASE_URL}/api/v1/requests`
* **Autenticación y Rol:** JWT requerido (`Authorization: Bearer <token>`) + Rol **`SOLICITANTE`**.
* **Request (`CreateServiceRequestDto`):**
```json
{
  "category": "TRAMITE",
  "subject": "Solicitud de certificado de notas oficial",
  "description": "Requiero el certificado oficial de calificaciones del semestre 2026-1 para trámite de beca institucional.",
  "previousRequestCode": "SOL-2026-00012"
}
```
*(El campo `previousRequestCode` se envía como `null` u omitido si el solicitante lo deja vacío.)*
* **Response `201 Created` (`ServiceRequestDetailDto` → `ServiceRequestDetail` vía `RequestMapper`):**
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
* **Errores mapeados (`ErrorMapper`):**
  * `400 Bad Request` → asigna `details[]` a `fieldErrors` (`category`, `subject`, `description`, `previousRequestCode` — por formato, inexistencia, otro propietario o estado distinto de `RESUELTA`/`CERRADA`).
  * `401 Unauthorized` → `SessionEvent.SessionExpired` → limpieza de `SessionStorage` y redirección a `LoginRoute`.
  * `403 Forbidden` → aviso controlado de permisos insuficientes sin cerrar sesión.

---

### 1.2 `GET {API_BASE_URL}/api/v1/requests/my`
* **Autenticación y Rol:** JWT requerido + Rol **`SOLICITANTE`**.
* **Query Parameters:** `page` (entero $\ge 1$, default `1`), `size` (`20`), `status` (opcional: `ABIERTA` | `EN_REVISION` | `ESCALADA` | `RESUELTA` | `CERRADA` | `CANCELADA`).
* **Response `200 OK` (`PagedServiceRequestsResponseDto` → `ServiceRequestsPage` → `List<RequestSummaryUiModel>`):**
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
* **Errores mapeados:** `400` (parámetros inválidos), `401` (`SessionExpired`), `403` (`Forbidden`), error de red / `5xx` (banner con acción **"Reintentar"**).

---

### 1.3 `GET {API_BASE_URL}/api/v1/requests/{code}`
* **Autenticación y Rol:** JWT requerido + Rol **`SOLICITANTE`** propietario.
* **Path Parameter:** `code` (ej. `SOL-2026-00019`).
* **Response `200 OK` (`ServiceRequestDetailDto` → `ServiceRequestDetail` → `RequestDetailUiModel`):**
  * Incluye `responseText`, `respondedAt` y `closedAt` cuando correspondan (`RESUELTA`/`CERRADA`) e historial cronológico sin intervenciones ni motivos internos (`RF-03`).
* **Errores mapeados:**
  * `404 Not Found` (`"Solicitud no encontrada."`) → estado de error específico en `RequestDetailUiState.Error(isNotFound = true)` con botón **"Volver a mis solicitudes"**.
  * `403 Forbidden` → estado `RequestDetailUiState.Error(isForbidden = true)` con aviso en español y botón para volver.
  * `401 Unauthorized` → `SessionExpired`.

---

### 1.4 `POST {API_BASE_URL}/api/v1/requests/{code}/cancel`
* **Autenticación y Rol:** JWT requerido + Rol **`SOLICITANTE`** propietario (habilitado en UI solo si `status == ABIERTA`).
* **Request (`CancelServiceRequestDto`):**
```json
{
  "reason": "Ya completé el trámite directamente en taquilla."
}
```
*(Si el solicitante no escribe motivo, se envía `{"reason": null}`.)*
* **Response `200 OK` (`ServiceRequestDetailDto` actualizado con `status: "CANCELADA"`):**
  * Cierra `CancelRequestDialog`, reemplaza el detalle actual por la respuesta actualizada del servidor e informa al usuario la cancelación exitosa.
* **Errores mapeados:**
  * `400 Bad Request` (`reason > 500` chars) → error en línea dentro del diálogo sin cerrarlo.
  * `409 Conflict` (`"Solo es posible cancelar una solicitud que se encuentre en estado Abierta."`) → cierra el diálogo, muestra el mensaje del servidor en `actionError` y ejecuta recarga inmediata de `getDetail(code)` para sincronizar el estado real.
  * `401` / `403` / `404` → manejo uniforme con el detalle.

---

## 2. Contrato de Navegación Tipada y Mapeo en Español

### Rutas del Módulo Solicitante (`AppNavHost`)
| Ruta (`@Serializable`) | Pantalla | Rol Requerido | Comportamiento de Guardia RBAC |
| :--- | :--- | :--- | :--- |
| `ApplicantHomeRoute` | `MyRequestsScreen` *(reemplaza placeholder de `002`)* | `SOLICITANTE` | Sin sesión → `LoginRoute`; otro rol → su Home |
| `CreateRequestRoute(previousRequestCode: String? = null)` | `CreateRequestScreen` | `SOLICITANTE` | Sin sesión → `LoginRoute`; otro rol → su Home |
| `RequestDetailRoute(code: String)` | `RequestDetailScreen` | `SOLICITANTE` | Sin sesión → `LoginRoute`; otro rol → su Home |

### Tabla de Traducción UI (`RequestUiMapper`)
* **Categorías (`RequestCategory`):** `TRAMITE` → `"Trámite"`, `ORIENTACION` → `"Orientación"`, `ATENCION_ADMINISTRATIVA` → `"Atención administrativa"`, `REQUERIMIENTO_SERVICIO` → `"Requerimiento de servicio"`.
* **Estados (`RequestStatus`):** `ABIERTA` → `"Abierta"`, `EN_REVISION` → `"En revisión"`, `ESCALADA` → `"Escalada"`, `RESUELTA` → `"Resuelta"`, `CERRADA` → `"Cerrada"`, `CANCELADA` → `"Cancelada"`.
* **Acciones de Historial (`RequestHistoryAction`):** `CREACION` → `"Creación de solicitud"`, `CANCELACION` → `"Cancelación de solicitud"`, `ASIGNACION` → `"Asignación"`, `INTERVENCION` → `"Intervención"`, `ESCALAMIENTO` → `"Escalamiento"`, `REASIGNACION` → `"Reasignación"`, `RESPUESTA` → `"Respuesta registrada"`, `CIERRE_AUTOMATICO` → `"Cierre automático"`.
* **Fechas:** Formateadas en `America/Bogota` con patrón `dd/mm/aaaa hh:mm` vía `DateFormatter`.

---

## 3. Contrato de Componentes UI, Confirmaciones (`RNF-09`) y Test Tags

### Diálogo de Confirmación de Cancelación (`CancelRequestDialog`)
* **Título:** `"Cancelar solicitud"`
* **Mensaje:** `"¿Estás seguro de cancelar la solicitud <code>? Esta acción no se puede deshacer."`
* **Campo Opcional:** `"Motivo de cancelación (opcional)"` (`<= 500` caracteres, con contador `N / 500`).
* **Acciones:** `"Volver"` / `"Confirmar cancelación"`.

### Etiquetas Semánticas Estables (`Modifier.testTag`)
* **Mis Solicitudes:** `requests_filter_all`, `requests_filter_<STATUS>`, `requests_list_container`, `requests_item_<code>`, `requests_empty_state`, `requests_error_retry_button`, `requests_prev_page_button`, `requests_next_page_button`, `requests_new_button`.
* **Crear Solicitud:** `create_request_category_dropdown`, `create_request_category_<CATEGORY>`, `create_request_subject_input`, `create_request_description_input`, `create_request_previous_code_input`, `create_request_submit_button`, `create_request_error_banner`.
* **Detalle y Cancelación:** `request_detail_code`, `request_detail_status_badge`, `request_detail_previous_code_link`, `request_detail_response_card`, `request_detail_history_list`, `request_detail_create_related_button`, `request_detail_cancel_button`, `request_detail_action_error_banner`, `cancel_dialog_reason_input`, `cancel_dialog_confirm_button`, `cancel_dialog_dismiss_button`.
