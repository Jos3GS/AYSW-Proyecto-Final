# ITM Service Desk — Backend API

API RESTful del sistema **ITM Service Desk**, construida con **Kotlin 2.4.x**, **JDK 21 LTS**, **Ktor Server 3.6.x (Netty)**, **Exposed 1.6.x DSL**, **HikariCP**, **Flyway** y **PostgreSQL 18.x**.

---

## Arquitectura y Reglas del Proyecto

- **Arquitectura en Capas (`Layered Architecture`)** con flujo unidireccional estricto:
  - `routing -> service -> domain`
  - `service -> repository -> domain`
  - `domain/` es completamente independiente de Ktor, Exposed, HikariCP y JDBC.
- **Persistencia:** Exclusivamente **Exposed 1.6.x DSL** (`UsersTable`, `RevokedTokensTable`, `RequestCodeSequencesTable`, `ServiceRequestsTable`, `RequestHistoryTable`, `ExposedHealthRepository`, `ExposedUserSeedRepository`, `ExposedUserRepository`, `ExposedRevokedTokenRepository`, `ExposedServiceRequestRepository`, `ExposedAttentionRepository`, `ExposedReportRepository`) + **HikariCP** (`connectionTimeout = 2000ms`, `validationTimeout = 1500ms`) + **Flyway** (`V1__create_users_table.sql`, `V2__create_revoked_tokens_table.sql`, `V3__create_service_requests_tables.sql`, `V4__add_autoclose_partial_index.sql`, `V5__add_coordination_reports_indexes.sql`).
- **Configuración y Secretos:** `EnvConfig` (Kotlin puro, prioridad a `System.getenv()` con fallback a `.env`) + `AppConfigBuilder` (Patrón GoF **Builder**), ocultando contraseñas, cadenas JDBC y secretos en `toString()`. Incluye `AUTO_CLOSE_INTERVAL_SECONDS=60` (`> 0`).
- **Autenticación, Blacklist y RBAC (`002-auth-users`):**
  - **JWT (`HMAC256`, `exp = 1h / 3600s`):** Configurado con `JWT_EXPIRATION_SECONDS=3600`, emisión de `jti` UUID único por `register` / `login` y validación en `JwtAuthConfig` contra la blacklist persistente `revoked_tokens` (con purga automática `expires_at < NOW()` en `logout`).
  - **BCrypt (`0.10.2`, costo 12):** Salt aleatorio en servidor, validación de límite UTF-8 (`<= 72` bytes) y verificación contra hash dummy en `login` para evitar enumeración temporal.
  - **RBAC:** `RoleAuthorizationPlugin` (`withRole(...)`) + validación en `service`, impidiendo escalamiento de privilegios en `register` (siempre `SOLICITANTE`, `ignoreUnknownKeys = false`) y restringiendo `/api/v1/users/staff` a `COORDINADOR`.
- **Solicitudes de Servicio y Trazabilidad (`003-service-requests`):**
  - **Consecutivo Atómico (`SOL-AAAA-NNNNN`):** Generado por año vigente en `America/Bogota` mediante `INSERT INTO request_code_sequences ... ON CONFLICT (year) DO UPDATE SET last_number = request_code_sequences.last_number + 1 RETURNING last_number` dentro de la misma transacción de creación.
  - **Patrones GoF State & Builder:** `ServiceRequestState` (`AbiertaState`..`CanceladaState`) gobierna la cancelación (`ABIERTA -> CANCELADA`, `403` / `409`) con `UPDATE ... WHERE id = ? AND status = 'ABIERTA'`; `ServiceRequestBuilder` y `MyRequestsQueryBuilder` (`page >= 1`, `size in 1..20`) garantizan construcción inmutable y paginación acotada (`RNF-07`).
  - **Privacidad e Inmutabilidad (`RF-03` / `RNF-05`):** `request_history` es estrictamente append-only; `ApplicantVisibleHistoryEntry` excluye filas `INTERVENCION` y proyecta `comment = null` en `ESCALAMIENTO` y `REASIGNACION`. `previousRequestCode` exige existencia, propiedad y estado `RESUELTA` o `CERRADA` (`400` en caso contrario) sin mutar la solicitud previa.
