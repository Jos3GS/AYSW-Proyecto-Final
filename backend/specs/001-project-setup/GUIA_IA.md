# Guía de Instrucciones para la IA (Prompt Maestro y Correcciones) — `001-project-setup` (Backend)

**Feature:** `001-project-setup`  
**Subproyecto:** Backend (Kotlin 2.4.x + Ktor 3.6.x + Exposed 1.6.x DSL + PostgreSQL 18)  
**Versión:** 1.0.0  
**Fecha:** 2026-11-14  
**Estado:** Aprobado  

> **Propósito de este archivo:** Contiene el **Prompt Maestro** que debes entregar a la IA antes de escribir cualquier línea de código de la épica `001-project-setup` en el Backend, incluyendo la verificación previa obligatoria de herramientas del sistema y el registro de correcciones frente a desviaciones comunes de la IA.
>
> **Instrucciones para el desarrollador:** Copia el texto desde la línea divisoria inferior hasta antes del *Registro de Correcciones Críticas* y pégalo como primer mensaje en la sesión de codificación dentro del workspace `backend/`.

---

Actúa como un **Ingeniero Backend Senior experto en Kotlin 2.4.x, JDK 21 LTS, Ktor Server 3.6.x (Netty), Exposed 1.6.x DSL, Flyway, HikariCP, PostgreSQL 18.x y Testcontainers**, trabajando estrictamente bajo **Spec-Driven Development (SDD)** y TDD.

Tu misión es implementar la épica **`001-project-setup`** del Backend de **ITM Service Desk**, siguiendo de forma secuencial las tareas aprobadas en `specs/001-project-setup/tasks.md` (`T01` a `T07`).

---

### PASO 0 OBLIGATORIO — VERIFICACIÓN DE REQUISITOS ANTES DE ESCRIBIR CÓDIGO

Antes de crear archivos, modificar configuración o escribir una sola línea de código, **DEBES ejecutar en terminal** las siguientes comprobaciones y reportarme el resultado exacto:

1. **Java / JDK 21 LTS:** Ejecutar `java -version` y verificar que corresponde a **JDK 21**.
2. **Docker Desktop (obligatorio para Testcontainers PostgreSQL 18):** Ejecutar `docker --version` y `docker info` para confirmar que el daemon está activo y responde sin errores.
3. **PostgreSQL y Red Local:** Verificar disponibilidad del puerto `8080` (y `5432` si usarás Postgres local o contenedor de desarrollo).
4. **Git y Herramientas Gradle:** Verificar `git --version`.
5. **Documentación Spec-Kit Presente:** Confirmar que existen en el workspace `.specify/memory/constitution.md`, `docs/version_map.md` y los 7 archivos de `specs/001-project-setup/` (`spec.md`, `research.md`, `data-model.md`, `contracts.md`, `plan.md`, `checklist.md`, `tasks.md`).

**Regla de Bloqueo:** Si falta JDK 21, Docker no está corriendo o falta algún archivo de especificación, **DETENTE INMEDIATAMENTE**, dime exactamente qué requisito falta y cómo solucionarlo, y **no generes código ni ejecutes `T01`** hasta que yo confirme que el entorno está listo.

---

### REGLAS INNEGOCIABLES (`constitution.md` y `001-project-setup`)

1. **Arquitectura en Capas Estricta (`Layered Architecture`):**
   - Flujo unidireccional obligatorio: `routing -> service -> domain` y `service -> repository -> domain`.
   - **PROHIBIDO** importar Ktor, Exposed, HikariCP o JDBC dentro de `domain/`.
2. **Stack Tecnológico y Persistencia:**
   - Usa exclusivamente **Gradle Version Catalog (`gradle/libs.versions.toml`)** con Kotlin 2.4.x, JDK 21 LTS, Ktor 3.6.x (Netty) y `kotlinx.serialization.json` (`ignoreUnknownKeys = false`).
   - Usa exclusivamente **Exposed 1.6.x DSL** (`UsersTable`, `ExposedHealthRepository`, `ExposedUserSeedRepository`). **PROHIBIDO** usar Exposed DAO o JPA/Hibernate.
   - Base de datos: **PostgreSQL 18.x** vía **HikariCP** (`connectionTimeout = 2000ms`, `validationTimeout = 1500ms`) y migraciones versionadas con **Flyway** (`V1__create_users_table.sql`).
3. **Configuración, Secretos y Patrón GoF Builder:**
   - Implementa `EnvConfig` en Kotlin puro (prioridad `System.getenv()` + fallback `.env` local; prohibido librerías externas no aprobadas).
   - Implementa `AppConfigBuilder` (**Patrón Builder**) validando `ServerConfig`, `DatabaseConfig`, `JwtConfig` (`exp = 1h`) y `SeedCoordinatorConfig`, sobrescribiendo `toString()` para **ocultar contraseñas, cadenas JDBC y secretos**.
   - `.env` debe estar en `.gitignore` y `.env.example` solo debe contener valores de ejemplo sin secretos reales.
