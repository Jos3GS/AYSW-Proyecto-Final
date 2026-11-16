# Feature Spec: 004-attention-lifecycle (Ciclo de Atención, SLA, Intervenciones, Escalamiento, Respuesta y Cierre Automático)
**Ubicación en Repositorio:** `backend/specs/004-attention-lifecycle/spec.md`  
**Versión de Spec:** 1.0.0  
**Versión Destino:** `v0.2.0`  
**Estado:** Aprobada  

---

## 1. Resumen y Justificación
* **Problema:** Una vez el solicitante radica una solicitud (`ABIERTA`), el equipo interno necesita ejecutar de punta a punta el ciclo operativo de atención definido en **HU-03**, **HU-04**, **HU-05**, **RF-04**, **RF-05** y las **Tablas 3, 4 y 5**: el *Coordinador* debe ver la bandeja ordenada cronológicamente de pendientes (`ABIERTA` y `ESCALADA`), asignar/reasignar responsables de atención con prioridad y SLA fijo sin reinicio de reloj; el *Personal de atención* debe consultar sus asignaciones (con marca informativa de vencimiento), registrar intervenciones internas ocultas al solicitante, escalar casos o emitir respuesta oficial inmutable; y el sistema (`SISTEMA`) debe ejecutar el cierre automático a las 24 horas de haberse respondido.
* **Solución propuesta:** Completar el **Patrón GoF State** (`ServiceRequestState`) y la capa de atención (`/api/v1/attention/*`) reutilizando las columnas y tablas creadas en `V3` (`service_requests` y `request_history`), garantizando atomicidad transaccional (**RNF-05**), control estricto de rol y asignación (**RNF-03**), cálculo exacto de vencimientos (`ALTA = 24h`, `MEDIA = 72h`, `BAJA = 120h` desde `first_assigned_at`), registro de cumplimiento SLA (`responded_within_sla`) y proceso automático de cierre (`AutoCloseScheduler`) sin dependencias externas fuera de la Constitución.
* **Fuera de alcance (Out of Scope):**
  - Tablero consolidado de métricas y filtros gerenciales de Coordinación (`005-coordination-reports`).
  - Adjuntos, envío de correos, WhatsApp, encuestas, pagos o integraciones externas (prohibido por **RNF-10**).

---

## 2. Historias de Usuario
* **HU-03 (Bandeja de Coordinación, Asignación Inicial, Prioridad SLA y Reasignación — RF-04 / Tabla 5):** Como *Coordinador*, quiero consultar primero las solicitudes `Abierta` y `Escalada` de la más antigua a la más reciente, asignarlas a personal de atención definiendo prioridad (`ALTA`, `MEDIA`, `BAJA`), y reasignar las escaladas con motivo obligatorio y prioridad ajustable (recalculando vencimiento desde la primera asignación), para distribuir la carga operativa con control de SLA.
* **HU-04 (Gestión del Personal de Atención: Bandeja, Intervenciones, Escalamiento y Vencimiento — RF-04 / RF-05):** Como *Personal de atención*, quiero consultar mis solicitudes asignadas viendo cuáles están vencidas, registrar notas de intervención interna (`10..1000` chars) sin cambiar el estado, o escalar la solicitud con motivo obligatorio (`10..500` chars) devolviéndola al Coordinador.
* **HU-05 (Respuesta Final Inmutable y Cierre Automático a las 24 Horas — RF-05 / Tabla 4):** Como *Personal de atención*, quiero registrar una respuesta definitiva (`20..2000` chars) que cambie el estado a `Resuelta` dejando constancia de cumplimiento dentro o fuera del SLA; y como *Sistema*, debo cerrar automáticamente (`Resuelta -> Cerrada`) las solicitudes tras cumplirse 24 horas desde `respondedAt`.

---

## 3. Criterios de Aceptación (Escenarios BDD / Gherkin)

### Escenario 1: Bandeja de pendientes del Coordinador (`ABIERTA` + `ESCALADA`, más antiguas primero — HU-03.1)
* **Given (Dado que):** Existen múltiples solicitudes en estados `ABIERTA`, `EN_REVISION`, `ESCALADA`, `RESUELTA`, `CERRADA` y `CANCELADA`.
* **When (Cuando):** Un `COORDINADOR` consulta `GET /api/v1/attention/coordination/pending?page=1&size=20` (con filtro opcional `status` entre `ABIERTA` o `ESCALADA`).
* **Then (Entonces):**
  1. Responde `HTTP 200 OK` devolviendo únicamente solicitudes en estado `ABIERTA` o `ESCALADA`, ordenadas estrictamente de la **más antigua a la más reciente** (`created_at ASC, id ASC`), con `size <= 20` y en `< 2s` (`RNF-07`).
  2. Incluye etiqueta informativa `isOverdue = true` si una `ESCALADA` ya superó `dueAt`.
  3. Si consulta `SOLICITANTE` o `PERSONAL_ATENCION` → `HTTP 403 Forbidden`.

