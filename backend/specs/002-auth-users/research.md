# Investigación Técnica y Spikes: 002-auth-users (Backend)
**Ubicación:** `specs/002-auth-users/research.md`  
**Spec Asociada:** `specs/002-auth-users/spec.md`  
**Estado:** Concluido (Aprobado)  

---

## 1. Preguntas de Investigación y Objetivos del Spike
1. **Emisión, expiración y unicidad del JWT (1 hora):** ¿Cómo emitir y validar tokens JWT con `ktor-server-auth-jwt` garantizando expiración exacta de 1 hora (`3600` segundos), identificador único `jti` por sesión tanto en `register` como en `login`, y emisión siempre fresca en cada login?
2. **Blacklist de JWT en PostgreSQL 18 (`POST /api/v1/auth/logout`) y purga de expirados:** ¿Cómo almacenar y consultar tokens revocados usando únicamente Exposed 1.6.x DSL + PostgreSQL 18 sin degradar el tiempo de respuesta (`< 2s`, RNF-07) y limpiando automáticamente registros ya vencidos (`expires_at < NOW()`)?
3. **Separación estricta `401 Unauthorized` vs `403 Forbidden` (RBAC):** ¿Cómo implementar autorización por rol (`SOLICITANTE`, `PERSONAL_ATENCION`, `COORDINADOR`) en Ktor 3.6.x manteniendo la Layered Architecture y sin introducir dependencias externas?
4. **Concurrencia en unicidad de `email` y `documentNumber` (`HTTP 409 Conflict`):** ¿Cómo evitar condiciones de carrera (*check-then-insert*) al registrar solicitantes o crear personal interno concurrentemente?
5. **Validación integral de Tabla 1 (`HTTP 400` con `details[]`) y protección BCrypt sin fugas:** ¿Cómo validar todos los campos sin dependencias externas de validación, devolver todos los errores acumulados en `details[]`, y mantener tiempos seguros ante ataques de enumeración en `login`?
6. **Paginación determinista y consulta de personal interno (`GET /api/v1/users/staff`):** ¿Cómo estructurar la consulta Exposed DSL para devolver máximo 20 registros por página en `< 2s` excluyendo siempre `SOLICITANTE`?

---

## 2. Alternativas Evaluadas y Trade-offs

| Decisión / Problema | Alternativas Evaluadas | Ventajas | Desventajas / Riesgos | Alineación con Constitución | Decisión |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Almacenamiento de Blacklist en Logout** | **Opción A:** Cache en memoria (`ConcurrentHashMap`) | Latencia en nanosegundos, cero I/O | Se pierde al reiniciar el servidor y no comparte estado entre réplicas | Debilita seguridad tras reinicios | Descartada |
| | **Opción B:** Redis externo | TTL automático nativo y alta velocidad | Añade infraestructura y cliente externo no aprobado en la Constitución | Requiere enmienda de stack | Descartada |
| | **Opción C:** Tabla PostgreSQL `revoked_tokens` (`jti` PK + `expires_at`) vía Exposed DSL + purga de expirados | Persistente ante reinicios, cero dependencias extra, consulta por PK O(log n) `< 1ms` | Requiere migración Flyway `V2` y mecanismo de limpieza de expirados | Cumple 100% | **Seleccionada** |
| **Mecanismo de purga de `revoked_tokens`** | **Opción A:** Job externo (Quartz / cron del SO) | Limpieza periódica independiente | Introduce librería/configuración operativa extra innecesaria para el tamaño esperado | Complejidad injustificada | Descartada |
| | **Opción B:** Purga oportunista (`DELETE WHERE expires_at < NOW()`) en `logout` + corrutina periódica opcional de ciclo de vida Ktor | Sin librerías extra, 100% determinista en pruebas (`logout` limpia vencidos al instante) y mantiene tabla mínima | Si nadie hace `logout` en días, las filas vencidas esperan hasta el siguiente ciclo | Cumple 100% | **Seleccionada** |
| **Control RBAC en Rutas Ktor (`401` vs `403`)** | **Opción A:** Validar `if (principal.role != ...)` manualmente en cada endpoint | Directo sin abstracciones | Duplicación de código y alto riesgo de olvidar validaciones en nuevas rutas | Baja mantenibilidad | Descartada |
| | **Opción B:** `RouteScopedPlugin` reutilizable en Ktor (`withRole(...)`) + validación de rol también en capa `service` (defensa en profundidad) | Centraliza `403 Forbidden` en HTTP y protege reglas de negocio si se invoca el servicio internamente | Requiere cuidado al ordenar `authenticate` antes de `withRole` | Cumple 100% (Layered Architecture) | **Seleccionada** |
| **Motor de Validación Tabla 1 (`400 Bad Request`)** | **Opción A:** Librerías externas (`Konform`, `Hibernate Validator`) | DSL declarativo externo | Añade dependencia no aprobada en la Constitución | Requiere enmienda de stack | Descartada |
| | **Opción B:** Validador puro en `domain/validation` con acumulación de errores y Builder de respuesta | Cero dependencias, 100% unit-testable sin levantar servidor ni BD, retorna todos los `details[]` de una vez | Código propio que mantener | Cumple 100% | **Seleccionada** |
| **Manejo de duplicados concurrentes (`409 Conflict`)** | **Opción A:** Solo consulta previa `SELECT` antes de `INSERT` | Mensajes simples | Vulnerable a *race condition* con dos registros paralelos (`500` por constraint SQL) | Riesgo de integridad | Descartada |
| | **Opción B:** Consulta previa de negocio + captura atómica de `SQLState 23505` (`unique_violation`) en el repositorio mapeada a `ConflictException` | Garantiza `409 Conflict` incluso bajo peticiones simultáneas | Requiere inspeccionar `PSQLException` / `ExposedSQLException` en la capa `repository` | Cumple 100% | **Seleccionada** |