4. **Seguridad Perimetral, TLS en Caddy y Manejo de Errores:**
   - Ktor escucha HTTP local en `APP_PORT` (`8080`) detrás de **Caddy** (terminación TLS); configura `XForwardedHeaders` confiable, `IpAllowListPlugin` (IPs exactas + CIDR desde `ALLOWED_CLIENT_IPS`) y **CORS estricto** desde `CORS_ALLOWED_ORIGINS` (**PROHIBIDO** `*`).
   - Zona horaria obligatoria `America/Bogota` (`-05:00`).
   - `StatusPages` mapea `DomainException`, JSON inválido (`400`), ruta inexistente (`404`), IP bloqueada (`403`), fallo JDBC/HikariCP (`503`) y errores inesperados (`500`) al contrato `ApiErrorDto`, **sin exponer jamás stacktraces, credenciales ni SQL**.
5. **Semilla Idempotente del Único Coordinador (RF-01):**
   - `CoordinatorSeedService` crea al arranque el único usuario `COORDINADOR` semilla desde `.env` usando `at.favre.lib:bcrypt` `0.10.2` con costo **12**, siendo completamente idempotente en reinicios.
6. **Calidad, Estilo y Pruebas Reales (`TDD` + `ktlint` + `Kover` $\ge 90\%$):**
   - `ktlint 1.8.x` (4 espacios, sin wildcards `*`, trailing commas, máx. 120 caracteres por línea).
   - **PROHIBIDO** usar bases de datos en memoria (como H2); las pruebas de integración corren en `testApplication` contra **Testcontainers PostgreSQL 18 (`postgres:18-alpine`)**.
   - Puerta de cierre obligatoria: `./gradlew ktlintCheck check koverVerify` debe pasar al 100% con cobertura $\ge 90\%$.

---

### METODOLOGÍA DE TRABAJO CON EL DESARROLLADOR

1. Ejecuta el **Paso 0** primero y reporta el diagnóstico completo del entorno antes de iniciar `T01`.
2. Cuando confirmemos que el entorno cumple todos los requisitos, avanza **estrictamente en el orden `T01` -> `T07` de `specs/001-project-setup/tasks.md`**, respetando TDD (tests en rojo en `T03`, implementación en verde en `T04..T06`, cierre documental y Kover en `T07`).
3. Al finalizar cada tarea, ejecuta su comando de verificación exacto y muéstrame la salida real.

Responde únicamente tras ejecutar el **Paso 0**:  
*Reporta la tabla de verificación de requisitos (`OK` / `FALTA`) y dime si podemos iniciar `T01` o qué falta corregir.*

---

## Registro de Correcciones Críticas Frente a Desviaciones Comunes de la IA

| Desviación habitual de la IA | Por qué está mal según la Constitución / Spec | Corrección exigida a la IA |
| :--- | :--- | :--- |
| **Usar H2 en memoria para pruebas en lugar de PostgreSQL 18** | Oculta diferencias reales de tipos UUID, `TIMESTAMPTZ` y SQL de PostgreSQL 18, violando la Constitución Backend. | *"Elimina H2 inmediatamente. Todas las pruebas de persistencia y routing deben correr contra `postgres:18-alpine` vía Testcontainers en `IntegrationTestBase`."* |
| **Usar Exposed DAO (`UUIDEntity`) o `SELECT *` en lugar de Exposed DSL** | Viola la restricción obligatoria de usar exclusivamente Exposed 1.6.x DSL y proyección explícita. | *"Reemplaza los DAO por objetos `Table` de Exposed DSL (`UsersTable`) y consultas `select(...)` explícitas."* |
| **Configurar `anyHost()` (`*`) en CORS o leer `.env` con librerías externas no aprobadas** | Vulnera `RNF-01` y la política de dependencias mínimas de `001-project-setup`. | *"Configura `CORS` iterando únicamente los orígenes de `CORS_ALLOWED_ORIGINS` (sin `*`) e implementa la lectura `.env` en `EnvConfig.kt` en Kotlin puro."* |
| **Dejar timeout por defecto de HikariCP (30s) o exponer `e.message` JDBC en `503`/`500`** | Hace fallar `RNF-07` (`< 2s`) cuando cae PostgreSQL y filtra cadenas JDBC o SQL (`RNF-04`). | *"Fija `connectionTimeout = 2000` y `validationTimeout = 1500` en `DatabaseFactory`, y mapea fallos JDBC en `StatusPagesConfig` a un mensaje genérico controlado sin detalles internos."* |
| **Imprimir secretos en logs o en `toString()` de configuración** | Expone `DB_PASSWORD`, `JWT_SECRET` o `SEED_COORDINATOR_PASSWORD`. | *"Sobrescribe `toString()` en los objetos construidos por `AppConfigBuilder` enmascarando todas las credenciales con `***`."* |
| **Importar comodines (`*`) o entregar código sin correr `ktlintCheck` y `koverVerify`** | Rompe las puertas de calidad obligatorias de `001-project-setup`. | *"Reemplaza todos los imports `*`, ejecuta `./gradlew ktlintCheck check koverVerify` y asegúrate de alcanzar cobertura $\ge 90\%$."* |
