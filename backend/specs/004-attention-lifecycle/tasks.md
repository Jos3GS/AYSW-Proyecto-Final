# Checklist de Tareas Técnicas: 004-attention-lifecycle (Backend)
**Ubicación en Repositorio:** `backend/specs/004-attention-lifecycle/tasks.md`  
**Plan Asociado:** `backend/specs/004-attention-lifecycle/plan.md`  
**Estado:** Completado  
**Progreso:** 100% (7 / 7 tareas completadas)  

---

## Tareas de Implementación

### Fase 1: Migración `V4`, `TimeProvider` y Extensión de Dominio (State + Builder + Validador)
- [x] **T01: Crear migración Flyway `V4__add_autoclose_partial_index.sql` y configurar `AUTO_CLOSE_INTERVAL_SECONDS`**
  - **Archivos:** `src/main/resources/db/migration/V4__add_autoclose_partial_index.sql`, `.env.example`, `src/main/kotlin/.../config/EnvConfig.kt`, `src/main/kotlin/.../domain/builder/AppConfigBuilder.kt`
  - **Detalle:** Crear índice parcial `idx_sr_autoclose ON service_requests (responded_at ASC, id ASC) WHERE status = 'RESUELTA'`; añadir `AUTO_CLOSE_INTERVAL_SECONDS=60` a la configuración tipada con validación `> 0`.
  - **Verificación:** `./gradlew ktlintCheck compileKotlin` finaliza con código de salida 0.

- [x] **T02: Implementar `TimeProvider`, modelos de atención, State completo (`ServiceRequestState`), `AttentionQueryBuilder`, `AttentionValidator` y puerto `AttentionRepository`**
  - **Archivos:** `src/main/kotlin/.../domain/time/{TimeProvider.kt, BogotaTimeProvider.kt}`, `domain/model/{RequestPriority.kt, ServiceRequest.kt, AttentionModels.kt}`, `domain/state/ServiceRequestState.kt`, `domain/builder/AttentionQueryBuilder.kt`, `domain/validation/AttentionValidator.kt`, `domain/repository/AttentionRepository.kt`
  - **Detalle:** Enriquecer `RequestPriority` con `slaHours` (`24`/`72`/`120`); completar `ServiceRequestState` con `assign`, `addIntervention`, `escalate`, `reassign`, `respond` y `autoClose`; garantizar en `reassign` que `dueAt` se recalcule siempre desde `firstAssignedAt`; implementar `AttentionQueryBuilder` (`page >= 1`, `size in 1..20`) y `AttentionValidator` (`comment` `10..1000`, `reason` `10..500`, `responseText` `20..2000`).
  - **Verificación:** `./gradlew ktlintCheck compileKotlin` pasa sin errores ni dependencias hacia Ktor/Exposed en `domain`.

### Fase 2: Suite TDD Inicial (Tests Unitarios e Integración en Rojo)
- [x] **T03: Escribir suite TDD (en rojo) para SLA, máquina de estados completa, cierre automático 24h, privacidad RF-03, concurrencia y `1000` solicitudes**
  - **Archivos:** `src/test/kotlin/.../{AttentionValidatorTest.kt, ServiceRequestStateLifecycleTest.kt, AttentionQueryBuilderTest.kt, AutoCloseServiceTest.kt, AttentionRoutingTest.kt}`
  - **Detalle:** Cubrir todos los escenarios BDD (`Escenario 1` a `Escenario 7`): orden `created_at ASC` en Coordinación (`ABIERTA`+`ESCALADA`); asignación con cálculo `24h`/`72h`/`120h` y rechazo de usuario no `PERSONAL_ATENCION` (`400`); intervenciones (`10..1000`) ocultas para solicitante (`GET /api/v1/requests/{code}`) pero visibles en `GET /api/v1/attention/requests/{code}`; escalamiento (`10..500`); reasignación recalculando `dueAt` desde `firstAssignedAt` sin reiniciar reloj; `isOverdue` informativo no bloqueante; respuesta final inmutable (`20..2000`) con `respondedWithinSla = true/false`; cierre automático exacto a `>= 24h` por `SISTEMA` (`actor_id = NULL`, `"Cierre automático"`); concurrencia `409`; matriz `403`; y consulta `< 2s` con `1000` filas.
  - **Verificación:** `./gradlew test` compila y falla inicialmente en rojo por implementaciones faltantes (`./gradlew ktlintCheck` pasa en verde).

