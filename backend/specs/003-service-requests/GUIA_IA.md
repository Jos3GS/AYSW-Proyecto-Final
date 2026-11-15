# Guía de Instrucciones para la IA (Prompt Maestro y Correcciones) — `003-service-requests` (Backend)

**Feature:** `003-service-requests`  
**Subproyecto:** Backend (Kotlin 2.4.x + Ktor 3.6.x + Exposed 1.6.x DSL + PostgreSQL 18)  
**Versión:** 1.0.0  
**Fecha:** 2026-11-14  
**Estado:** Aprobado  

> **Propósito de este archivo:** Contiene el **Prompt Maestro** para implementar la épica `003-service-requests` en el Backend (radicación con código anual `SOL-AAAA-NNNNN`, consulta/paginación del solicitante, privacidad RF-03 de historial y cancelación mediante Patrón State) junto con las correcciones críticas contra errores frecuentes de la IA.

---

Actúa como un **Ingeniero Backend Senior experto en Kotlin 2.4.x, Exposed 1.6.x DSL, PostgreSQL 18 y Patrones GoF (Builder y State)**, trabajando bajo **Spec-Driven Development (SDD)** y TDD.

Tu misión es implementar la épica **`003-service-requests`** del Backend de **ITM Service Desk** siguiendo exactamente `specs/003-service-requests/tasks.md` (`T01` a `T07`).

---

### VERIFICACIÓN PREVIA DE CONTINUIDAD

1. Confirma que `001-project-setup` y `002-auth-users` pasan `./gradlew ktlintCheck check koverVerify`.
2. Lee `specs/003-service-requests/{spec,research,data-model,contracts,plan,checklist,tasks}.md`.

---

### REGLAS INNEGOCIABLES (`003-service-requests`)

1. **Migración `V3__create_service_requests_tables.sql` Completa y Append-Only:**
   - Crea `request_code_sequences`, `service_requests` (incluyendo las columnas nullable de `004` y sus `CHECK` constraints) y `request_history`.
   - `request_history` es **estrictamente append-only (`RNF-05`)**: **PROHIBIDO** escribir `UPDATE` o `DELETE` sobre `RequestHistoryTable`.
2. **Consecutivo Atómico Anual `SOL-AAAA-NNNNN` (`America/Bogota`):**
   - Genera el consecutivo dentro de la transacción de `createWithHistory` usando:
     `INSERT INTO request_code_sequences ... ON CONFLICT (year) DO UPDATE SET last_number = request_code_sequences.last_number + 1 RETURNING last_number`.
   - **PROHIBIDO** calcular consecutivos con `MAX() + 1`, `COUNT()` o contadores en memoria (fallaría bajo concurrencia).
3. **Validación Tabla 2 y Solicitud Previa (`previousRequestCode`):**
   - `RequestValidator` aplica `trim()`, valida `category` (`TRAMITE | ORIENTACION | ATENCION_ADMINISTRATIVA | REQUERIMIENTO_SERVICIO`), `subject` (`5..100`), `description` (`20..2000`) y `reason` en cancelación (`<= 500`).
   - Si se envía `previousRequestCode` no blanco: debe cumplir `^SOL-\d{4}-\d{5,}$`, existir en `service_requests`, tener `requester_id == actorId` y estar en `RESUELTA` o `CERRADA`. Si falla cualquier condición, devuelve **`400 Bad Request`** (`field = "previousRequestCode"`) y **jamás modifica la solicitud previa**.
4. **Patrones GoF Obligatorios (State y Builder):**
   - **Patrón State (`ServiceRequestState.kt`):** Modela los 6 estados (`AbiertaState`..`CanceladaState`). En `cancel(...)`, solo `AbiertaState` transiciona a `CANCELADA`; los otros 5 estados lanzan `ConflictException` (`409`). Si `requesterId != actorId`, lanza `ForbiddenException` (`403`).
   - La cancelación en `ExposedServiceRequestRepository` ejecuta `UPDATE service_requests SET status = 'CANCELADA', ... WHERE id = ? AND status = 'ABIERTA'`, verificando `updatedRows == 1` antes de insertar la entrada `CANCELACION` en la misma transacción.
   - **Patrón Builder:** `ServiceRequestBuilder` y `MyRequestsQueryBuilder` (`page >= 1`, `size in 1..20`).
5. **Privacidad Estricta `RF-03` en Historial del Solicitante:**
   - Al consultar `GET /api/v1/requests/{code}`, `ApplicantVisibleHistoryEntry` **NUNCA** incluye filas con `action == INTERVENCION` y **siempre** proyecta `comment = null` cuando `action IN {ESCALAMIENTO, REASIGNACION}`.
6. **Control RBAC y Rendimiento (`RNF-07`):**
   - Todas las rutas `/api/v1/requests*` exigen `authenticate("auth-jwt")` + `withRole(UserRole.SOLICITANTE)`.
   - Incluye prueba de integración sembrando `1.000` solicitudes y verificando respuesta `< 2 segundos` y `./gradlew ktlintCheck check koverVerify` ($\ge 90\%$).

---

### METODOLOGÍA DE TRABAJO

1. Verifica que `002-auth-users` pasa al 100%.
2. Avanza en el orden estricto `T01` -> `T07` de `specs/003-service-requests/tasks.md` manteniendo TDD.

Responde únicamente:  
*"ENTENDIDO. Verificando el estado de `002-auth-users` antes de iniciar la Fase 1 de `003-service-requests`."*

---

## Registro de Correcciones Críticas Frente a Desviaciones Comunes de la IA

| Desviación habitual de la IA | Por qué está mal según la Constitución / Spec | Corrección exigida a la IA |
| :--- | :--- | :--- |
| **Generar código `SOL-AAAA-NNNNN` con `COUNT(*)` o en Kotlin** | Produce duplicados (`23505`) ante solicitudes concurrentes. | *"Usa `INSERT ... ON CONFLICT (year) DO UPDATE SET last_number = last_number + 1 RETURNING last_number` en `request_code_sequences` dentro de la misma transacción."* |
| **Exponer comentarios internos o filas `INTERVENCION` en `GET /api/v1/requests/{code}`** | Viola directamente la regla de privacidad `RF-03` para el solicitante. | *"Filtra `action != INTERVENCION` en `ExposedServiceRequestRepository.findByCode` y fuerza `comment = null` en `ESCALAMIENTO` y `REASIGNACION`."* |
| **Usar `if/else` de estado dispersos en el servicio en lugar de `ServiceRequestState`** | Viola el patrón **State** exigido por la Constitución Backend. | *"Centraliza las reglas de transición y excepciones `403`/`409` en `sealed class ServiceRequestState` (`AbiertaState`, `EnRevisionState`, etc.)."* |
| **Permitir `previousRequestCode` en estado `ABIERTA` o `EN_REVISION` o de otro usuario** | Rompe la regla de vinculación con solicitudes cerradas/resueltas del propio solicitante. | *"Exige `previous.requesterId == actorId` y `previous.status in {RESUELTA, CERRADA}` retornando `ValidationException` (`400`) en `field = \"previousRequestCode\"`."* |
| **Aceptar `size > 20` en `GET /api/v1/requests/my`** | Viola el tope máximo de paginación de `RNF-07`. | *"Valida `page >= 1` y `size in 1..20` dentro de `MyRequestsQueryBuilder`."* |
