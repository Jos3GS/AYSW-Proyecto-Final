# Investigación Técnica y Spikes: 004-attention-lifecycle (Backend)
**Ubicación en Repositorio:** `backend/specs/004-attention-lifecycle/research.md`  
**Spec Asociada:** `backend/specs/004-attention-lifecycle/spec.md`  
**Estado:** Concluido (Aprobado)  

---

## 1. Preguntas de Investigación y Objetivos del Spike
1. **Regla SLA sin reinicio de reloj al reasignar (`first_assigned_at` — HU-03.5 / Tabla 5):** ¿Cómo garantizar que al escalar y reasignar una solicitud (incluso cambiando su prioridad) el vencimiento `dueAt` se recalcule siempre desde `firstAssignedAt` y nunca desde la hora de reasignación?
2. **Cierre automático a las 24 horas (`RESUELTA -> CERRADA`) sin librerías prohibidas por la Constitución:** ¿Cómo implementar la tarea periódica de cierre (`actor = SISTEMA`) cumpliendo la Constitución (sin Quartz, Redis ni cron externo), garantizando seguridad ante concurrencia e idempotencia?
3. **Extensión del Patrón GoF State (`ServiceRequestState`) y carreras concurrentes:** ¿Cómo unificar las 7 transiciones/operaciones (`cancel`, `assign`, `addIntervention`, `escalate`, `reassign`, `respond`, `autoClose`) bajo **State** y respaldo atómico SQL `WHERE id = ? AND status = ?`?
4. **Proyección de historial completo con nombres de actor y bandeja ordenada `< 2s` (`RNF-07`):** ¿Cómo evitar problemas N+1 al devolver listas paginadas de Coordinación/Agente y el detalle interno con nombres de solicitante, responsable y actores del historial?
5. **Pruebas deterministas de SLA (`24h`/`72h`/`120h`), vencimiento y cierre `24h`:** ¿Cómo probar expiración SLA y cierre automático a las 24 horas exactas en Testcontainers sin `Thread.sleep` frágiles?

---

## 2. Alternativas Evaluadas y Trade-offs

| Decisión / Problema | Alternativas Evaluadas | Ventajas | Desventajas / Riesgos | Alineación con Constitución | Decisión |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Mecanismo de Cierre Automático 24h (`RESUELTA -> CERRADA`)** | **Opción A:** Quartz Scheduler / JobRunr / cron externo | Maduro en grandes clústeres | Viola explícitamente la Constitución del Backend (dependencias externas de scheduling prohibidas) | Prohibido | Descartada |
| | **Opción B:** Evaluación únicamente "lazy" al consultar `GET` | Cero procesos en segundo plano | Reportes (`005`) o consultas de tabla seguirían viendo `RESUELTA` pasado 24h si nadie abre el caso | Inconsistente para reportes | Descartada |
| | **Opción C:** `AutoCloseService.closeEligibleRequests()` transaccional + `AutoCloseScheduler` en corutina nativa de Ktor (`ApplicationStarted`/`ApplicationStopping`, intervalo configurable en `EnvConfig`) con bloqueo SQL `FOR UPDATE SKIP LOCKED` | 100% Kotlin + corutinas estándar, cero librerías nuevas, seguro frente a múltiples instancias e invocable sincrónicamente en tests | Requiere manejar cancelación limpia de corutina al apagar Ktor | Cumple Constitución y RNF-05 | **Seleccionada** |
| **Reloj de tiempo para cálculo SLA y cierre 24h** | **Opción A:** Llamar `OffsetDateTime.now()` / `Instant.now()` directo en métodos de dominio y servicio | Menos interfaces | Imposibilita probar `respondedWithinSla = false`, `isOverdue = true` y cierre a las 24h sin esperar o manipular SQL manualmente en cada caso | Dificulta Kover >= 90% | Descartada |
| | **Opción B:** Puerto `TimeProvider` en `domain/time/` (`SystemTimeProvider` en prod en `America/Bogota`, fake controlable en tests) | Pruebas 100% deterministas en milisegundos para `24h`, `72h`, `120h` y cierre a `24h` exactas | Una interfaz mínima de un método | Cumple Layered Architecture | **Seleccionada** |
| **Consulta de pendientes de Coordinación (`ABIERTA` + `ESCALADA`)** | **Opción A:** Dos queries separadas y ordenación en memoria | Sencillo | Rompe paginación SQL (`OFFSET`/`LIMIT`) y `totalItems` con 1000+ filas | Viola RNF-07 | Descartada |
| | **Opción B:** Consulta única Exposed DSL `WHERE status IN ('ABIERTA', 'ESCALADA') ORDER BY created_at ASC, id ASC` con `LEFT JOIN users` para `assignedToName` y `requesterName` | Paginación exacta en PostgreSQL, aprovechamiento de `idx_sr_status_created` y cero N+1 | Requiere alias de `UsersTable` en Exposed (`requesterUser` vs `assignedUser`) | Cumple Exposed DSL y RNF-07 | **Seleccionada** |

---

## 3. Análisis Técnico y Hallazgos

