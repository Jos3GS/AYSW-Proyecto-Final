# Checklist de Calidad y Puertas de Aprobación (Quality Gates): 002-auth-users (Backend)
**Ubicación:** `specs/002-auth-users/checklist.md`  
**Spec Asociada:** `specs/002-auth-users/spec.md`  
**Estado:** Aprobado  

---

## 1. Validación contra la Constitución
- [x] El diseño técnico utiliza únicamente el stack autorizado: **Kotlin 2.4.x**, **JDK 21 LTS**, **Ktor 3.6.x (Netty)**, **`kotlinx.serialization.json`**, **PostgreSQL 18.x**, **Exposed 1.6.x (DSL)**, **HikariCP**, **Flyway**, **Ktor Auth JWT**, **`at.favre.lib:bcrypt` 0.10.2**, **`ktlint` 1.8.x** y **Testcontainers PostgreSQL**.
- [x] No se incorporan librerías externas no aprobadas para validación de campos, caché en memoria compartida, Redis ni programación de jobs externos.
- [x] Se respeta estrictamente **Layered Architecture** (`routing -> service -> domain` y `service -> repository -> domain`): la capa `domain` permanece desacoplada de Ktor (`AuthenticatedIdentity` vs `JwtUserPrincipal`) y de Exposed (`UserRepository` / `RevokedTokenRepository`).
- [x] Se aplica el patrón GoF **Builder** (`AuthSessionBuilder`, `StaffPageQueryBuilder`, `AppConfigBuilder` actualizado a `expirationSeconds = 3600`). *(El patrón GoF **State** de solicitudes de negocio corresponde a `004-attention-lifecycle`.)*
- [x] Se mantienen las reglas estrictas de `org.jlleitschuh.gradle.ktlint` (sin importaciones `*`, 4 espacios, trailing commas, máx. 120 caracteres) y cobertura global $\ge 90\%$ verificada con **JetBrains Kover**.

## 2. Completitud de la Especificación y Seguridad
- [x] Todos los escenarios BDD (`Escenario 1` a `Escenario 8`) de `spec.md` cubren HU-01 (registro/sesión), HU-07 (personal interno) y US-Auth-01 (JWT + RBAC).
- [x] Los contratos REST JSON en `contracts.md` definen explícitamente `POST /api/v1/auth/register`, `POST /api/v1/auth/login`, `GET /api/v1/auth/me`, `POST /api/v1/auth/logout`, `POST /api/v1/users/staff` y `GET /api/v1/users/staff`, junto con todos sus códigos de error (`400`, `401`, `403`, `409`) alineados con `ApiErrorDto`.
- [x] La política de tokens JWT cumple los lineamientos aprobados: expiración exacta de **1 hora (`3600s`)**, inicio de sesión automático tras registro (`201 Created` con `AuthResponseDto`), emisión de un **nuevo JWT con `jti` único** en cada `login`, y cierre de sesión con blacklist persistente en **`revoked_tokens`** (`V2__create_revoked_tokens_table.sql`) con purga automática de registros expirados (`expires_at < NOW()`).
- [x] Todas las reglas de Tabla 1 (longitud, regex Unicode de nombres, dígitos de documento/teléfono, correo normalizado a minúsculas, contraseña $\ge 8$ caracteres y $\le 72$ bytes UTF-8 con letra+número), hashing BCrypt costo 12 con salt aleatorio en servidor, verificación temporal uniforme en `login` y prohibición absoluta de exponer `password` / `passwordHash` están especificadas.
- [x] Las restricciones RBAC y de integridad están blindadas: `register` fuerza `SOLICITANTE` rechazando campos desconocidos (`400`), `/users/staff` exige rol `COORDINADOR` (`403` para otros roles, `401` sin sesión), `createStaff` rechaza `role = SOLICITANTE` (`400`), la paginación limita `size <= 20` excluyendo `SOLICITANTE`, y colisiones concurrentes SQL `23505` devuelven `409 Conflict`.
- [x] No quedan puntos pendientes marcados con `[NECESITA ACLARACIÓN]`.

## 3. Preparación para Implementación (`tasks.md`)
- [x] El plan de tareas atómicas (`specs/002-auth-users/tasks.md`) está redactado y aprobado en orden TDD.
- [x] Cada tarea de `tasks.md` define su criterio de verificación ejecutable (`ktlintCheck`, `./gradlew test`, `koverVerify`).
- [x] Aprobación explícita final para iniciar la implementación de `002-auth-users` (Backend) *(implementación verificada y en verde sobre PostgreSQL 18)*.