### Fase 3: Persistencia Exposed DSL y Servicios (`AttentionService`, `AutoCloseService`, `AutoCloseScheduler`)
- [x] **T04: Implementar `ExposedAttentionRepository` sobre PostgreSQL 18 (Testcontainers)**
  - **Archivos:** `src/main/kotlin/.../repository/ExposedAttentionRepository.kt`
  - **Detalle:** Implementar listados paginados sin N+1 mediante alias de `UsersTable`, detalle interno con historial cronológico completo y nombres de actor (`"Sistema"` para `SISTEMA`), `applyTransitionWithHistory` con `UPDATE ... WHERE id = ? AND status = ?` + `INSERT INTO request_history` en la misma transacción, y `autoCloseEligibleRequests` con `FOR UPDATE SKIP LOCKED`.
  - **Verificación:** `./gradlew test --tests "*AttentionValidatorTest*" --tests "*ServiceRequestStateLifecycleTest*" --tests "*AttentionQueryBuilderTest*"` y pruebas de repositorio pasan en verde.

- [x] **T05: Implementar `AttentionService`, `AutoCloseService` y `AutoCloseScheduler`**
  - **Archivos:** `src/main/kotlin/.../service/{AttentionService.kt, AutoCloseService.kt, AutoCloseScheduler.kt}`
  - **Detalle:** Validar existencia y rol `PERSONAL_ATENCION` del `assigneeId` antes de asignar/reasignar; orquestar transiciones con `TimeProvider` en `America/Bogota` y lanzamiento de `ConflictException` si hubo carrera SQL (`null`); implementar barrido transaccional de cierre 24h y scheduler basado en corutinas Ktor con cancelación limpia en apagado.
  - **Verificación:** `./gradlew test --tests "*AutoCloseServiceTest*" ktlintCheck` pasa en verde.

### Fase 4: Capa REST Ktor (`routing`) y Cableado
- [x] **T06: Implementar DTOs `@Serializable`, `AttentionRouting` y cablear dependencias/scheduler en `Application.kt`**
  - **Archivos:** `src/main/kotlin/.../routing/dto/attention/*`, `src/main/kotlin/.../routing/AttentionRouting.kt`, `src/main/kotlin/.../Application.kt`
  - **Detalle:** Exponer bajo `authenticate("auth-jwt")` y `withRole(...)` los 8 endpoints `/api/v1/attention/*` con `Json { ignoreUnknownKeys = false }`, y registrar `ApplicationStarted` / `ApplicationStopping` para `AutoCloseScheduler`.
  - **Verificación:** `./gradlew test --tests "*AttentionRoutingTest*" ktlintCheck` pasa al 100% en verde sobre Testcontainers PostgreSQL 18.

### Fase 5: Cierre Documental y Gate Final de Cobertura
- [x] **T07: Actualizar documentación operativa (`README.md`, `docs/version_map.md`) y validar `check` + `koverVerify`**
  - **Archivos:** `README.md`, `docs/version_map.md`, `specs/004-attention-lifecycle/checklist.md`
  - **Detalle:** Documentar endpoints `/api/v1/attention/*`, política SLA, `AUTO_CLOSE_INTERVAL_SECONDS` y cierre automático `24h`; actualizar avance de `004-attention-lifecycle` en `version_map.md`.
  - **Verificación:** `./gradlew check koverVerify` finaliza con `0` errores en `ktlintCheck`, 100% de tests en verde sobre PostgreSQL 18 (Testcontainers) y cobertura global $\ge 90\%$ en JetBrains Kover.