* **Hallazgo 1 — Reglas Exactas de Prioridad y Cálculo de Vencimiento SLA (`Tabla 5` y `HU-03.5`):**
  * Duraciones fijas: `ALTA -> 24 horas`, `MEDIA -> 72 horas`, `BAJA -> 120 horas`.
  * En **asignación inicial (`ABIERTA -> EN_REVISION`)**: `firstAssignedAt = now`, `assignedAt = now`, `dueAt = firstAssignedAt + duration(priority)`.
  * En **reasignación (`ESCALADA -> EN_REVISION`)**: `firstAssignedAt` es **inmutable**; se actualiza `assignedTo = newAssigneeId`, `assignedAt = now`, `priority = newPriority ?: currentPriority`, y **`dueAt = firstAssignedAt + duration(effectivePriority)`**. Si al reducir prioridad (ej. `BAJA -> ALTA`) el nuevo `dueAt` ya quedó en el pasado respecto a `now`, el sistema lo acepta e inmediatamente marca `isOverdue = true` (informativo, sin bloquear la operación).

* **Hallazgo 2 — Cierre Automático Transaccional Idempotente (`AutoCloseService`):**
  * La consulta transaccional selecciona las filas elegibles mediante `WHERE status = 'RESUELTA' AND responded_at <= cutoff` (`cutoff = now - 24h`) con bloqueo optimista/exclusivo (`FOR UPDATE SKIP LOCKED` o `UPDATE ... WHERE id = ? AND status = 'RESUELTA'`), actualiza `status = 'CERRADA'`, `closed_at = now`, `updated_at = now` e inserta para cada una su entrada append-only en `request_history`:
    - `actor_id = NULL` *(ya permitido por `V3`)*
    - `actor_role = 'SISTEMA'`
    - `action = 'CIERRE_AUTOMATICO'`
    - `previous_state = 'RESUELTA'`
    - `new_state = 'CERRADA'`
    - `comment = "Cierre automático"`
  * El `AutoCloseScheduler` se inicia en `ApplicationStarted`, ejecuta cada `AUTO_CLOSE_INTERVAL_SECONDS` (default `60s` en `.env.example`), captura excepciones transitorias para no tumbar el servidor y cancela su `Job` en `ApplicationStopping`.

* **Hallazgo 3 — Control Estricto de Propiedad Asignada y Estados (`ServiceRequestState`):**
  * Ninguna operación de agente (`interventions`, `escalate`, `respond`) puede ser ejecutada por un `COORDINADOR` ni por otro `PERSONAL_ATENCION` distinto a `assignedTo`: todos reciben `HTTP 403 Forbidden`.
  * Toda operación sobre estado inválido devuelve `HTTP 409 Conflict`:
    - `assign`: solo `ABIERTA` (`409` en cualquier otro estado).
    - `reassign`: solo `ESCALADA` (`409` en cualquier otro estado).
    - `interventions`, `escalate`, `respond`: solo `EN_REVISION` (`409` en `ABIERTA`, `ESCALADA`, `RESUELTA`, `CERRADA`, `CANCELADA`).
  * `assign` y `reassign` validan además que `assigneeId` corresponda a un usuario registrado en `users` con `role == 'PERSONAL_ATENCION'`; si no existe o tiene rol `SOLICITANTE` / `COORDINADOR` responden `HTTP 400 Bad Request`.

* **Hallazgo 4 — Esquema de Base de Datos (`V3` Suficiente sin Cambios Destructivos):**
  * Se verifica que la migración `V3__create_service_requests_tables.sql` (aprobada en `003`) ya incluyó todas las columnas necesarias (`assigned_to`, `priority`, `first_assigned_at`, `assigned_at`, `due_at`, `response_text`, `responded_at`, `responded_within_sla`, `closed_at`), los `CHECK` constraints de los 6 estados/3 prioridades/8 acciones/4 roles (incluyendo `SISTEMA`), y los índices `idx_sr_status_created` e `idx_sr_assigned_due`. Por tanto `004-attention-lifecycle` **no requiere alterar columnas existentes** y puede añadir opcionalmente en `V4` un índice parcial específico para el barrido de cierre automático (`CREATE INDEX IF NOT EXISTS idx_sr_autoclose ON service_requests (responded_at) WHERE status = 'RESUELTA'`).

---

## 4. Decisión Final Justificada
> Se implementa el ciclo completo de atención (`/api/v1/attention/*`) extendiendo el **Patrón GoF State** (`ServiceRequestState`) y aplicando **Patrón GoF Builder** (`AttentionQueryBuilder`) sobre Exposed 1.6.x DSL y la estructura `V3` (complementada con un índice parcial `V4` para `status = 'RESUELTA'`), manteniendo cálculo de SLA anclado invariablemente a `first_assigned_at`, marca informativa `isOverdue` no bloqueante, `TimeProvider` inyectable en `America/Bogota`, auditoría transaccional append-only en `request_history` (`RNF-05`), y `AutoCloseScheduler` nativo con corutinas Ktor para cerrar a las 24h como `SISTEMA` cumpliendo 100% la Constitución.