---

## 3. Análisis Técnico y Hallazgos

* **Hallazgo 1 — Estructura de Claims JWT y Emisión Unificada (1 hora / `3600s`):**
  * Tanto `register` como `login` delegarán la emisión del token en un único `JwtTokenService` usando `HMAC256(jwtConfig.secret)` y zona horaria unificada (`Instant` / `OffsetDateTime` en `America/Bogota`).
  * Cada emisión generará un `jti = UUID.randomUUID().toString()` distinto, con `sub = user.id`, `role = user.role.name`, `email = user.email`, `iss = jwtConfig.issuer`, `aud = jwtConfig.audience`, `iat = now` y `exp = now.plusSeconds(3600)` (alineando `JwtConfig` a `expirationSeconds: Long = 3600`).
  * Un nuevo `login` en menos de 1 hora siempre emitirá un JWT nuevo con `jti` y `exp` frescos; si el usuario cierra sesión con un token específico, solo se revoca ese `jti`, aunque como política de seguridad en cliente el logout elimina la sesión local vigente.

* **Hallazgo 2 — Validación JWT en Ktor con Blacklist PostgreSQL 18 (`V2`):**
  * El plugin `Authentication` (`jwt("auth-jwt")`) verificará en orden:
    1. Firma HMAC256, `issuer`, `audience` y expiración (`exp`).
    2. Presencia y formato UUID válido de los claims `sub`, `jti` y `role` (mapeando a `UserRole`).
    3. Consulta rápida por clave primaria en `revoked_tokens` (`!revokedTokenRepository.isRevoked(jti)`) y estado activo del usuario si aplica.
  * Si falla cualquiera de los puntos anteriores (token ausente, expirado, malformado, firma inválida o `jti` presente en `revoked_tokens`), el `challenge` devolverá `HTTP 401 Unauthorized` usando el contrato estándar `ApiErrorDto`.

* **Hallazgo 3 — Diseño de Tabla `revoked_tokens` y Purga Automática (`V2__create_revoked_tokens_table.sql`):**
  * La tabla almacenará únicamente `jti UUID PRIMARY KEY`, `expires_at TIMESTAMPTZ NOT NULL` y `revoked_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`, junto con un índice `idx_revoked_tokens_expires_at` sobre `expires_at`.
  * Al ejecutarse `POST /api/v1/auth/logout`, dentro de la transacción de cierre de sesión se realizará:
    1. Inserción idempotente (`ON CONFLICT (jti) DO NOTHING`) del `jti` del token actual con su `expires_at` extraído del JWT.
    2. Purga automática de tokens vencidos: `DELETE FROM revoked_tokens WHERE expires_at < NOW()` (`RevokedTokensTable.deleteWhere { expiresAt less CurrentTimestamp }`), manteniendo acotado el crecimiento de la tabla en PostgreSQL sin necesidad de jobs externos.

