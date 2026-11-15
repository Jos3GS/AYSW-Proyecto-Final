# Feature Spec: 003-service-requests (Registro, Consulta, Trazabilidad del Solicitante y Cancelación)
**Ubicación:** `specs/003-service-requests/spec.md`  
**Versión de Spec:** 1.0.0  
**Versión Destino:** `v0.2.0`  
**Estado:** Aprobada  

---

## 1. Resumen y Justificación
* **Problema:** Una vez autenticado, el *Usuario solicitante* necesita un canal único y trazable para registrar solicitudes de servicio con validaciones estrictas (Tabla 2), asociar opcionalmente una solicitud previa resuelta o cerrada cuando no quedó conforme (HU-05 / RF-02), consultar sus propias solicitudes de forma paginada con filtro por estado (HU-02 / RF-03), visualizar el detalle con cambios de estado y respuesta final (sin filtrar información interna), y cancelar solicitudes que aún no hayan sido asignadas (`Abierta → Cancelada`).
* **Solución propuesta:** Implementar los modelos, persistencia (`service_requests` y `request_history` en PostgreSQL 18), patrones GoF **Builder** (construcción segura de solicitudes y filtros) y **State** (base de la máquina de estados con transición inicial `ABIERTA → CANCELADA`), y los endpoints RESTful exclusivos para `SOLICITANTE`: `POST /api/v1/requests`, `GET /api/v1/requests/my`, `GET /api/v1/requests/{code}` y `POST /api/v1/requests/{code}/cancel`.
* **Fuera de alcance (Out of Scope):**
  - Asignación, priorización (SLA 24h/72h/120h), intervenciones internas, escalamiento, reasignación, registro de respuesta y cierre automático a las 24 horas (pertenecen a `004-attention-lifecycle`). *(La tabla `service_requests` y `request_history` se estructuran listas para soportar dichas columnas y acciones en `004` sin romper compatibilidad).*
  - Tablero y resumen consolidado para Coordinación (pertenece a `005-coordination-reports`).
  - Adjuntos, correos, WhatsApp, pagos o integraciones externas (prohibido por RNF-10).

---

## 2. Historias de Usuario
* **HU-01 (Parte Solicitud de Servicio — RF-02):** Como *Usuario solicitante*, quiero presentar solicitudes de servicio con categoría, asunto, descripción y código de solicitud anterior opcional, recibiendo un código único `SOL-AAAA-NNNNN`, fecha/hora de creación y estado inicial `Abierta`, para que la organización reciba mis necesidades con trazabilidad.
* **HU-02 (Consulta y Cancelación — RF-03 / RF-05):** Como *Usuario solicitante*, quiero listar mis propias solicitudes filtradas por estado, ver su detalle e historial de cambios de estado, y cancelar las que aún están en estado `Abierta`, para hacer seguimiento y retirar casos que ya no requiero.
* **HU-05 (Parte Consulta de Respuesta y Reapertura vía Nueva Solicitud — RF-02 / RF-03):** Como *Usuario solicitante*, quiero visualizar en el detalle la respuesta final y fecha de cierre cuando existan, y abrir una nueva solicitud vinculada a una solicitud previa `Resuelta` o `Cerrada` si no estoy satisfecho, sin alterar el estado de la solicitud anterior.

---

## 3. Criterios de Aceptación (Escenarios BDD / Gherkin)

