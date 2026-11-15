# Investigación Técnica y Spikes: 003-service-requests (Backend)
**Ubicación:** `specs/003-service-requests/research.md`  
**Spec Asociada:** `specs/003-service-requests/spec.md`  
**Estado:** Concluido (Aprobado)  

---

## 1. Preguntas de Investigación y Objetivos del Spike
1. **Generación atómica de código `SOL-AAAA-NNNNN` sin colisiones concurrentes:** ¿Cómo generar el consecutivo anual de 5 dígitos (`SOL-2026-00001`, reiniciando en `00001` al cambiar de año `AAAA` en `America/Bogota`) garantizando cero colisiones bajo múltiples creaciones paralelas?
2. **Diseño anticipado del esquema Flyway (`V3`):** ¿Conviene crear `service_requests` y `request_history` incluyendo ya las columnas de asignación, SLA, respuesta y cierre (nulas en `003`), o agregar columnas mediante migraciones adicionales en `004-attention-lifecycle`?
3. **Aplicación del Patrón GoF State desde `003` junto con control optimista en PostgreSQL:** ¿Cómo estructurar `ServiceRequestState` para gestionar `ABIERTA → CANCELADA` ahora y extender limpiamente las transiciones de `004-attention-lifecycle` evitando carreras entre asignación y cancelación?
4. **Privacidad del historial para el Solicitante (RF-03 regla 2):** ¿Cómo asegurar estructuralmente que las intervenciones internas y motivos de escalamiento almacenados en `request_history` jamás sean visibles en las respuestas del `SOLICITANTE`?
5. **Rendimiento con 1000+ solicitudes y paginación 20/pág (`< 2s`, RNF-07):** ¿Qué índices en PostgreSQL 18 y consultas Exposed 1.6.x DSL aseguran tiempos sub-segundo sin problemas N+1?

---

## 2. Alternativas Evaluadas y Trade-offs

| Decisión / Problema | Alternativas Evaluadas | Ventajas | Desventajas / Riesgos | Alineación con Constitución | Decisión |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Generación de código `SOL-AAAA-NNNNN`** | **Opción A:** `SELECT COUNT(*) + 1` o `MAX(code)` en Kotlin | No requiere tabla auxiliar | *Race condition* severa bajo concurrencia (`SQLState 23505` frecuente) | Riesgo de integridad | Descartada |
| | **Opción B:** Secuencia única global `CREATE SEQUENCE` | Rápida | No reinicia automáticamente a `00001` al cambiar el año `AAAA` sin lógica extra de mantenimiento | Menor fidelidad a `SOL-AAAA-NNNNN` | Descartada |
| | **Opción C:** Tabla atómica `request_code_sequences (year INT PK, last_number INT)` con `INSERT ... ON CONFLICT (year) DO UPDATE SET last_number = last_number + 1 RETURNING last_number` dentro de la misma transacción | Consecutivo 100% libre de colisiones por bloqueo de fila en PostgreSQL, reinicio automático por año en `America/Bogota` y cero dependencias | Bloqueo breve de fila en inserciones concurrentes (`< 1ms` por transacción corta) | Cumple RNF-05 / RNF-07 | **Seleccionada** |
| **Alcance del DDL `V3` (`service_requests` & `request_history`)** | **Opción A:** Solo columnas usadas en `003` y `ALTER TABLE` en `004` | Tablas mínimas en `003` | Impide probar completamente el `Escenario 2` (`previousRequestCode` en `RESUELTA`/`CERRADA`) y `Escenario 5` (`responseText`/`closedAt`) sin columnas reales | Dificulta pruebas integradas | Descartada |
| | **Opción B:** Esquema completo `service_requests`, `request_history` y `request_code_sequences` en `V3__create_service_requests_tables.sql` | Permite validar desde `003` respuestas/cierres, historial completo e integridad relacional de estados/prioridades (`CHECK`) sin migraciones disruptivas posteriores | Algunas columnas (`assigned_to`, `priority`, `due_at`, `response_text`) permanecen `NULL` hasta `004` | Cumple RNF-05 al 100% | **Seleccionada** |
| **Protección contra concurrencia en Cancelación vs Asignación** | **Opción A:** Validar `request.status == ABIERTA` solo en memoria Kotlin y hacer `UPDATE WHERE id = ?` | Simple | Si un coordinador asigna la solicitud milisegundos antes, `UPDATE` sobreescribiría `EN_REVISION` con `CANCELADA` | Viola Tabla 4 | Descartada |
| | **Opción B:** Validación de dominio vía **Patrón State** (`AbiertaState.cancel()`) + actualización condicional atómica `UPDATE ... WHERE id = ? AND status = 'ABIERTA'` en PostgreSQL | Garantiza invariantes tanto en el dominio puro como en concurrencia de base de datos, traduciendo `0 filas afectadas` a `ConflictException` (`409`) | Requiere validar `updatedRows == 1` antes de insertar en `request_history` | Cumple Constitución y RNF-05 | **Seleccionada** |
| **Ocultamiento de información interna al Solicitante (RF-03)** | **Opción A:** Devolver todas las filas de `request_history` y filtrar en Frontend | Menos código en Backend | Expondría intervenciones internas y motivos de escalamiento en el JSON (grave brecha `RNF-04` / `RF-03`) | Viola RF-03 y RNF-04 | Descartada |
| | **Opción B:** Columna `action` tipada + consulta/mapper exclusivo de visibilidad solicitante en Backend (`WHERE action <> 'INTERVENCION'` y comentario omitido en `ESCALAMIENTO` / `REASIGNACION`) | Jamás envía datos internos por red; el solicitante solo ve su propia creación, cambios de estado y respuesta pública | Requiere dos proyecciones de historial al llegar `004` (`Applicant` vs `Internal`) | Cumple RF-03 / RNF-04 | **Seleccionada** |

