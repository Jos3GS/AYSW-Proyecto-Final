# ITM Service Desk — Sistema de Gestión de Solicitudes de Servicio

Proyecto Final del curso **Aplicaciones y Servicios Web** (Guía PF-01).  
Monorepo compuesto por una **API RESTful** en Kotlin/Ktor + PostgreSQL 18 (`backend/`) y una **aplicación cliente multiplataforma** Web (`wasmJs`) y Android en Compose Multiplatform (`frontend/`).

---

## 1. Descripción del Problema y Propósito

En la institución, las solicitudes de servicio se gestionaban por canales dispersos (correo electrónico, llamadas telefónicas y WhatsApp), sin trazabilidad centralizada, sin control de acuerdos de nivel de servicio (SLA) ni consolidación gerencial.

**ITM Service Desk** centraliza el ciclo completo de atención desde la radicación de una solicitud hasta su respuesta final y cierre automático, manteniendo un historial auditable e inmutable de cada acción.

---

## 2. Actores y Alcance del Sistema

| Actor | Rol | Capacidades principales |
| :--- | :--- | :--- |
| **Usuario Solicitante** | `SOLICITANTE` | Registro público, inicio/cierre de sesión, radicación de solicitudes (`SOL-AAAA-NNNNN`, con vínculo opcional a una solicitud previa `RESUELTA` o `CERRADA`), consulta de estado/respuesta e historial filtrado (`RF-03`), y cancelación mientras esté `ABIERTA`. |
| **Personal de Atención** | `PERSONAL_ATENCION` | Consulta de bandeja asignada (con marca informativa `isOverdue`), registro de intervenciones internas (`10..1000` chars), escalamiento con motivo (`10..500` chars) y emisión de respuesta final inmutable (`20..2000` chars). |
| **Coordinación** | `COORDINADOR` | Gestión de personal interno (`PERSONAL_ATENCION` y `COORDINADOR`), bandeja de pendientes (`ABIERTA` y `ESCALADA`), asignación/reasignación con prioridad SLA (`ALTA=24h`, `MEDIA=72h`, `BAJA=120h`) sin reinicio de reloj, y reportes consolidados con filtros combinables. |
| **Sistema** | `SISTEMA` | Cierre automático (`RESUELTA` → `CERRADA`) tras cumplirse 24 horas desde `respondedAt`. |

> **Fuera de alcance (`RNF-10`):** No incluye adjuntos, notificaciones por correo o WhatsApp, pasarelas de pago, facturación ni exportación/integración BI externa.

---

## 3. Arquitectura y Tecnologías

- **Arquitectura Cliente–Servidor:** El frontend **Compose Multiplatform** consume exclusivamente la **API REST/JSON** detrás de **Caddy** (terminación TLS local `https://localhost:8443` → `127.0.0.1:8080`).
- **Backend (`backend/`):** Kotlin `2.4.21`, JDK `21 LTS`, Ktor Server `3.6.1` (Netty), Exposed `1.6.1` DSL, HikariCP `7.1.0`, Flyway `13.12.2`, PostgreSQL `18.x`, `at.favre.lib:bcrypt 0.10.2` (costo 12), Auth JWT (`HMAC256`, `exp = 1h`, blacklist en BD), `ktlint 1.8.0`, Kover ($\ge 90\%$) y Testcontainers `postgres:18-alpine`. Arquitectura en capas (`routing -> service -> domain`, `service -> repository -> domain`) con patrones GoF **State** y **Builder**.
- **Frontend (`frontend/`):** Kotlin Multiplatform `2.4.21`, Compose Multiplatform `1.12.2` (Material 3 `1.9.0`), Ktor Client `3.6.1` (`JS fetch` en `wasmJs`, `OkHttp` en Android), Koin `4.2.2`, Navigation Compose `2.9.2`, `kotlinx-datetime 0.8.0` (`America/Bogota`, `dd/mm/aaaa hh:mm`) y BuildKonfig `0.24.0` bajo **MVVM + UDF**.

