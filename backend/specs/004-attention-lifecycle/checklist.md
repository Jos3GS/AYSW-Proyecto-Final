# Checklist de Calidad y Puertas de Aprobación (Quality Gates): 004-attention-lifecycle (Backend)
**Ubicación en Repositorio:** `backend/specs/004-attention-lifecycle/checklist.md`  
**Spec Asociada:** `backend/specs/004-attention-lifecycle/spec.md`  
**Estado:** Aprobado  

---

## 1. Validación contra la Constitución
- [x] El diseño técnico utiliza únicamente el stack autorizado: **Kotlin 2.4.x**, **JDK 21 LTS**, **Ktor 3.6.x (Netty)**, **`kotlinx.serialization.json`**, **PostgreSQL 18.x**, **Exposed 1.6.x (DSL)**, **HikariCP**, **Flyway**, **Ktor Auth JWT**, **`ktlint` 1.8.x** y **Testcontainers PostgreSQL**.
- [x] No se incorporan dependencias externas prohibidas para tareas programadas (`AutoCloseScheduler` utiliza exclusivamente corutinas estándar dentro del ciclo de vida Ktor).
- [x] Se respeta estrictamente **Layered Architecture** (`routing -> service -> domain` y `service -> repository -> domain`), manteniendo `domain` desacoplado de Ktor y Exposed (incluyendo el puerto `TimeProvider` en zona `America/Bogota`).
- [x] Se aplican los patrones GoF obligatorios: **State** completo (`ServiceRequestState` gobernando todas las transiciones de Tabla 4) y **Builder** (`AttentionQueryBuilder`, actualización de `AppConfigBuilder`).
- [x] Se mantienen las reglas estrictas de `org.jlleitschuh.gradle.ktlint` (cero importaciones `*`, 4 espacios, trailing commas, máx. 120 caracteres) y cobertura global $\ge 90\%$ con **JetBrains Kover**.

## 2. Completitud de la Especificación, Contratos, SLA e Integridad
- [x] Todos los escenarios BDD (`Escenario 1` a `Escenario 7`) de `spec.md` cubren **HU-03** (bandeja de pendientes `ABIERTA` + `ESCALADA` de más antigua a más reciente, asignación inicial, SLA `24h`/`72h`/`120h`, reasignación sin reinicio de reloj), **HU-04** (bandeja asignada, marca informativa `isOverdue`, intervenciones `10..1000`, escalamiento `10..500`) y **HU-05** (respuesta final inmutable `20..2000`, cálculo `respondedWithinSla` y cierre automático `24h` como `SISTEMA`).
- [x] Los 8 endpoints REST JSON de `contracts.md` (`/api/v1/attention/coordination/pending`, `/api/v1/attention/my-assigned`, `/api/v1/attention/requests/{code}` y las acciones `assign`, `reassign`, `interventions`, `escalate`, `respond`) especifican su matriz RBAC y códigos de error (`400`, `401`, `403`, `404`, `409`) alineados con `ApiErrorDto`.
- [x] Las reglas de propiedad y prioridad están blindadas: `assign` y `reassign` solo aceptan `assigneeId` de usuarios con rol `PERSONAL_ATENCION` (`400` en otro caso); intervenciones, escalamientos y respuestas exigen `actorRole == PERSONAL_ATENCION && assignedTo == actorId` (`403` para cualquier otro usuario, incluido `COORDINADOR`); y `dueAt` en reasignación siempre se recalcula tomando como base inmutable `firstAssignedAt`.
- [x] La atomicidad transaccional e inmutabilidad (`RNF-05`) están cubiertas: todas las transiciones usan `UPDATE ... WHERE id = ? AND status = ?` más inserción append-only en `request_history`, el cierre automático usa `FOR UPDATE SKIP LOCKED` apoyado por el índice parcial `V4__add_autoclose_partial_index.sql`, y `isOverdue == true` nunca bloquea operaciones.
- [x] El rendimiento (`RNF-07 < 2s` con 1000 registros, `size <= 20`) se respalda mediante joins sin N+1 sobre índices dedicados y pruebas de carga en Testcontainers PostgreSQL 18.
- [x] No quedan puntos pendientes marcados con `[NECESITA ACLARACIÓN]`.

## 3. Preparación para Implementación (`tasks.md`)
- [ ] El plan de tareas atómicas (`specs/004-attention-lifecycle/tasks.md`) está redactado y aprobado en orden TDD.
- [ ] Cada tarea de `tasks.md` define su criterio de verificación ejecutable (`ktlintCheck`, `./gradlew test`, `koverVerify`).
- [ ] Aprobación explícita final para iniciar la implementación de `004-attention-lifecycle` (Backend) *(una vez completada la planificación del resto de épicas o autorizada la fase de código)*.