* **Hallazgo 4 — Seguridad en BCrypt (Costo 12) y Mitigación de Enumeración en `login`:**
  * `PasswordHasher` encapsulará `at.favre.lib:bcrypt` 0.10.2 (`BCrypt.withDefaults().hashToString(12, password.toCharArray())` y `BCrypt.verifyer().verify(...)`).
  * Cuando en `POST /api/v1/auth/login` el correo no exista en BD, además de responder exactamente el mismo mensaje `"Credenciales inválidas."` (`401`), se puede ejecutar una verificación BCrypt contra un hash dummy precomputado para equiparar el tiempo de respuesta (`~250ms`) y evitar ataques de temporización que detecten correos registrados.
  * En `POST /api/v1/auth/register`, el DTO de registro **no expondrá el campo `role`** y la capa de servicio asignará siempre `UserRole.SOLICITANTE` de forma inmutable.

* **Hallazgo 5 — Reglas de Validación Estrictas de Tabla 1 en Dominio:**
  * `fullName`: `trim()` previo; longitud `3..100`; regex `^[\p{L} ]+$` (solo letras Unicode incluyendo tildes/ñ y espacios; se rechazan dígitos y símbolos).
  * `documentNumber`: `trim()` previo; `^[0-9]{5,15}$`.
  * `email`: `trim().lowercase()`; longitud `<= 100`; regex estándar para correo válido.
  * `phone`: `trim()` previo; `^[0-9]{7,15}$`.
  * `password`: longitud `>= 8` (máx. `72` bytes UTF-8 por límite nativo de BCrypt, validado explícitamente para no truncar silenciosamente contraseñas largas); al menos 1 letra (`\p{L}`) y 1 dígito (`[0-9]`).
  * El validador acumulará todos los errores de validación en un `List<ValidationDetail>` antes de lanzar `ValidationException`, permitiendo que el Frontend muestre todos los mensajes de error en una sola respuesta `400`.

* **Hallazgo 6 — Manejo de Estado de `UserPrincipal` y Builder de Respuestas Paginadas:**
  * Aunque el patrón GoF **State** gobernará el ciclo de vida de solicitudes en `004-attention-lifecycle`, en `002-auth-users` se empleará el patrón GoF **Builder** (`AuthResponseBuilder`, `PagedStaffResponseBuilder` / `StaffQueryFilterBuilder`) para construir respuestas compuestas y filtros de consulta paginada (`page >= 1`, `size in 1..20`, filtro opcional `role in {PERSONAL_ATENCION, COORDINADOR}`), manteniendo el ordenamiento determinista `ORDER BY created_at DESC, id ASC`.

---

## 4. Decisión Final Justificada
> Se adopta autenticación stateless firmada con **Ktor Auth JWT (`HMAC256`)** y expiración estricta de **1 hora (`3600s`)**, emitiendo siempre un nuevo token con `jti` único tanto en `POST /api/v1/auth/register` como en `POST /api/v1/auth/login`. Para `POST /api/v1/auth/logout`, la revocación se implementa mediante la tabla **`revoked_tokens` en PostgreSQL 18** (`V2__create_revoked_tokens_table.sql`, Exposed DSL) con verificación por PK `jti` en `jwt.validate` y purga automática oportunista de registros ya expirados (`expires_at < NOW()`).  
> El control RBAC se refuerza en doble nivel: un `RouteScopedPlugin` (`withRole`) en la capa `routing` para distinguir limpiamente `401` de `403`, y verificación explícita en `service`. Todas las reglas de Tabla 1 se centralizan en validadores puros de dominio con acumulación de errores en `details[]`, hashing BCrypt costo 12 con verificación de tiempo constante en `login`, y mapeo atómico de colisiones SQL `23505` a `HTTP 409 Conflict`.  
> **Precauciones para `plan.md`:** Verificar en `002-auth-users/data-model.md` y `plan.md` el ajuste de `JwtConfig` a `expirationSeconds = 3600` (1 hora) para alinear con la política de seguridad aprobada, y cubrir con Testcontainers tanto la concurrencia de duplicados (`409`) como la revocación + purga de tokens vencidos en `revoked_tokens`.
