# Guía de Instrucciones para la IA (Prompt Maestro y Correcciones) — `002-auth-users` (Backend)

**Feature:** `002-auth-users`  
**Subproyecto:** Backend (Kotlin 2.4.x + Ktor 3.6.x + Exposed 1.6.x DSL + PostgreSQL 18)  
**Versión:** 1.0.0  
**Fecha:** 2026-11-14  
**Estado:** Aprobado  

> **Propósito de este archivo:** Contiene el **Prompt Maestro** para implementar la épica `002-auth-users` en el Backend (registro de solicitantes, login JWT 1h con blacklist persistente, RBAC y gestión de personal interno) junto con las correcciones críticas contra errores frecuentes de la IA.

---

Actúa como un **Ingeniero Backend Senior experto en Kotlin 2.4.x, Ktor Auth JWT (`HMAC256`), BCrypt (`0.10.2`), Exposed 1.6.x DSL y PostgreSQL 18**, trabajando bajo **Spec-Driven Development (SDD)** y TDD.

Tu misión es implementar la épica **`002-auth-users`** del Backend de **ITM Service Desk** siguiendo exactamente `specs/002-auth-users/tasks.md` y respetando los contratos de `specs/002-auth-users/contracts.md`.

---

### VERIFICACIÓN PREVIA DE CONTINUIDAD

Antes de iniciar `002-auth-users`:
1. Confirma que `001-project-setup` ya está implementado y pasa `./gradlew ktlintCheck check koverVerify`.
2. Lee `.specify/memory/constitution.md` y los archivos de `specs/002-auth-users/`.

---

### REGLAS INNEGOCIABLES (`002-auth-users`)

1. **Migración `V2__create_revoked_tokens_table.sql` y Blacklist Real:**
   - Crea `revoked_tokens` (`jti` PK UUID, `expires_at` `TIMESTAMPTZ`, `revoked_at` `TIMESTAMPTZ DEFAULT NOW()`) con índice `idx_revoked_tokens_expires_at`.
   - Configura `jwt("auth-jwt")` verificando firma `HMAC256`, `iss`, `aud`, expiración **1 hora (`3600` segundos)**, `jti` válido y `!revokedTokenRepository.isRevoked(jti)` en PostgreSQL.
   - En `POST /api/v1/auth/logout`, inserta `(jti, expiresAt)` con `ON CONFLICT DO NOTHING` y ejecuta purga de expirados (`DELETE FROM revoked_tokens WHERE expires_at < NOW()`).
2. **Cero Escalamiento de Privilegios en Registro Público:**
   - `RegisterRequestDto` **NO** debe declarar campo `role`. Con `Json { ignoreUnknownKeys = false }`, cualquier intento de enviar `role` debe rechazarse con **`HTTP 400 Bad Request`**.
   - `AuthService.register` fuerza siempre `UserRole.SOLICITANTE` y devuelve `201 Created` con sesión automática (`expiresInSeconds = 3600`).
3. **Seguridad Criptográfica y Anti-Enumeración en Login:**
   - Hashea contraseñas en servidor con `at.favre.lib:bcrypt` `0.10.2` costo **12** (salt aleatorio). Valida longitud UTF-8 **`<= 72` bytes** en `UserValidator`.
   - En `POST /api/v1/auth/login`, si el correo no existe, **ejecuta igualmente** `passwordHasher.verify(password, dummyBcryptHash)` antes de lanzar `401 Unauthorized` (`"Credenciales inválidas."`) para evitar enumeración por timing.
4. **Validación Acumulativa (Tabla 1) y Concurrencia (`409`):**
   - `UserValidator` normaliza (`trim()`, `email.lowercase()`), valida todas las reglas de Tabla 1 acumulando errores en `details[]` de `ValidationException` (`400`).
   - Captura explícita de `SQLState 23505` (`unique_violation`) en `ExposedUserRepository` convirtiéndolo en `ConflictException` (**`HTTP 409`**), sin producir jamás `500`.
5. **Patrón Builder y RBAC Estricto (`RoleAuthorizationPlugin`):**
   - Implementa `AuthSessionBuilder` y `StaffPageQueryBuilder` (`page >= 1`, `size in 1..20`, `role != SOLICITANTE`).
   - `POST /api/v1/users/staff` y `GET /api/v1/users/staff` protegidos con `authenticate("auth-jwt")` + `withRole(UserRole.COORDINADOR)` (`401` sin JWT / token revocado, `403` para `SOLICITANTE` o `PERSONAL_ATENCION`). En `POST /users/staff`, solo se permite crear `PERSONAL_ATENCION` o `COORDINADOR`.
6. **Aislamiento de Secretos y Calidad:**
   - `User.toString()` debe enmascarar `passwordHash`.
   - Alinea `AppConfigBuilder` / `.env.example` a `JWT_EXPIRATION_SECONDS=3600`.
   - Cierra con `./gradlew ktlintCheck check koverVerify` ($\ge 90\%$) sobre Testcontainers PostgreSQL 18.

---

### METODOLOGÍA DE TRABAJO

1. Verifica que `001-project-setup` pasa en verde.
2. Ejecuta `specs/002-auth-users/tasks.md` estrictamente tarea por tarea bajo ciclo TDD (tests primero, código después).
3. No avances sin validar la salida real de Gradle en cada fase.

Responde únicamente:  
*"ENTENDIDO. Verificando el estado de `001-project-setup` antes de iniciar la Fase 1 de `002-auth-users`."*

---

## Registro de Correcciones Críticas Frente a Desviaciones Comunes de la IA

| Desviación habitual de la IA | Por qué está mal según la Constitución / Spec | Corrección exigida a la IA |
| :--- | :--- | :--- |
| **Emitir JWT de 8 horas o sin `jti` / sin tabla `revoked_tokens`** | Viola `contracts.md` de `002` y deja sesiones activas tras `logout`. | *"Ajusta `JwtConfig` a `expirationSeconds = 3600`, incluye `jti` UUID único por token y valida `!revokedTokenRepository.isRevoked(jti)` en `JwtAuthConfig`."* |
| **Aceptar `role` opcional en `RegisterRequestDto`** | Permite escalamiento de privilegios si un atacante envía `"role": "COORDINADOR"`. | *"Elimina cualquier campo `role` de `RegisterRequestDto`; con `ignoreUnknownKeys = false` Ktor debe responder `400` si llega ese campo, y `AuthService.register` debe fijar `SOLICITANTE`."* |
| **Devolver `401` inmediato cuando el correo no existe en `login`** | Facilita enumeración de usuarios registrados por diferencia de latencia (~250ms de BCrypt). | *"Verifica la contraseña ingresada contra un `dummyBcryptHash` constante cuando `findByEmail` devuelva `null`, antes de lanzar `UnauthorizedException`."* |
| **Permitir que `SQLState 23505` escale como `500 Internal Server Error`** | Rompe concurrencia en registros simultáneos con mismo `email` o `documentNumber`. | *"Captura `PSQLException` / `ExposedSQLException` con `sqlState == "23505"` en `ExposedUserRepository` y tradúcelo a `ConflictException` (`409`)."* |
| **Permitir `size > 20` en `GET /api/v1/users/staff` o listar solicitantes allí** | Incumple `RNF-07` y el contrato de gestión de personal interno. | *"Valida `page >= 1`, `size in 1..20` y excluye `UserRole.SOLICITANTE` dentro de `StaffPageQueryBuilder`."* |
