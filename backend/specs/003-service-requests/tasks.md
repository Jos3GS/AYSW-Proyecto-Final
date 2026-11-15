# Checklist de Tareas Técnicas: 003-service-requests (Backend)
**Ubicación:** `specs/003-service-requests/tasks.md`  
**Plan Asociado:** `specs/003-service-requests/plan.md`  
**Estado:** Completado  
**Progreso:** 100% (7 / 7 tareas completadas)  

---

## Tareas de Implementación

### Fase 1: Migración `V3`, Tablas Exposed y Capa de Dominio
- [x] **T01: Crear migración Flyway `V3__create_service_requests_tables.sql` y definiciones de tablas Exposed DSL**
  - **Archivos:** `src/main/resources/db/migration/V3__create_service_requests_tables.sql`, `src/main/kotlin/.../repository/{RequestCodeSequencesTable.kt, ServiceRequestsTable.kt, RequestHistoryTable.kt}`
  - **Detalle:** Crear tablas `request_code_sequences`, `service_requests` (incluyendo columnas `NULL`ables de `004` con sus constraints `CHECK` y FKs) y `request_history` append-only más los índices compuestos `idx_sr_requester_created`, `idx_sr_requester_status_created`, `idx_sr_status_created`, `idx_sr_assigned_due` e `idx_rh_request_created`; definir sus correspondientes `Table` en Exposed 1.6.x DSL.
  - **Verificación:** `./gradlew ktlintCheck compileKotlin` finaliza con código de salida 0.

- [x] **T02: Implementar modelos de dominio, Patrón State (`ServiceRequestState`), Patrón Builder, `RequestValidator` y puerto `ServiceRequestRepository`**
  - **Archivos:** `src/main/kotlin/.../domain/model/{RequestCategory.kt, RequestStatus.kt, RequestPriority.kt, HistoryAction.kt, HistoryActorRole.kt, ServiceRequest.kt, ServiceRequestSummary.kt, ApplicantVisibleHistoryEntry.kt, ServiceRequestDetail.kt}`, `src/main/kotlin/.../domain/state/ServiceRequestState.kt`, `src/main/kotlin/.../domain/builder/{ServiceRequestBuilder.kt, MyRequestsQueryBuilder.kt}`, `src/main/kotlin/.../domain/validation/RequestValidator.kt`, `src/main/kotlin/.../domain/repository/ServiceRequestRepository.kt`
  - **Detalle:** Implementar enums y proyecciones limpias sin dependencias HTTP/SQL; implementar **Patrón State** (`AbiertaState` .. `CanceladaState`) controlando `cancel(...)` (`403` si no es propietario `SOLICITANTE`, `409` en estados distintos de `ABIERTA`); implementar **Patrón Builder** (`ServiceRequestBuilder`, `MyRequestsQueryBuilder` con `page >= 1`, `size in 1..20`); implementar `RequestValidator` con todas las reglas de Tabla 2, `trim()`, regex `^SOL-\d{4}-\d{5,}$` y `reason <= 500`.
  - **Verificación:** `./gradlew ktlintCheck compileKotlin` pasa sin errores ni importaciones de Ktor o Exposed dentro de `domain`.

### Fase 2: Suite TDD Inicial (Tests Unitarios e Integración en Rojo)
- [x] **T03: Escribir suite TDD (en rojo) para dominio, radicación, concurrencia, privacidad de historial, cancelación y rendimiento**
  - **Archivos:** `src/test/kotlin/.../{RequestValidatorTest.kt, ServiceRequestStateTest.kt, ServiceRequestBuilderTest.kt, ServiceRequestRoutingTest.kt}`
  - **Detalle:** Cubrir todos los escenarios BDD de `spec.md`: creación con `SOL-AAAA-NNNNN` y entrada `CREACION` (`201`/`400`), vinculación con `previousRequestCode` en `RESUELTA`/`CERRADA` vs rechazo (`400`) por formato inválido, inexistencia, otro propietario o estado inválido sin alterar la solicitud previa; concurrencia en generación de consecutivos sin duplicados; listado paginado máx. `20/pág` con filtro `status` (`200`/`400`); detalle con privacidad RF-03 (sin filas `INTERVENCION` y con `comment = null` en `ESCALAMIENTO`/`REASIGNACION`) y rechazo `403`/`404`; cancelación (`200`/`403`/`404`/`409`) + cancelación concurrente; control RBAC (`401`/`403` para `PERSONAL_ATENCION`/`COORDINADOR`); y prueba de rendimiento sobre `1000` solicitudes en `< 2s`.
  - **Verificación:** `./gradlew test` compila y falla en rojo por implementaciones faltantes (`./gradlew ktlintCheck` pasa en verde).