- **Ciclo de Vida de Atención, SLA y Cierre Automático (`004-attention-lifecycle`):**
  - **Reloj Inyectable (`TimeProvider` / `BogotaTimeProvider`):** Toda lógica de SLA, cálculo de `isOverdue(now)`, `respondedWithinSla` y cierre automático 24h usa `America/Bogota` (`-05:00`) vía `TimeProvider` inyectado (determinista para pruebas).
  - **Política SLA e Invariante de Reasignación:** `ALTA = 24h`, `MEDIA = 72h`, `BAJA = 120h`. Al asignar (`ABIERTA -> EN_REVISION`) se fija `firstAssignedAt = now` y `dueAt = firstAssignedAt + slaHours`. Al reasignar tras escalamiento (`ESCALADA -> EN_REVISION`), **`firstAssignedAt` jamás se reinicia** y `dueAt` se recalcula como `firstAssignedAt + effectivePriority.slaHours`. `isOverdue` es estrictamente informativo (nunca bloquea transiciones).
  - **Propiedad, Validaciones y Atomicidad:** Solo `COORDINADOR` puede asignar/reasignar (validando en BD usuario activo `PERSONAL_ATENCION`, `400` en caso contrario). Solo el `PERSONAL_ATENCION` asignado (`assignedTo == actorId`) puede registrar intervenciones (`10..1000`), escalar (`10..500`) o emitir respuesta final inmutable (`20..2000`). Todas las transiciones usan `UPDATE ... WHERE id = ? AND status = ?` + `INSERT INTO request_history` (`409` ante carrera).
  - **Cierre Automático `24h` Nativo (`SISTEMA`):** `AutoCloseScheduler` corre con corutinas nativas de Ktor (`ApplicationStarted` / `ApplicationStopping`, cada `AUTO_CLOSE_INTERVAL_SECONDS`), ejecutando `AutoCloseService` en lotes con `FOR UPDATE SKIP LOCKED` sobre el índice parcial `idx_sr_autoclose` (`responded_at <= now - 24h`, `actorId = null`, `actorRole = SISTEMA`, `actorName = "Sistema"`, comentario `"Cierre automático"`).
- **Consolidado y Reportes de Coordinación (`005-coordination-reports`):**
  - **Agregaciones SQL Puras en `TRANSACTION_REPEATABLE_READ`:** `ExposedReportRepository` calcula todas las métricas gerenciales (`totalRequests`, `byStatus`, `byCategory`, `byPriority`, `unassignedPriorityCount`, `byAssignee`, `unassignedCount`, `overdueCount`, `resolvedWithinSlaCount`, `resolvedOutsideSlaCount`) directamente en PostgreSQL 18 apoyado por los índices de `V5__add_coordination_reports_indexes.sql`, compartiendo `ServiceRequestsTable.toFilterOp(criteria)` con el listado paginado (`< 2s` con `1.000+` solicitudes).
  - **Patrón GoF Builder (`ReportFilterBuilder`):** Valida acumulativamente `createdFrom`/`createdTo` en `ZoneId.of("America/Bogota")` usando intervalo semiabierto `[startOfDay(from), startOfDay(to + 1 día))`, enums (`status`, `category`, `priority`), `assignedToId` (UUID) y paginación (`page >= 1`, `size in 1..20`).
  - **RBAC Estricto y Reutilización con `004`:** Ambos endpoints (`/api/v1/reports/summary` y `/api/v1/reports/requests`) están protegidos exclusivamente con `withRole(UserRole.COORDINADOR)`, y el detalle completo e historial reutilizan `GET /api/v1/attention/requests/{code}`.
- **Seguridad Perimetral:** Terminación TLS delegada a **Caddy** (`infra/Caddyfile.example`) hacia HTTP local (`APP_PORT=8080`), `XForwardedHeaders` confiable, `IpAllowListPlugin` (IPs exactas y rangos CIDR desde `ALLOWED_CLIENT_IPS`), **CORS estricto** (`CORS_ALLOWED_ORIGINS`, prohibido `*`), zona horaria `America/Bogota` (`-05:00`) y `StatusPages` con respuestas estandarizadas `ApiErrorDto`.
- **Semilla Idempotente (`RF-01`):** `CoordinatorSeedService` crea al arranque el único usuario `COORDINADOR` inicial con `at.favre.lib:bcrypt` (`0.10.2`, costo **12**) sin duplicarlo en reinicios.