### Escenario 1: Registro exitoso de nueva solicitud por Solicitante (HU-01 / RF-02)
* **Given (Dado que):** Existe un usuario autenticado con rol `SOLICITANTE` (JWT válido).
* **When (Cuando):** Envía `POST /api/v1/requests` con `category` (`TRAMITE` | `ORIENTACION` | `ATENCION_ADMINISTRATIVA` | `REQUERIMIENTO_SERVICIO`), `subject` (5..100 chars), `description` (20..2000 chars) y opcionalmente sin `previousRequestCode`.
* **Then (Entonces):**
  1. El servidor ignora o rechaza (`400` si vienen campos no permitidos) cualquier intento del cliente de enviar `code`, `status`, `createdAt` o `requesterId`.
  2. Genera en el servidor el código único `SOL-AAAA-NNNNN` (`AAAA` en zona `America/Bogota`), asigna estado inicial `ABIERTA`, toma el solicitante desde el JWT y guarda en **una misma transacción** (RNF-05) la solicitud y su primera entrada en `request_history` (`"Creación de solicitud"`, estado `ABIERTA`).
  3. Responde `HTTP 201 Created` con la solicitud creada incluyendo `code`, `status: "ABIERTA"` y `createdAt` en `America/Bogota`.

### Escenario 2: Vinculación válida e inválida con solicitud anterior (`previousRequestCode`) (HU-01 criterio 6 / HU-05)
* **Given (Dado que):** Un `SOLICITANTE` envía `POST /api/v1/requests` indicando `previousRequestCode`.
* **When (Cuando):** El código enviado cumple o incumple las reglas de RF-02.
* **Then (Entonces):**
  1. Si el código tiene formato `SOL-AAAA-NNNNN`, **existe**, **pertenece al mismo solicitante** y está en estado **`RESUELTA`** o **`CERRADA`**, la nueva solicitud se crea (`HTTP 201 Created`) guardando la referencia `previousRequestCode`, mientras la solicitud anterior conserva intacto su estado.
  2. Si el código tiene formato inválido, **no existe**, **pertenece a otro solicitante** o está en un estado distinto de `RESUELTA`/`CERRADA` (ej. `ABIERTA`, `EN_REVISION`, `ESCALADA`, `CANCELADA`), la nueva solicitud no se registra y el servidor responde `HTTP 400 Bad Request` con el detalle del error en `previousRequestCode`.

### Escenario 3: Validación de Tabla 2 y control de rol en registro (`400`, `401`, `403`)
* **Given (Dado que):** Se realiza `POST /api/v1/requests`.
* **When (Cuando):** No hay JWT válido, el JWT pertenece a `PERSONAL_ATENCION` o `COORDINADOR`, o algún campo incumple Tabla 2 (`subject` < 5 o > 100 chars, `description` < 20 o > 2000 chars, `category` desconocida).
* **Then (Entonces):**
  1. Sin JWT o token expirado/revocado → `HTTP 401 Unauthorized`.
  2. Rol `PERSONAL_ATENCION` o `COORDINADOR` → `HTTP 403 Forbidden`.
  3. Datos inválidos → `HTTP 400 Bad Request` con `details[]` indicando el error específico de cada campo; nada se almacena en PostgreSQL.

### Escenario 4: Listado propio paginado con filtro por estado (HU-02 / RNF-03 / RNF-07)
* **Given (Dado que):** Existen múltiples solicitudes de distintos solicitantes en PostgreSQL.
* **When (Cuando):** Un `SOLICITANTE` consulta `GET /api/v1/requests/my?page=1&size=20` (opcionalmente con `&status=ABIERTA`).
* **Then (Entonces):**
  1. Devuelve `HTTP 200 OK` únicamente con las solicitudes cuyo propietario es el solicitante autenticado, paginadas de 20 en 20 (`size <= 20`) en `< 2 segundos` (RNF-07).
  2. Cada elemento incluye `id`, `code`, `subject`, `category`, `status`, `previousRequestCode` y `createdAt`.
  3. Si consulta con un rol distinto de `SOLICITANTE` → `HTTP 403 Forbidden`; si envía parámetros de paginación o estado inválidos → `HTTP 400 Bad Request`.