---

## 3. Análisis Técnico y Hallazgos

* **Hallazgo 1 — Validación y Normalización de Tabla 2 y `previousRequestCode`:**
  * `RequestValidator` en `domain/validation` aplicará `trim()` sobre `subject`, `description` y `previousRequestCode` (si no es nulo/vacío) validando en una sola pasada (`details[]`):
    - `category`: obligatorio, debe pertenecer a `{TRAMITE, ORIENTACION, ATENCION_ADMINISTRATIVA, REQUERIMIENTO_SERVICIO}`.
    - `subject`: `5..100` caracteres tras `trim()`.
    - `description`: `20..2000` caracteres tras `trim()`.
    - `previousRequestCode`: opcional; si se envía no en blanco, debe cumplir el patrón `^SOL-\d{4}-\d{5,}$` (y además en `ServiceRequestService` se verifica que exista en BD, pertenezca al mismo `requesterId` y tenga estado `RESUELTA` o `CERRADA`, devolviendo **`400 Bad Request`** en `details[]` si falla cualquiera de estas reglas según HU-01 criterio 6).
  * Con `Json { ignoreUnknownKeys = false }` (configurado en `001`/`002`), si un cliente intenta enviar `code`, `status`, `createdAt` o `requesterId`, Ktor responderá automáticamente `400 Bad Request`.

* **Hallazgo 2 — Patrón GoF State (`ServiceRequestState`) y Builder (`ServiceRequestBuilder`, `MyRequestsQueryBuilder`):**
  * Se define la jerarquía sellada `ServiceRequestState` en `domain/state/`: `AbiertaState`, `EnRevisionState`, `EscaladaState`, `ResueltaState`, `CerradaState`, `CanceladaState`.
  * En `003-service-requests`, el método `cancel(actorRole, isOwner)` solo permite la transición en `AbiertaState` cuando `isOwner == true && actorRole == SOLICITANTE`; en el resto de estados lanza `ConflictException("Solo es posible cancelar una solicitud que se encuentre en estado Abierta.")` (`HTTP 409`). Esta misma jerarquía se ampliará en `004` con `assign`, `intervene`, `escalate`, `respond`, `reassign` y `autoClose`.
  * `ServiceRequestBuilder` construirá la nueva instancia inmutable tras generar el código `SOL-AAAA-NNNNN`, fijando zona `America/Bogota`, estado `ABIERTA` y vinculación validada con la solicitud anterior (sin modificarla).

* **Hallazgo 3 — Atomicidad Transaccional y Auditoría Inmutable (RNF-05):**
  * `POST /api/v1/requests` ejecutará en una sola transacción Exposed (`dbQuery`):
    1. Generación del consecutivo `last_number` para el año actual en `America/Bogota`.
    2. `INSERT` en `service_requests`.
    3. `INSERT` en `request_history` (`action = CREACION`, `previous_state = NULL`, `new_state = ABIERTA`, `actor_id = requesterId`, `actor_role = SOLICITANTE`).
  * `POST /api/v1/requests/{code}/cancel` ejecutará en una sola transacción:
    1. Validación de existencia (`404`), propiedad (`403`) y transición State (`409`).
    2. `UPDATE` condicional `WHERE id = ? AND status = 'ABIERTA'`.
    3. `INSERT` en `request_history` (`action = CANCELACION`, `previous_state = ABIERTA`, `new_state = CANCELADA`).
  * No existirán métodos `delete` ni `update` sobre `RequestHistoryTable` ni `delete` sobre `ServiceRequestsTable`.

* **Hallazgo 4 — Estrategia de Índices y Consultas `< 2s` con 1000 Solicitudes (RNF-07):**
  * `service_requests` contará con: `UNIQUE(code)`, índice `(requester_id, created_at DESC, id ASC)` y `(requester_id, status, created_at DESC, id ASC)` para `GET /api/v1/requests/my`.
  * `request_history` contará con índice `(request_id, created_at ASC, id ASC)` para recuperar la línea de tiempo completa de una solicitud en una sola consulta ordenada sin N+1.

---

## 4. Decisión Final Justificada
> Se adopta la migración **`V3__create_service_requests_tables.sql`** con las tablas `service_requests`, `request_history` y `request_code_sequences`, garantizando consecutivos `SOL-AAAA-NNNNN` atómicos por año en `America/Bogota` mediante `INSERT ... ON CONFLICT DO UPDATE RETURNING`, atomicidad transaccional única entre entidad e historial (RNF-05), filtrado estricto en servidor de intervenciones internas y motivos de escalamiento hacia el solicitante (RF-03), y aplicación explícita de los patrones **GoF Builder** (`ServiceRequestBuilder`, `MyRequestsQueryBuilder`) y **GoF State** (`ServiceRequestState` con transición atómica `ABIERTA → CANCELADA` protegida ante concurrencia con `UPDATE ... WHERE status = 'ABIERTA'`).