---

## Requisitos Previos

1. **JDK 21 LTS** (`java -version` debe reportar versión 21).
2. **PostgreSQL 18.x** (local o contenedor Docker para ejecución) y **Docker Desktop** (obligatorio para pruebas de integración con **Testcontainers `postgres:18-alpine`**).
3. *(Opcional en producción/staging)* **Caddy** para terminación HTTPS/TLS.

---

## Ejecución Local Desde Cero

1. **Crear el archivo `.env`** a partir de la plantilla (nunca versionar `.env` en Git):
   ```bash
   cp .env.example .env
   ```
   Ajusta `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET` (mínimo 32 caracteres), `JWT_EXPIRATION_SECONDS=3600`, `AUTO_CLOSE_INTERVAL_SECONDS=60` y `SEED_COORDINATOR_*` según tu entorno local.

2. **Levantar PostgreSQL 18** (ejemplo rápido con Docker para desarrollo local):
   ```bash
   docker run -d \
     --name itm-postgres18 \
     -e POSTGRES_DB=itm_service_desk \
     -e POSTGRES_USER=itm_user \
     -e POSTGRES_PASSWORD=change_me_local_db_password \
     -p 5432:5432 \
     postgres:18-alpine
   ```

3. **Ejecutar el Backend:**
   ```bash
   ./gradlew run
   ```
   Al arrancar:
   - Se valida toda la configuración mediante `AppConfigBuilder`.
   - `CaddyBootstrapper` verifica si **Caddy** ya está corriendo (puerto `8443` / `2019` o proceso activo); si no lo está y `CADDY_AUTO_START=true` (por defecto), lo inicia automáticamente usando `infra/Caddyfile`.
   - Flyway ejecuta automáticamente las migraciones pendientes (`V1__create_users_table.sql`, `V2__create_revoked_tokens_table.sql`, `V3__create_service_requests_tables.sql`, `V4__add_autoclose_partial_index.sql`, `V5__add_coordination_reports_indexes.sql`).
   - `CoordinatorSeedService` crea de forma idempotente el único usuario `COORDINADOR` semilla.
   - `AutoCloseScheduler` inicia el barrido periódico de cierre automático (`24h`) con corutinas Ktor.
   - Ktor Netty queda escuchando en `http://0.0.0.0:8080`.

4. **Verificar disponibilidad (`Health Check`):**
   ```bash
   curl http://127.0.0.1:8080/api/v1/health
   ```
   Respuesta esperada (`200 OK`):
   ```json
   {
     "status": "UP",
     "service": "itm-service-desk-backend",
     "database": "UP",
     "timestamp": "2026-11-14T21:15:00-05:00"
   }
   ```

5. **Proxy Inverso TLS con Caddy (Automático en local / configurable en producción):**
   Por defecto al ejecutar el proyecto (`./gradlew run` o `ApplicationKt.main()`), el backend verifica si Caddy está activo y, de no estarlo, lo inicia en segundo plano con [`infra/Caddyfile`](infra/Caddyfile) (`https://localhost:8443` → `127.0.0.1:8080`). Si ya se encuentra en ejecución, omite el inicio para evitar conflictos de puertos. En entornos donde Caddy se gestione externamente, puedes desactivarlo con `CADDY_AUTO_START=false`. Usa [`infra/Caddyfile.example`](infra/Caddyfile.example) como referencia para producción.

---

## Endpoints Disponibles (`v0.3.0`)