Detalle completo de diagramas, modelo ER, decisiones y seguridad en [docs/diseno.md](docs/diseno.md).

---

## 4. Estructura del Repositorio

```text
AYSW-Proyecto-Final/
├── README.md                  # Guía principal de instalación, ejecución y documentación
├── docs/
│   ├── requisitos.md          # Problema, actores, alcance, RF/RNF y CA-01..CA-06
│   ├── diseno.md              # Arquitectura, modelo ER, diseño API, State/Builder y seguridad
│   ├── trazabilidad.md        # Matriz de trazabilidad y tabla de PRs/contribuciones
│   ├── pruebas.md             # Documentación de las 12 pruebas funcionales mínimas
│   └── pruebas_api.http       # Colección HTTP lista para repetir pruebas en vivo
├── evidencias/                # Capturas y respaldo (plan B) de pruebas funcionales
├── backend/                   # API RESTful Ktor + Exposed DSL + Flyway + Caddyfile
│   ├── .env.example           # Plantilla de variables de entorno (sin secretos reales)
│   ├── infra/                 # Caddyfile local y plantilla de producción
│   ├── specs/                 # Especificaciones por épica (001 a 005)
│   └── src/                   # Código fuente y pruebas (unitarias + Testcontainers)
└── frontend/                  # Cliente Compose Multiplatform (Web wasmJs + Android)
    ├── composeApp/src/        # commonMain, androidMain, wasmJsMain, tests
    └── specs/                 # Especificaciones por épica (001 a 005)
```

---

## 5. Requisitos Previos y Variables de Entorno

1. **JDK 21 LTS** (`java -version`).
2. **PostgreSQL 18.x** (local o mediante **Docker Desktop**).
3. **Docker Desktop** (necesario si ejecutas los tests de integración del backend con Testcontainers).
4. **Caddy** *(opcional en local, recomendado para HTTPS)*: si está instalado, `CaddyBootstrapper` lo inicia automáticamente en `https://localhost:8443` usando `backend/infra/Caddyfile`; también puedes desactivarlo con `CADDY_AUTO_START=false` en `backend/.env`.
5. **Navegador moderno** (Chrome, Edge o Firefox) para Web `wasmJs` o **Android SDK** para Android.

### Configuración de entorno (`backend/.env`)
El repositorio **no** incluye credenciales reales. Antes de iniciar el backend, crea `backend/.env` copiando la plantilla [backend/.env.example](backend/.env.example):

```bash
cd backend
cp .env.example .env
```

Variables principales en `backend/.env`:
- **Servidor y Proxy:** `APP_PORT=8080`, `APP_TIMEZONE=America/Bogota`, `CORS_ALLOWED_ORIGINS=http://localhost:8081,http://127.0.0.1:8081`, `ALLOWED_CLIENT_IPS=127.0.0.1,::1`, `AUTO_CLOSE_INTERVAL_SECONDS=60`, `CADDY_AUTO_START=true`, `CADDYFILE_PATH=infra/Caddyfile`, `CADDY_PORT=8443`.
- **Base de datos:** `DB_URL`, `DB_USER`, `DB_PASSWORD`, `DB_MAX_POOL_SIZE`.
- **JWT:** `JWT_SECRET` (mínimo 32 caracteres), `JWT_ISSUER`, `JWT_AUDIENCE`, `JWT_EXPIRATION_SECONDS=3600`.
- **Semilla inicial (`COORDINADOR`):** `SEED_COORDINATOR_NAME`, `SEED_COORDINATOR_DOCUMENT`, `SEED_COORDINATOR_EMAIL`, `SEED_COORDINATOR_PHONE`, `SEED_COORDINATOR_PASSWORD`.

---

## 6. Instalación y Ejecución Desde Cero

### Paso 1: Levantar PostgreSQL 18
```bash
docker run -d \
  --name itm-postgres18 \
  -e POSTGRES_DB=itm_service_desk \
  -e POSTGRES_USER=itm_user \
  -e POSTGRES_PASSWORD=change_me_local_db_password \
  -p 5432:5432 \
  postgres:18-alpine
```