### Escenario 2: Asignación inicial y cálculo de fecha límite SLA (HU-03.2 / HU-03.3 / Tabla 5)
* **Given (Dado que):** Una solicitud está en estado `ABIERTA` y existe un usuario con rol `PERSONAL_ATENCION`.
* **When (Cuando):** El `COORDINADOR` envía `POST /api/v1/attention/requests/{code}/assign` con `{ assigneeId, priority }` (`ALTA` | `MEDIA` | `BAJA`).
* **Then (Entonces):**
  1. El Patrón **State** valida `status == ABIERTA` (si no → `HTTP 409 Conflict`).
  2. Verifica que `assigneeId` pertenezca a un usuario con rol **`PERSONAL_ATENCION`** (si es `SOLICITANTE`, `COORDINADOR` o no existe → `HTTP 400 Bad Request`).
  3. Fija `status = EN_REVISION`, `assignedTo = assigneeId`, `priority`, `firstAssignedAt = now`, `assignedAt = now` y `dueAt = firstAssignedAt + {24h | 72h | 120h}` según prioridad.
  4. Inserta en la misma transacción la entrada `ASIGNACION` (`ABIERTA -> EN_REVISION`) en `request_history` y responde `HTTP 200 OK`.

### Escenario 3: Reasignación de solicitud `Escalada` sin reinicio de reloj SLA (HU-03.4 / HU-03.5)
* **Given (Dado que):** Una solicitud se encuentra en estado **`ESCALADA`** con `firstAssignedAt` registrado previamente.
* **When (Cuando):** El `COORDINADOR` envía `POST /api/v1/attention/requests/{code}/reassign` con `{ assigneeId, reason, priority? }` (`reason` `10..500` caracteres).
* **Then (Entonces):**
  1. Si `status != ESCALADA` → `HTTP 409 Conflict`.
  2. Si `assigneeId` no corresponde a `PERSONAL_ATENCION` o `reason` incumple `10..500` chars → `HTTP 400 Bad Request`.
  3. Cambia a `EN_REVISION`, actualiza `assignedTo = assigneeId`, `assignedAt = now`, y conserva intacto `firstAssignedAt`; si se especifica nueva `priority` (o con la vigente), recalcula **`dueAt = firstAssignedAt + {24h | 72h | 120h}`** *(sin reiniciar el SLA desde cero)*.
  4. Inserta `REASIGNACION` (`ESCALADA -> EN_REVISION`, `comment = reason`) en `request_history` en la misma transacción.

### Escenario 4: Bandeja del Personal de atención, detalle interno y marca informativa de vencimiento (HU-04.1 / HU-04.5)
* **Given (Dado que):** Un usuario `PERSONAL_ATENCION` inicia sesión.
* **When (Cuando):** Consulta `GET /api/v1/attention/my-assigned?page=1&size=20` (opcional `&status=...`) o `GET /api/v1/attention/requests/{code}`.
* **Then (Entonces):**
  1. En su bandeja recibe únicamente solicitudes asignadas a su `userId` (`assigned_to == actorId`), indicando `isOverdue = true` cuando `status IN {EN_REVISION, ESCALADA}` y `now > dueAt`.
  2. En el detalle interno (`COORDINADOR` ve cualquiera; `PERSONAL_ATENCION` solo las asignadas a él, recibiendo `HTTP 403 Forbidden` si consulta ajenas o no asignadas) se muestra el **historial completo** incluyendo `INTERVENCION` y comentarios completos de `ESCALAMIENTO`/`REASIGNACION`.
  3. El vencimiento (`isOverdue == true`) es informativo y **no bloquea** intervenciones, escalamiento, reasignación ni respuesta final.

### Escenario 5: Registro de intervención interna y escalamiento por el responsable asignado (HU-04.2 / HU-04.3 / HU-04.4)
* **Given (Dado que):** Una solicitud está en estado `EN_REVISION` asignada al agente A (`assigned_to == agentA`).
* **When (Cuando):**
  - El agente A (o un agente B distinto) envía `POST /api/v1/attention/requests/{code}/interventions` con `{ comment }` (`10..1000` chars); o
  - El agente A envía `POST /api/v1/attention/requests/{code}/escalate` con `{ reason }` (`10..500` chars).
* **Then (Entonces):**
  1. Si otro usuario `PERSONAL_ATENCION` o un `COORDINADOR` intenta intervenir o escalar sin ser el responsable asignado → `HTTP 403 Forbidden`; si `status != EN_REVISION` → `HTTP 409 Conflict`.
  2. La intervención valida `10..1000` caracteres, mantiene `status = EN_REVISION` sin cambios y guarda fila `INTERVENCION` (`EN_REVISION -> EN_REVISION`) en `request_history`.
  3. El escalamiento valida `10..500` caracteres, cambia `status` a `ESCALADA` (manteniendo `firstAssignedAt` y `dueAt`) y guarda fila `ESCALAMIENTO` (`EN_REVISION -> ESCALADA`, `comment = reason`) en la misma transacción.

