# Checklist de Tareas Técnicas: 002-auth-users (Backend)
**Ubicación:** `specs/002-auth-users/tasks.md`  
**Plan Asociado:** `backend/specs/002-auth-users/plan.md`  
**Estado:** Aprobado  
**Progreso:** 0% (0 / 7 tareas completadas)  

---

## Tareas de Implementación

### Fase 1: Dependencias, Migración `V2` y Capa de Dominio
- [ ] **T01: Habilitar Ktor Auth JWT, ajustar `JwtConfig` a 1 hora (`3600s`) y crear migración Flyway `V2`**
  - **Archivos:** `gradle/libs.versions.toml`, `build.gradle.kts`, `.env.example`, `src/main/kotlin/.../config/EnvConfig.kt`, `src/main/kotlin/.../domain/builder/AppConfigBuilder.kt`, `src/main/resources/db/migration/V2__create_revoked_tokens_table.sql`
  - **Detalle:** Verificar inclusión de `ktor-server-auth` y `ktor-server-auth-jwt` en el catálogo; actualizar `JwtConfig` a `expirationSeconds = 3600` (y `.env.example` con `JWT_EXPIRATION_SECONDS=3600`); crear migración `V2__create_revoked_tokens_table.sql` (`jti UUID PRIMARY KEY`, `expires_at TIMESTAMPTZ`, `revoked_at TIMESTAMPTZ` e índice `idx_revoked_tokens_expires_at`).
  - **Verificación:** `./gradlew ktlintCheck compileKotlin` finaliza con código de salida 0.

- [ ] **T02: Implementar entidades de dominio, Builders, puertos y validador puro de Tabla 1 (`UserValidator`)**
  - **Archivos:** `src/main/kotlin/.../domain/model/{User.kt, UserProfile.kt, StaffSummary.kt, IssuedToken.kt, AuthSession.kt, AuthenticatedIdentity.kt, PagedResult.kt}`, `src/main/kotlin/.../domain/builder/{AuthSessionBuilder.kt, StaffPageQueryBuilder.kt}`, `src/main/kotlin/.../domain/repository/{UserRepository.kt, RevokedTokenRepository.kt}`, `src/main/kotlin/.../domain/security/{PasswordHasher.kt, TokenProvider.kt}`, `src/main/kotlin/.../domain/validation/UserValidator.kt`
  - **Detalle:** Modelar `User` enmascarando `passwordHash` en `toString()`; implementar `UserValidator` (trim, lowercase en `email`, regex Unicode de nombres, `5..15` dígitos documento, `7..15` dígitos teléfono, contraseña `>= 8` caracteres y `<= 72` bytes UTF-8 con letra+número, acumulación completa en `details[]`); implementar `StaffPageQueryBuilder` (`page >= 1`, `size in 1..20`, excluyendo `SOLICITANTE`) y `AuthSessionBuilder`.
  - **Verificación:** `./gradlew ktlintCheck compileKotlin` pasa sin errores y sin dependencias hacia Ktor o Exposed en `domain`.

### Fase 2: Suite TDD Inicial (Tests Unitarios e Integración en Rojo)
- [ ] **T03: Escribir suite TDD (en rojo) para validaciones, BCrypt, JWT, RBAC, blacklist y concurrencia**
  - **Archivos:** `src/test/kotlin/.../{UserValidatorTest.kt, BcryptPasswordHasherTest.kt, StaffPageQueryBuilderTest.kt, JwtTokenServiceTest.kt, AuthRoutingTest.kt, StaffRoutingTest.kt}`
  - **Detalle:** Cubrir todos los escenarios BDD (`Escenario 1` a `Escenario 8`): registro público con auto-login y rechazo de `role` extra (`201`/`400`/`409`), unicidad concurrente (`409`), login emitiendo siempre nuevo `jti` con `exp = 3600s` incluso antes de 1h (`200`/`401`), `/auth/me` (`200`/`401`), `/auth/logout` con revocación en `revoked_tokens` + purga de expirados (`204` y `401` posterior), creación/listado de personal interno exclusivo para `COORDINADOR` (`201`/`200`/`400`/`401`/`403`/`409`), exclusión de `SOLICITANTE` y paginación máx. `20/pág`.
  - **Verificación:** `./gradlew test` compila y falla en rojo por implementaciones faltantes (`./gradlew ktlintCheck` pasa en verde).