| Método y Ruta | Acceso | Descripción |
| :--- | :--- | :--- |
| `GET /api/v1/health` | Público (IP allowlist) | Verifica estado de la API y PostgreSQL 18. |
| `POST /api/v1/auth/register` | Público | Registra un `SOLICITANTE` (Tabla 1, BCrypt costo 12) y emite JWT `1h` (`201 Created`). |
| `POST /api/v1/auth/login` | Público | Autentica cualquier rol activo y emite nuevo JWT `1h` con `jti` único (`200 OK`). |
| `GET /api/v1/auth/me` | JWT requerido | Retorna perfil del usuario autenticado (`200 OK`). |
| `POST /api/v1/auth/logout` | JWT requerido | Revoca el `jti` en `revoked_tokens`, purga expirados y responde `204 No Content`. |
| `POST /api/v1/users/staff` | JWT (`COORDINADOR`) | Crea personal interno (`PERSONAL_ATENCION` o `COORDINADOR`) (`201 Created`). |
| `GET /api/v1/users/staff` | JWT (`COORDINADOR`) | Lista personal interno paginado (`page >= 1`, `size in 1..20`, excluye `SOLICITANTE`). |
| `POST /api/v1/requests` | JWT (`SOLICITANTE`) | Registra una solicitud (`ABIERTA`), genera `SOL-AAAA-NNNNN`, valida `previousRequestCode` y registra historial `CREACION` (`201 Created`). |
| `GET /api/v1/requests/my` | JWT (`SOLICITANTE`) | Lista las solicitudes propias paginadas (`page >= 1`, `size in 1..20`, filtro `status` opcional) (`200 OK`). |
| `GET /api/v1/requests/{code}` | JWT (`SOLICITANTE` propietario) | Consulta detalle, respuesta/cierre e historial filtrado por privacidad `RF-03` (`200 OK` / `403` / `404`). |
| `POST /api/v1/requests/{code}/cancel` | JWT (`SOLICITANTE` propietario) | Cancela una solicitud `ABIERTA` (`ABIERTA -> CANCELADA`) vía Patrón State + actualización condicional (`200 OK` / `403` / `404` / `409`). |
| `GET /api/v1/attention/coordination/pending` | JWT (`COORDINADOR`) | Bandeja de pendientes (`ABIERTA` y `ESCALADA`) ordenada por `created_at ASC, id ASC` (`200 OK`). |
| `POST /api/v1/attention/requests/{code}/assign` | JWT (`COORDINADOR`) | Asigna `PERSONAL_ATENCION` y prioridad (`ALTA`/`MEDIA`/`BAJA`), fijando `firstAssignedAt` y `dueAt` (`ABIERTA -> EN_REVISION`). |
| `POST /api/v1/attention/requests/{code}/reassign` | JWT (`COORDINADOR`) | Reasigna una solicitud `ESCALADA -> EN_REVISION` conservando intacto `firstAssignedAt` y recalculando `dueAt`. |
| `GET /api/v1/attention/my-assigned` | JWT (`PERSONAL_ATENCION`) | Bandeja de solicitudes asignadas al agente con `isOverdue` informativo (`200 OK`). |
| `GET /api/v1/attention/requests/{code}` | JWT (`COORDINADOR` o `PERSONAL_ATENCION` asignado) | Detalle interno e historial cronológico completo incluyendo intervenciones y `SISTEMA` (`200 OK` / `403` / `404`). |
| `POST /api/v1/attention/requests/{code}/interventions` | JWT (`PERSONAL_ATENCION` asignado) | Registra intervención interna (`10..1000` chars) en `EN_REVISION` sin mutar el estado (`200 OK`). |
| `POST /api/v1/attention/requests/{code}/escalate` | JWT (`PERSONAL_ATENCION` asignado) | Escala una solicitud (`EN_REVISION -> ESCALADA`) con motivo (`10..500` chars) (`200 OK`). |
| `POST /api/v1/attention/requests/{code}/respond` | JWT (`PERSONAL_ATENCION` asignado) | Emite respuesta final inmutable (`20..2000` chars, `EN_REVISION -> RESUELTA`) y calcula `respondedWithinSla` (`200 OK`). |
| `GET /api/v1/reports/summary` | JWT (`COORDINADOR`) | Resumen consolidado con métricas agregadas en SQL y filtros combinables `America/Bogota` (`200 OK` / `400`). |
| `GET /api/v1/reports/requests` | JWT (`COORDINADOR`) | Listado general paginado (`page >= 1`, `size in 1..20`, `created_at DESC, id DESC`) consistente con `/summary` (`200 OK` / `400`). |

---

## Puertas de Calidad y Testing

Todas las validaciones de estilo (`ktlint 1.8.0`), pruebas unitarias, pruebas de integración con **Testcontainers (`postgres:18-alpine`)** y cobertura global ($\ge 90\%$ con **JetBrains Kover**) se ejecutan con:

```bash
./gradlew ktlintCheck check koverVerify
```

Para generar el reporte HTML de cobertura:

```bash
./gradlew koverHtmlReport
```