### Paso 2: Ejecutar el Backend (`backend/`)
```bash
cd backend
./gradlew run
```
*(En Windows PowerShell usa `.\gradlew.bat run`)*

Al arrancar, el backend valida `AppConfigBuilder`, inicia Caddy si `CADDY_AUTO_START=true`, ejecuta las migraciones Flyway (`V1` a `V5`), crea de forma idempotente el `COORDINADOR` inicial con `CoordinatorSeedService`, arranca `AutoCloseScheduler` y expone la API en `http://0.0.0.0:8080` (y `https://localhost:8443` vía Caddy).

Verificación rápida:
```bash
curl http://127.0.0.1:8080/api/v1/health
```

### Paso 3: Ejecutar el Frontend (`frontend/`)

- **Web (`wasmJs`):**
  Por defecto apunta a `https://localhost:8443` (Caddy). Si ejecutas el backend sin Caddy o en otro puerto, sobrescribe con `-PwebDevApiUrl=http://localhost:8080`.
  ```bash
  cd frontend
  ./gradlew :composeApp:wasmJsBrowserDevelopmentRun
  ```
  > **Nota sobre puerto y TLS local:** Si el servidor de desarrollo web abre en `http://localhost:8081` (porque `8080` está ocupado por Ktor), coincide con `CORS_ALLOWED_ORIGINS` de `.env.example`. Si usas `https://localhost:8443` con certificado autofirmado de Caddy (`tls internal`), abre `https://localhost:8443/api/v1/health` una vez en el navegador y acepta el certificado local antes de iniciar sesión en la app web.

- **Android (`debug` en emulador):**
  Apunta por defecto a `http://10.0.2.2:8080` (o usa `-PapiBaseUrl=http://<TU_IP_LAN>:8080` en dispositivo físico).
  ```bash
  cd frontend
  ./gradlew :composeApp:installDebug
  ```

---

## 7. Endpoints Reales de la API REST (`/api/v1`)