### Fase 3: Persistencia Exposed DSL y Servicios de Seguridad/Aplicación (TDD Verde)
- [ ] **T04: Implementar repositorios Exposed 1.6.x DSL (`ExposedUserRepository` y `ExposedRevokedTokenRepository`)**
  - **Archivos:** `src/main/kotlin/.../repository/{RevokedTokensTable.kt, ExposedUserRepository.kt, ExposedRevokedTokenRepository.kt}`
  - **Detalle:** Implementar creación y búsqueda de usuarios por `id`/`email`/unicidad, captura atómica de `SQLState 23505` -> `ConflictException`, consulta paginada de personal interno (`role IN ('PERSONAL_ATENCION', 'COORDINADOR')`, orden `created_at DESC, id ASC`), inserción idempotente en `revoked_tokens` y purga automática `DELETE FROM revoked_tokens WHERE expires_at < NOW()`.
  - **Verificación:** `./gradlew test --tests "*UserValidatorTest*" --tests "*StaffPageQueryBuilderTest*"` y pruebas de repositorio contra Testcontainers PostgreSQL 18 pasan en verde.

- [ ] **T05: Implementar `BcryptPasswordHasher`, `JwtTokenService`, `AuthService` y `StaffService`**
  - **Archivos:** `src/main/kotlin/.../service/{BcryptPasswordHasher.kt, JwtTokenService.kt, AuthService.kt, StaffService.kt}`
  - **Detalle:** Hashing con `at.favre.lib:bcrypt` 0.10.2 costo 12 + hash dummy en `login` para evitar enumeración temporal; emisión JWT `HMAC256` de 1 hora (`3600s`) con `jti` único tanto en `register` (forzando `SOLICITANTE`) como en `login`; cierre de sesión revocando `jti` y purgando vencidos; validación de rol `COORDINADOR` en `StaffService` excluyendo `SOLICITANTE`.
  - **Verificación:** `./gradlew test --tests "*BcryptPasswordHasherTest*" --tests "*JwtTokenServiceTest*"` pasa en verde.

### Fase 4: Seguridad HTTP Ktor, DTOs y Endpoints REST
- [ ] **T06: Implementar `JwtAuthConfig`, `RoleAuthorizationPlugin`, DTOs, `AuthRouting`, `StaffRouting` y montar en `Application.kt`**
  - **Archivos:** `src/main/kotlin/.../config/security/{JwtAuthConfig.kt, RoleAuthorizationPlugin.kt}`, `src/main/kotlin/.../routing/dto/auth/*`, `src/main/kotlin/.../routing/dto/users/*`, `src/main/kotlin/.../routing/{AuthRouting.kt, StaffRouting.kt}`, `src/main/kotlin/.../Application.kt`
  - **Detalle:** Configurar `Authentication` (`jwt("auth-jwt")`) validando firma, `exp` y ausencia de `jti` en `revoked_tokens` (`401` con `ApiErrorDto`); configurar `RoleAuthorizationPlugin` (`withRole(...)` devolviendo `403`); exigir `Json { ignoreUnknownKeys = false }`; exponer `/api/v1/auth/{register,login,me,logout}` y `/api/v1/users/staff`.
  - **Verificación:** `./gradlew test --tests "*AuthRoutingTest*" --tests "*StaffRoutingTest*" ktlintCheck` pasa al 100% en verde.

### Fase 5: Cierre de Documentación y Gate Final de Cobertura
- [ ] **T07: Actualizar documentación operativa, `docs/version_map.md` y verificar cobertura $\ge 90\%$**
  - **Archivos:** `README.md`, `docs/version_map.md`, `specs/002-auth-users/checklist.md`
  - **Detalle:** Documentar endpoints y variables JWT de `1h` en `README.md`, actualizar progreso de `002-auth-users` en `version_map.md` y confirmar todos los checks de calidad sin exponer contraseñas ni `JWT_SECRET` en logs.
  - **Verificación:** `./gradlew check koverVerify` finaliza con `0` errores en `ktlintCheck`, 100% de tests en verde sobre PostgreSQL 18 (Testcontainers) y cobertura global $\ge 90\%$ en JetBrains Kover.