### Escenario 6: Registro de respuesta final inmutable y cálculo SLA (HU-05 / RF-05 / RNF-05)
* **Given (Dado que):** Una solicitud está en `EN_REVISION` asignada al agente A.
* **When (Cuando):** El agente A envía `POST /api/v1/attention/requests/{code}/respond` con `{ responseText }` (`20..2000` caracteres).
* **Then (Entonces):**
  1. Valida que el actor sea el responsable asignado (`403` si no) y que `status == EN_REVISION` (`409` si no).
  2. Cambia `status = RESUELTA`, persiste `responseText` (nunca modificable posteriormente), fija `respondedAt = now`, calcula `respondedWithinSla = (now <= dueAt)` y agrega entrada `RESPUESTA` (`EN_REVISION -> RESUELTA`) en `request_history` dentro de la misma transacción.

### Escenario 7: Cierre automático a las 24 horas (`RESUELTA -> CERRADA`) por actor `SISTEMA` (HU-05 / Tabla 4)
* **Given (Dado que):** Existen solicitudes en estado `RESUELTA` cuyo `respondedAt` tiene 24 horas o más de antigüedad (`responded_at <= now - 24h`) y otras con menos de 24 horas.
* **When (Cuando):** Se ejecuta el proceso automático de cierre (`AutoCloseScheduler` periódico o ejecución programada del servicio).
* **Then (Entonces):**
  1. Únicamente las solicitudes con `status == RESUELTA` y `respondedAt <= now - 24h` transicionan atómicamente a `CERRADA` (`WHERE status = 'RESUELTA' AND responded_at <= ?`), registrando `closedAt = now`.
  2. Cada cierre genera su entrada en `request_history` con `actor_id = NULL`, `actor_role = 'SISTEMA'`, `action = 'CIERRE_AUTOMATICO'`, `previous_state = 'RESUELTA'`, `new_state = 'CERRADA'` y `comment = "Cierre automático"`.
  3. Las solicitudes `RESUELTA` con menos de 24 horas permanecen en `RESUELTA`.

---

## 4. Contratos de Datos e Interfaces (`004-attention-lifecycle`)
* **`GET /api/v1/attention/coordination/pending`** (JWT `COORDINADOR`): listado paginado (`page`, `size <= 20`, `status?` in `{ABIERTA, ESCALADA}`) ordenado `created_at ASC, id ASC`.
* **`POST /api/v1/attention/requests/{code}/assign`** (JWT `COORDINADOR`): `{ assigneeId, priority }` → `ABIERTA -> EN_REVISION` (`200 OK`).
* **`POST /api/v1/attention/requests/{code}/reassign`** (JWT `COORDINADOR`): `{ assigneeId, reason, priority? }` → `ESCALADA -> EN_REVISION` (`200 OK`).
* **`GET /api/v1/attention/my-assigned`** (JWT `PERSONAL_ATENCION`): listado paginado propio (`page`, `size <= 20`, `status?`) con `priority`, `dueAt` e `isOverdue`.
* **`GET /api/v1/attention/requests/{code}`** (JWT `COORDINADOR` o `PERSONAL_ATENCION` asignado): detalle completo con `assignedTo`, `priority`, `firstAssignedAt`, `assignedAt`, `dueAt`, `isOverdue`, `respondedWithinSla` e historial completo incluyendo `actorName`, `actorRole`, intervenciones y motivos.
* **`POST /api/v1/attention/requests/{code}/interventions`** (JWT `PERSONAL_ATENCION` asignado): `{ comment }` (`10..1000`) → `EN_REVISION` (`200 OK`).
* **`POST /api/v1/attention/requests/{code}/escalate`** (JWT `PERSONAL_ATENCION` asignado): `{ reason }` (`10..500`) → `ESCALADA` (`200 OK`).
* **`POST /api/v1/attention/requests/{code}/respond`** (JWT `PERSONAL_ATENCION` asignado): `{ responseText }` (`20..2000`) → `RESUELTA` (`200 OK`).

---

## 5. Requisitos No Funcionales (NFRs)
* **Atomicidad y Auditoría Inmutable (RNF-05):** Toda asignación, intervención, escalamiento, reasignación, respuesta y cierre automático se ejecuta en transacción SQL única (`UPDATE` condicional de estado + `INSERT` append-only en `request_history`).
* **Integridad de Plazos y Concurrencia:** Los cálculos de `dueAt` (`+24h`, `+72h`, `+120h`), `respondedWithinSla` y `respondedAt + 24h` se realizan exclusivamente en servidor en zona `America/Bogota` con reloj inyectable (`Clock` / proveedor de tiempo) para pruebas deterministas; las transiciones concurrentes conflictivas devuelven `409 Conflict` sin duplicar historial.
* **Rendimiento (RNF-07):** Paginación de `20/pág` apoyada por `idx_sr_status_created` e `idx_sr_assigned_due` garantizando respuestas `< 2s` con `1000` solicitudes.