| Método y Ruta | Acceso | Descripción |
| :--- | :--- | :--- |
| `GET /api/v1/health` | Público (IP allowlist) | Verifica estado de la API y PostgreSQL 18 (`200 OK` / `503`). |
| `POST /api/v1/auth/register` | Público | Registra un `SOLICITANTE` (Tabla 1, BCrypt costo 12) y emite JWT `1h` (`201 Created`). |
| `POST /api/v1/auth/login` | Público | Autentica cualquier rol activo y emite nuevo JWT `1h` con `jti` único (`200 OK`). |
| `GET /api/v1/auth/me` | JWT requerido | Retorna perfil del usuario autenticado (`200 OK`). |
| `POST /api/v1/auth/logout` | JWT requerido | Revoca el `jti` en `revoked_tokens`, purga expirados y responde `204 No Content`. |
| `POST /api/v1/users/staff` | JWT (`COORDINADOR`) | Crea personal interno (`PERSONAL_ATENCION` o `COORDINADOR`) (`201 Created`). |
| `GET /api/v1/users/staff` | JWT (`COORDINADOR`) | Lista personal interno paginado (`page >= 1`, `size in 1..20`). |
| `POST /api/v1/requests` | JWT (`SOLICITANTE`) | Crea solicitud (`ABIERTA`), genera `SOL-AAAA-NNNNN`, valida `previousRequestCode` y registra `CREACION` (`201 Created`). |
| `GET /api/v1/requests/my` | JWT (`SOLICITANTE`) | Lista solicitudes propias paginadas (`page >= 1`, `size in 1..20`, filtro `status` opcional) (`200 OK`). |
| `GET /api/v1/requests/{code}` | JWT (`SOLICITANTE` propietario) | Consulta detalle, respuesta/cierre e historial filtrado por privacidad `RF-03` (`200 OK` / `403` / `404`). |
| `POST /api/v1/requests/{code}/cancel` | JWT (`SOLICITANTE` propietario) | Cancela solicitud `ABIERTA` (`ABIERTA -> CANCELADA`) (`200 OK` / `403` / `404` / `409`). |
| `GET /api/v1/attention/coordination/pending` | JWT (`COORDINADOR`) | Bandeja de pendientes (`ABIERTA` y `ESCALADA`) ordenada por `created_at ASC, id ASC` (`200 OK`). |
| `POST /api/v1/attention/requests/{code}/assign` | JWT (`COORDINADOR`) | Asigna `PERSONAL_ATENCION` y prioridad (`ALTA`/`MEDIA`/`BAJA`), fijando `firstAssignedAt` y `dueAt` (`ABIERTA -> EN_REVISION`). |
| `POST /api/v1/attention/requests/{code}/reassign` | JWT (`COORDINADOR`) | Reasigna solicitud `ESCALADA -> EN_REVISION` conservando `firstAssignedAt` y recalculando `dueAt`. |
| `GET /api/v1/attention/my-assigned` | JWT (`PERSONAL_ATENCION`) | Bandeja asignada al agente con `isOverdue` informativo (`200 OK`). |
| `GET /api/v1/attention/requests/{code}` | JWT (`COORDINADOR` o `PERSONAL_ATENCION` asignado) | Detalle interno e historial cronológico completo incluyendo intervenciones y `SISTEMA` (`200 OK` / `403` / `404`). |
| `POST /api/v1/attention/requests/{code}/interventions` | JWT (`PERSONAL_ATENCION` asignado) | Registra intervención interna (`10..1000` chars) en `EN_REVISION` sin cambiar estado (`200 OK`). |
| `POST /api/v1/attention/requests/{code}/escalate` | JWT (`PERSONAL_ATENCION` asignado) | Escala solicitud (`EN_REVISION -> ESCALADA`) con motivo (`10..500` chars) (`200 OK`). |
| `POST /api/v1/attention/requests/{code}/respond` | JWT (`PERSONAL_ATENCION` asignado) | Emite respuesta final inmutable (`20..2000` chars, `EN_REVISION -> RESUELTA`) y calcula `respondedWithinSla` (`200 OK`). |
| `GET /api/v1/reports/summary` | JWT (`COORDINADOR`) | Resumen consolidado con métricas agregadas SQL y filtros `America/Bogota` (`200 OK` / `400`). |
| `GET /api/v1/reports/requests` | JWT (`COORDINADOR`) | Listado general paginado (`page >= 1`, `size in 1..20`, `created_at DESC, id DESC`) consistente con `/summary` (`200 OK` / `400`). |

---

## 8. Pruebas, Calidad y Evidencias

- **Pruebas funcionales documentadas (12 casos mínimos PF-01):** [docs/pruebas.md](docs/pruebas.md)
- **Colección HTTP lista para repetir pruebas en vivo:** [docs/pruebas_api.http](docs/pruebas_api.http)
- **Carpeta de evidencias y respaldo:** [evidencias/](evidencias/)

### Comandos de verificación automatizada (`ktlint`, tests y cobertura $\ge 90\%$ Kover)

**Backend:**
```bash
cd backend
./gradlew ktlintCheck check koverVerify
```

**Frontend:**
```bash
cd frontend
./gradlew ktlintCheck commonTest koverVerify compileKotlinWasmJs assembleDebug wasmJsBrowserDistribution
```

---

## 9. Documentación Técnica, Trazabilidad y Equipo

- **Requisitos y Condiciones de Aceptación (`CA-01`..`CA-06`):** [docs/requisitos.md](docs/requisitos.md)
- **Diseño Técnico y Decisiones de Arquitectura:** [docs/diseno.md](docs/diseno.md)
- **Matriz de Trazabilidad y Pull Requests (`#1`..`#11`):** [docs/trazabilidad.md](docs/trazabilidad.md)
- **Guías específicas por subproyecto:** [backend/README.md](backend/README.md) · [frontend/README.md](frontend/README.md)

**Integrantes del equipo (contribuciones verificables en GitHub):**
- José Gutiérrez (`Jos3GS`)
- Kevin Stiven Montoya (`KevinStiven04`)