### Escenario 5: Consulta de detalle, privacidad del historial y errores `403`/`404` (HU-02 / RF-03 / HU-05)
* **Given (Dado que):** El `SOLICITANTE` consulta `GET /api/v1/requests/{code}`.
* **When (Cuando):** El código existe y le pertenece, existe pero pertenece a otro usuario, o no existe.
* **Then (Entonces):**
  1. Si el código no existe → `HTTP 404 Not Found` con mensaje `"Solicitud no encontrada."`.
  2. Si pertenece a otro solicitante → `HTTP 403 Forbidden`.
  3. Si le pertenece → `HTTP 200 OK` con el detalle completo, incluyendo `responseText`, `respondedAt` y `closedAt` cuando existan (`RESUELTA`/`CERRADA`), y su historial cronológico de cambios de estado **filtrando y ocultando cualquier intervención interna o motivo de escalamiento** (RF-03).

### Escenario 6: Cancelación de solicitud en estado `Abierta` (HU-02 / Tabla 4 / RNF-05)
* **Given (Dado que):** Existe una solicitud del solicitante autenticado.
* **When (Cuando):** El `SOLICITANTE` propietario envía `POST /api/v1/requests/{code}/cancel`.
* **Then (Entonces):**
  1. Si el estado actual es `ABIERTA`, el Patrón **State** ejecuta la transición a `CANCELADA` y guarda en **una misma transacción** el cambio de estado y la entrada en `request_history` (`"Cancelación de solicitud"`, `ABIERTA → CANCELADA`), respondiendo `HTTP 200 OK` con la solicitud actualizada.
  2. Si la solicitud ya fue asignada o está en cualquier otro estado (`EN_REVISION`, `ESCALADA`, `RESUELTA`, `CERRADA`, `CANCELADA`), la operación es rechazada sin modificar datos con `HTTP 409 Conflict`.
  3. Si pertenece a otro solicitante → `HTTP 403 Forbidden`; si el código no existe → `HTTP 404 Not Found`.

---

## 4. Contratos de Datos e Interfaces

### Entradas y Salidas Clave
* `POST /api/v1/requests` (JWT — solo `SOLICITANTE`):
  * Input: `{ category, subject, description, previousRequestCode? }`
  * Output (`201 Created`): `{ id, code, category, subject, description, status: "ABIERTA", previousRequestCode, createdAt, updatedAt }`
* `GET /api/v1/requests/my` (JWT — solo `SOLICITANTE`):
  * Query params: `page` (default `1`), `size` (default/máx `20`), `status` (opcional)
  * Output (`200 OK`): `{ items: [{ id, code, subject, category, status, previousRequestCode, createdAt }], page, size, totalItems, totalPages }`
* `GET /api/v1/requests/{code}` (JWT — solo `SOLICITANTE` propietario en `003`):
  * Output (`200 OK`): Detalle completo + `responseText?`, `respondedAt?`, `closedAt?` + `history[]` (solo cambios de estado visibles al solicitante)
* `POST /api/v1/requests/{code}/cancel` (JWT — solo `SOLICITANTE` propietario):
  * Input (opcional): `{ reason? }`
  * Output (`200 OK`): Detalle actualizado con `status: "CANCELADA"`

---

## 5. Requisitos No Funcionales (NFRs)
* **Atomicidad Transaccional y No Eliminación (RNF-05):** Toda creación o cambio de estado inserta su entrada inmutable en `request_history` dentro de la misma transacción PostgreSQL (`transaction { ... }`). Ninguna solicitud ni entrada de historial puede editarse ni eliminarse.
* **Control de Propiedad y Privacidad (RNF-03 / RNF-04):** Validación estricta de rol y propiedad en servidor; ocultamiento estricto de notas/intervenciones internas y motivos de escalamiento en respuestas al `SOLICITANTE`.
* **Rendimiento y Paginación (RNF-07):** Listados paginados de 20 en 20 y tiempos `< 2 segundos` con hasta 1000 solicitudes en PostgreSQL mediante índices adecuados y consultas Exposed DSL sin N+1.
* **Integridad Temporal:** Generación de código `SOL-AAAA-NNNNN` y timestamps en `America/Bogota` (`TIMESTAMPTZ`).
