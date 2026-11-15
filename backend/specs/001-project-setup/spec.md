# Feature Spec: 001-project-setup (Infraestructura Base y Arranque del Backend)
**Ubicación:** `specs/001-project-setup/spec.md`  
**Versión de Spec:** 1.0.0  
**Versión Destino:** `v0.1.0`  
**Estado:** Aprobada  

---

## 1. Resumen y Justificación
* **Problema:** Antes de construir los módulos de autenticación y gestión de solicitudes de servicio, el Backend necesita una base técnica reproducible bajo **Layered Architecture**, con arranque determinista en Ktor/Netty detrás de terminación TLS en proxy inverso (Caddy), acceso seguro a PostgreSQL 18.x vía HikariCP + Exposed DSL, migraciones versionadas en Flyway, manejo centralizado de errores HTTP, configuración por variables de entorno sin secretos en el repositorio, y validación automática de estilo (`ktlint`) y pruebas de integración (`Testcontainers` + JetBrains Kover).
* **Solución propuesta:** Inicializar el proyecto Gradle con **Gradle Version Catalog (`libs.versions.toml`)** (Kotlin 2.4.x / JDK 21 LTS), configurar la estructura de paquetes por capas, el servidor Ktor 3.6.x (Netty) en HTTP local (`APP_PORT=8080`) detrás de Caddy para terminación TLS, `kotlinx.serialization.json`, plugin CORS estricto + filtro por IPs permitidas, `StatusPages`, conectividad PostgreSQL (HikariCP + Exposed 1.6.x DSL + Flyway), endpoint de salud (`GET /api/v1/health`), carga idempotente del usuario Coordinador semilla desde `.env` (RF-01), y el pipeline local de calidad (`ktlint 1.8.x` + Ktor Server Tests + Testcontainers PostgreSQL + JetBrains Kover $\ge 90\%$).
* **Fuera de alcance (Out of Scope):**
  - Terminación SSL/TLS nativa dentro del código Ktor (delegada al proxy inverso Caddy).
  - Endpoints de registro público, login JWT y gestión de personal (pertenecen a `002-auth-users`).
  - Entidades de negocio completas de solicitudes e historial (pertenecen a `003-service-requests` y `004-attention-lifecycle`).
  - Endpoints de consulta consolidada (pertenecen a `005-coordination-reports`).

---

## 2. Historias de Usuario / Capacidades Técnicas
* **US-01 (Infraestructura Base):** Como desarrollador del Backend, quiero inicializar el esqueleto Ktor Netty con Layered Architecture, `libs.versions.toml`, `.env.example`, `ktlint 1.8.x` y JetBrains Kover, para garantizar compilación reproducible y cumplimiento constitucional desde el primer commit.
* **US-02 (Persistencia y Semilla Inicial):** Como administrador/sistema, quiero que al iniciar el servidor se conecte a PostgreSQL mediante HikariCP, ejecute las migraciones Flyway de forma idempotente y cree el único usuario Coordinador semilla (RF-01) usando los valores de `.env` (hasheando su contraseña con BCrypt), para dejar el sistema operativo desde cero siguiendo el README.
* **US-03 (Observabilidad, Seguridad Perimetral y Manejo de Errores):** Como cliente HTTP (Frontend) u operador, quiero consultar `GET /api/v1/health`, operar bajo políticas estrictas de CORS/IPs permitidas y recibir respuestas JSON estandarizadas ante fallos (`400`, `403`, `404`, `500`, `503`), para diagnosticar la disponibilidad del servicio sin exponer trazas ni información sensible.
* **US-04 (Harness de Pruebas de Integración):** Como desarrollador, quiero ejecutar Ktor Server Tests conectados a Testcontainers PostgreSQL con verificación automática de cobertura $\ge 90\%$ mediante JetBrains Kover, para validar cada capa contra PostgreSQL 18 real.

---

## 3. Criterios de Aceptación (Escenarios BDD / Gherkin)

### Escenario 1: Arranque exitoso y verificación de salud (Health Check)
* **Given (Dado que):** Las variables de entorno requeridas en `.env` están configuradas y la instancia de PostgreSQL 18.x está disponible y accesible.
* **When (Cuando):** La aplicación Backend inicia en `APP_PORT` (por defecto `8080`) y se realiza una petición `GET /api/v1/health`.
* **Then (Entonces):**
  1. Flyway ejecuta las migraciones pendientes sin errores.
  2. El inicializador verifica si existe un Coordinador; si no existe, crea el único usuario Coordinador semilla desde `.env` aplicando BCrypt (`at.favre.lib:bcrypt` 0.10.2) con salt aleatorio, sin duplicarlo en reinicios posteriores.
  3. El endpoint responde `HTTP 200 OK` con cuerpo JSON indicando estado `"UP"`, estado de base de datos `"UP"` y timestamp en zona horaria `America/Bogota`.

### Escenario 2: Indisponibilidad de la Base de Datos (RNF-08)
* **Given (Dado que):** El servidor Ktor está en ejecución pero la conexión con PostgreSQL falla o el pool HikariCP no responde dentro del timeout configurado.
* **When (Cuando):** Se realiza una petición `GET /api/v1/health` (o cualquier petición que requiera base de datos).
* **Then (Entonces):**
  1. `StatusPages` intercepta la falla de conectividad de forma controlada.
  2. El sistema responde `HTTP 503 Service Unavailable` con un mensaje JSON genérico y controlado.
  3. No se exponen stacktraces, credenciales, cadenas JDBC ni consultas SQL en la respuesta ni en logs de cliente (RNF-04).

