# ITM Service Desk — Backend API

API RESTful del sistema **ITM Service Desk**, construida con **Kotlin 2.4.x**, **JDK 21 LTS**, **Ktor Server 3.6.x (Netty)**, **Exposed 1.6.x DSL**, **HikariCP**, **Flyway** y **PostgreSQL 18.x**.

---

## Arquitectura y Reglas del Proyecto

- **Arquitectura en Capas (`Layered Architecture`)** con flujo unidireccional estricto:
  - `routing -> service -> domain`
  - `service -> repository -> domain`
  - `domain/` es completamente independiente de Ktor, Exposed, HikariCP y JDBC.
- **Persistencia:** Exclusivamente **Exposed 1.6.x DSL** (`UsersTable`, `ExposedHealthRepository`, `ExposedUserSeedRepository`) + **HikariCP** (`connectionTimeout = 2000ms`, `validationTimeout = 1500ms`) + **Flyway** (`V1__create_users_table.sql`).
- **Configuración y Secretos:** `EnvConfig` (Kotlin puro, prioridad a `System.getenv()` con fallback a `.env`) + `AppConfigBuilder` (Patrón GoF **Builder**), ocultando contraseñas, cadenas JDBC y secretos en `toString()`.
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
   Ajusta `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET` (mínimo 32 caracteres) y `SEED_COORDINATOR_*` según tu entorno local.

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
   - Flyway ejecuta automáticamente las migraciones pendientes (`V1__create_users_table.sql`).
   - `CoordinatorSeedService` crea de forma idempotente el único usuario `COORDINADOR` semilla.
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

5. **Proxy Inverso TLS con Caddy (Opcional en local / Obligatorio en producción):**
   Usa [`infra/Caddyfile.example`](infra/Caddyfile.example) como base para exponer HTTPS con Caddy redirigiendo hacia `127.0.0.1:8080`.

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