### Fase 3: Persistencia Exposed DSL y Servicio de Aplicación (TDD Verde)
- [x] **T04: Implementar `ExposedServiceRequestRepository` transaccional sobre PostgreSQL 18**
  - **Archivos:** `src/main/kotlin/.../repository/ExposedServiceRequestRepository.kt`
  - **Detalle:** Implementar generación atómica del consecutivo anual (`America/Bogota`) con `INSERT INTO request_code_sequences ... ON CONFLICT (year) DO UPDATE SET last_number = request_code_sequences.last_number + 1 RETURNING last_number` dentro de la transacción de `createWithHistory`; implementar `findByCode` con proyección `ApplicantVisibleHistoryEntry` filtrada según RF-03; implementar `findPagedByRequester` con orden `created_at DESC, id ASC`; implementar `cancelWithHistory` con `UPDATE service_requests ... WHERE id = ? AND status = 'ABIERTA'` verificando `updatedRows == 1` antes de insertar la fila append-only `CANCELACION` en `request_history`.
  - **Verificación:** `./gradlew test --tests "*RequestValidatorTest*" --tests "*ServiceRequestStateTest*" --tests "*ServiceRequestBuilderTest*"` y pruebas de repositorio contra Testcontainers PostgreSQL 18 pasan en verde.

- [x] **T05: Implementar `ServiceRequestService` con reglas de negocio y control de propiedad**
  - **Archivos:** `src/main/kotlin/.../service/ServiceRequestService.kt`
  - **Detalle:** Orquestar `create` (validación Tabla 2, resolución de `previousRequestCode` exigiendo existencia, propiedad y estado `RESUELTA`/`CERRADA` o `ValidationException` `400` sin mutar la previa, `ServiceRequestBuilder`, `createWithHistory`), `listMine` (`MyRequestsQueryBuilder`), `getDetail` (`NotFoundException("Solicitud no encontrada.")` o `ForbiddenException`) y `cancel` (`findByCode`, validación `ServiceRequestState.cancel`, `cancelWithHistory`, `ConflictException` si hubo carrera concurrente).
  - **Verificación:** `./gradlew test --tests "*RequestValidatorTest*" --tests "*ServiceRequestStateTest*" --tests "*ServiceRequestBuilderTest*" ktlintCheck` pasa en verde.

### Fase 4: Capa REST Ktor (`routing`) y Cableado
- [x] **T06: Implementar DTOs `@Serializable`, `ServiceRequestRouting` y cablear dependencias en `Application.kt`**
  - **Archivos:** `src/main/kotlin/.../routing/dto/requests/{CreateServiceRequestDto.kt, CancelServiceRequestDto.kt, RequestHistoryItemDto.kt, ServiceRequestDetailDto.kt, ServiceRequestSummaryDto.kt, PagedServiceRequestsResponseDto.kt}`, `src/main/kotlin/.../routing/ServiceRequestRouting.kt`, `src/main/kotlin/.../Application.kt`
  - **Detalle:** Exponer bajo `authenticate("auth-jwt")` + `withRole(UserRole.SOLICITANTE)` los endpoints `POST /api/v1/requests`, `GET /api/v1/requests`, `GET /api/v1/requests/{code}` y `POST /api/v1/requests/{code}/cancel`, manteniendo `Json { ignoreUnknownKeys = false }`, normalización y manejo uniforme de `ApiErrorDto`.
  - **Verificación:** `./gradlew test --tests "*ServiceRequestRoutingTest*" ktlintCheck` pasa al 100% en verde sobre Testcontainers PostgreSQL 18.

### Fase 5: Cierre Documental y Gate Final de Cobertura
- [x] **T07: Actualizar documentación operativa (`README.md`, `docs/version_map.md`) y validar `check` + `koverVerify`**
  - **Archivos:** `README.md`, `docs/version_map.md`, `specs/003-service-requests/checklist.md`
  - **Detalle:** Documentar endpoints `/api/v1/requests*` y estructura de `V3` en `README.md`, actualizar progreso de `003-service-requests` en `version_map.md` y comprobar cumplimiento de `RNF-05` y `RNF-07`.
  - **Verificación:** `./gradlew check koverVerify` finaliza con `0` errores en `ktlintCheck`, 100% de tests en verde sobre PostgreSQL 18 (Testcontainers) y cobertura global $\ge 90\%$ en JetBrains Kover.