### Escenario 3: Control CORS e IPs permitidas
* **Given (Dado que):** El servidor está configurado con `CORS_ALLOWED_ORIGINS` (sin comodines `*`) y `ALLOWED_CLIENT_IPS`.
* **When (Cuando):** Un navegador o cliente realiza una petición desde un origen autorizado (`GET`, `POST`, `PATCH`, `PUT`, `DELETE` con cabeceras `Authorization` y `Content-Type`) desde una IP permitida, o desde un origen/IP no autorizado.
* **Then (Entonces):**
  1. Las peticiones desde orígenes e IPs permitidas reciben las cabeceras CORS válidas y son procesadas.
  2. Las peticiones desde orígenes no listados en `CORS_ALLOWED_ORIGINS` o IPs bloqueadas por `ALLOWED_CLIENT_IPS` son rechazadas de forma controlada sin procesar lógica de negocio.

### Escenario 4: Error de serialización o ruta inexistente (RNF-01 / RNF-04)
* **Given (Dado que):** El servidor Ktor está activo.
* **When (Cuando):** Un cliente solicita una ruta inexistente o envía un JSON mal formado.
* **Then (Entonces):** El servidor responde con el código HTTP coherente (`404` o `400`) y el esquema JSON estándar de error, sin filtrar trazas internas del servidor.

### Escenario 5: Validación de estilo y suite de pruebas aislada
* **Given (Dado que):** El código fuente del módulo base está implementado.
* **When (Cuando):** Se ejecutan las tareas Gradle de validación (`ktlintCheck`, `test` y `koverVerify`).
* **Then (Entonces):**
  1. `ktlintCheck` (1.8.x) finaliza con 0 errores (sin wildcards, 4 espacios, trailing commas, máx. 120 caracteres).
  2. Las pruebas en `testApplication` levantan un contenedor PostgreSQL 18 vía Testcontainers, validan Flyway, semilla idempotente, `/api/v1/health`, CORS/IPs y manejo `503`.
  3. `koverVerify` confirma cobertura global $\ge 90\%$.

---

## 4. Contratos de Datos e Interfaces

### Variables de Entorno Requeridas (`.env.example`, sin secretos reales)
* `APP_PORT=8080` — Puerto HTTP local de escucha para Ktor (detrás de terminación TLS en Caddy).
* `APP_TIMEZONE=America/Bogota` — Zona horaria oficial.
* `CORS_ALLOWED_ORIGINS=http://localhost:8081,http://127.0.0.1:8081` — Orígenes permitidos separados por comas (en producción solo el dominio oficial `https://...`, prohibido `*`).
* `ALLOWED_CLIENT_IPS=127.0.0.1,::1` — Lista de direcciones IP/rangos permitidos para peticiones hacia el backend (separados por comas).
* `DB_URL`, `DB_USER`, `DB_PASSWORD`, `DB_MAX_POOL_SIZE`.
* `JWT_SECRET`, `JWT_ISSUER`, `JWT_AUDIENCE`, `JWT_EXPIRATION_HOURS=8`.
* `SEED_COORDINATOR_NAME`, `SEED_COORDINATOR_DOCUMENT`, `SEED_COORDINATOR_EMAIL`, `SEED_COORDINATOR_PHONE`, `SEED_COORDINATOR_PASSWORD`.

### Configuración CORS Obligatoria
* **Orígenes:** Cargados dinámicamente desde `CORS_ALLOWED_ORIGINS` (cero comodines `*`).
* **Métodos autorizados:** `GET`, `POST`, `PATCH`, `PUT`, `DELETE` (+ preflight `OPTIONS`).
* **Cabeceras autorizadas:** `Authorization`, `Content-Type`.
* **Nota de Arquitectura:** Si en producción Caddy unifica Frontend y API bajo un mismo dominio y puerto, el navegador opera en same-origin y la política CORS de Ktor actúa como segunda capa de seguridad perimetral.

### Endpoint `GET /api/v1/health` — Respuesta Exitosa (`200 OK`)
```json
{
  "status": "UP",
  "service": "itm-service-desk-backend",
  "database": "UP",
  "timestamp": "2026-11-13T20:03:00-05:00"
}
```

### Contrato Estándar de Error (`4xx` / `5xx`, incl. `503 Service Unavailable`)
```json
{
  "timestamp": "2026-11-13T20:03:00-05:00",
  "status": 503,
  "error": "SERVICE_UNAVAILABLE",
  "message": "El servicio de base de datos no se encuentra disponible temporalmente.",
  "path": "/api/v1/health",
  "details": []
}
```

---

## 5. Requisitos No Funcionales (NFRs)
* **Build y Calidad (Constitución):** Gestión centralizada de dependencias con Gradle Version Catalog (`gradle/libs.versions.toml`); `ktlint 1.8.x` sin errores; verificación automática de cobertura $\ge 90\%$ con **JetBrains Kover**.
* **Seguridad de Transporte y Perímetro (RNF-04 / Constitución):** Ktor escucha HTTP local en `APP_PORT` delegando terminación HTTPS/TLS a **Caddy**; validación perimetral con `CORS_ALLOWED_ORIGINS` y `ALLOWED_CLIENT_IPS`; `.env` excluido en `.gitignore` y `.env.example` sin secretos; prohibido registrar contraseñas o credenciales en logs.
* **Disponibilidad y Persistencia (RNF-08):** Pool HikariCP con timeouts definidos para detectar fallos de PostgreSQL y responder `HTTP 503`; migraciones Flyway idempotentes y persistentes tras reinicios.
* **Localización y Tiempo (RNF-09):** Zona horaria global fijada en `America/Bogota` (`UTC-5`).
