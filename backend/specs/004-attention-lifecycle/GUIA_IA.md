---
proyecto: ITM Service Desk
subproyecto: Backend
feature: 004-attention-lifecycle
categoria: Spec_Kit / Guia_IA
fecha: 2026-11-14
version: 1.0.0
estado: Aprobado
tags:
  - spec-kit
  - backend
  - guia-ia
  - prompt-maestro
  - attention-lifecycle
---

# Guía de Instrucciones para la IA (Prompt Maestro y Correcciones) — `004-attention-lifecycle` (Backend)

**Feature:** `004-attention-lifecycle`  
**Subproyecto:** Backend (Kotlin 2.4.x + Ktor 3.6.x + Exposed 1.6.x DSL + PostgreSQL 18)  
**Versión:** 1.0.0  
**Fecha:** 2026-11-14  
**Estado:** Aprobado  

> **Propósito de este archivo:** Contiene el **Prompt Maestro** para implementar la épica `004-attention-lifecycle` en el Backend (asignación y prioridad SLA, máquina de estados completa, intervenciones, escalamiento, reasignación sin reiniciar reloj, respuesta inmutable y cierre automático a las 24h) junto con el registro de correcciones críticas.

---

Actúa como un **Ingeniero Backend Senior experto en Kotlin 2.4.x, Exposed 1.6.x DSL, PostgreSQL 18 (`FOR UPDATE SKIP LOCKED`), Corutinas Ktor y Patrones GoF (State y Builder)**, trabajando bajo **Spec-Driven Development (SDD)** y TDD.

Tu misión es implementar la épica **`004-attention-lifecycle`** del Backend de **ITM Service Desk** siguiendo exactamente `specs/004-attention-lifecycle/tasks.md` (`T01` a `T07`).

---

### VERIFICACIÓN PREVIA DE CONTINUIDAD

1. Confirma que `001-project-setup`, `002-auth-users` y `003-service-requests` pasan `./gradlew ktlintCheck check koverVerify`.
2. Lee `specs/004-attention-lifecycle/{spec,research,data-model,contracts,plan,checklist,tasks}.md`.

---

### REGLAS INNEGOCIABLES (`004-attention-lifecycle`)

1. **Migración `V4__add_autoclose_partial_index.sql` y Configuración Tipada:**
   - Crea el índice parcial `idx_sr_autoclose ON service_requests (responded_at ASC, id ASC) WHERE status = 'RESUELTA'`.
   - Añade `AUTO_CLOSE_INTERVAL_SECONDS` (default `60`, validación `> 0`) a `.env.example`, `EnvConfig` y `AppConfigBuilder`.
2. **Reloj Inyectable `TimeProvider` (`America/Bogota` `-05:00`):**
   - Usa `BogotaTimeProvider` inyectado como `TimeProvider` en toda lógica de SLA, `isOverdue(now)`, cálculo de `respondedWithinSla` y cierre automático 24h.
   - **PROHIBIDO** llamar `OffsetDateTime.now()` directamente en servicios, dominio o repositorios (rompe la determinismo de pruebas de SLA y cierre 24h).
3. **Invariante Crítica de SLA y Reasignación (`firstAssignedAt` Inmutable):**
   - `RequestPriority`: `ALTA` = `24h`, `MEDIA` = `72h`, `BAJA` = `120h`.
   - En **Asignación (`ABIERTA -> EN_REVISION`)**: fija `firstAssignedAt = now`, `assignedAt = now` y `dueAt = firstAssignedAt + priority.slaHours`.
   - En **Reasignación tras escalamiento (`ESCALADA -> EN_REVISION`)**: **NUNCA reinicies el reloj SLA**. Preserva `firstAssignedAt` intacto y recalcula `dueAt = firstAssignedAt + effectivePriority.slaHours`.
   - `isOverdue(now)` (`status in {EN_REVISION, ESCALADA} && dueAt != null && now.isAfter(dueAt)`) es **estrictamente informativo**: jamás bloquea ni rechaza intervenciones, escalamientos, reasignaciones ni respuestas.
4. **Patrón State Completo, Propiedad y Validaciones (`AttentionValidator`):**
   - Completa los 6 estados en `ServiceRequestState`: solo `COORDINADOR` puede `assign`/`reassign`; solo el `PERSONAL_ATENCION` **asignado** (`assignedTo == actorId`) puede `addIntervention` (`10..1000` chars), `escalate` (`10..500` chars) y `respond` (`20..2000` chars, calcula `respondedWithinSla = !now.isAfter(dueAt)`, respuesta inmutable).
   - `AttentionService` valida además en base de datos que `assigneeId` pertenezca a un usuario con rol `PERSONAL_ATENCION` (`400` en caso contrario).
   - Toda transición en `ExposedAttentionRepository.applyTransitionWithHistory` usa `UPDATE ... WHERE id = ? AND status = expectedCurrentStatus` + `INSERT INTO request_history` en la misma transacción; si `updatedRows == 0`, lanza `ConflictException` (`409`).
5. **Bandejas, Privacidad RF-03 y Cierre Automático 24h Nativo (`SISTEMA`):**
   - `GET /api/v1/attention/coordination/pending` (`COORDINADOR`) ordena estrictamente por `created_at ASC, id ASC` (`ABIERTA` / `ESCALADA`).
   - Preserva intacta la privacidad de `GET /api/v1/requests/{code}` (`003`: sin intervenciones ni comentarios internos para solicitante), mientras `GET /api/v1/attention/requests/{code}` expone el historial completo incluyendo `"Sistema"`.
   - `AutoCloseService` ejecuta cierre en lotes con **`FOR UPDATE SKIP LOCKED`** (`responded_at <= now - 24h`, `actorId = null`, `actorRole = SISTEMA`, actorName `"Sistema"`, comentario `"Cierre automático"`).
   - `AutoCloseScheduler` usa exclusivamente corutinas nativas en Ktor (`ApplicationStarted` / `ApplicationStopping`). **PROHIBIDO** agregar Quartz, JobRunr o Redis.
6. **Calidad:** `./gradlew ktlintCheck check koverVerify` ($\ge 90\%$) con Testcontainers PostgreSQL 18.

---

### METODOLOGÍA DE TRABAJO

1. Verifica que `003-service-requests` pasa al 100%.
2. Ejecuta `specs/004-attention-lifecycle/tasks.md` en orden estricto `T01` -> `T07` bajo TDD.

Responde únicamente:  
*"ENTENDIDO. Verificando el estado de `003-service-requests` antes de iniciar la Fase 1 de `004-attention-lifecycle`."*

---

## Registro de Correcciones Críticas Frente a Desviaciones Comunes de la IA

| Desviación habitual de la IA | Por qué está mal según la Constitución / Spec | Corrección exigida a la IA |
| :--- | :--- | :--- |
| **Recalcular `dueAt = now + slaHours` en `reassign` tras escalamiento** | Viola `HU-04` / Tabla 5: el SLA no se reinicia cuando un caso escala y se reasigna. | *"En `ServiceRequestState.reassign`, calcula obligatoriamente `dueAt = request.firstAssignedAt + effectivePriority.slaHours` y no modifiques `first_assigned_at` en el `UPDATE`."* |
| **Bloquear `respond` o `escalate` cuando `isOverdue == true`** | El indicador de vencimiento es informativo (`HU-04.5`); las solicitudes vencidas deben poder atenderse y responderse (marcando `respondedWithinSla = false`). | *"Elimina cualquier validación que rechace transiciones por `dueAt < now`; simplemente marca `respondedWithinSla = !now.isAfter(dueAt)` al responder."* |
| **Usar `OffsetDateTime.now()` directo o `Thread.sleep` en tests del cierre automático 24h** | Hace las pruebas lentas, frágiles y dependientes del reloj del SO. | *"Inyecta `TimeProvider` en servicios y repositorios; en pruebas avanza un reloj falso (`FakeTimeProvider`) exactamente +24h."* |
| **Agregar Quartz o `kotlinx.coroutines` sin cancelación en `ApplicationStopping`** | Viola la restricción de no usar librerías externas de scheduling y provoca fugas al cerrar Ktor. | *"Implementa `AutoCloseScheduler` solo con corutinas nativas de Ktor suscritas a `ApplicationStarted` y canceladas en `ApplicationStopping`."* |
| **Permitir `SELECT ... WHERE status = 'RESUELTA'` sin `FOR UPDATE SKIP LOCKED` o filtrar N+1 por `actorName`** | Genera condiciones de carrera entre instancias o múltiples queries por fila de historial. | *"Usa `FOR UPDATE SKIP LOCKED` en el barrido automático y alias de `UsersTable` en el `LEFT JOIN` de `ExposedAttentionRepository`."* |
