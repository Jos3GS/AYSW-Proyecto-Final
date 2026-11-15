# Checklist de Tareas Técnicas: 001-project-setup (Backend)
**Ubicación:** `specs/001-project-setup/tasks.md`  
**Plan Asociado:** `backend/specs/001-project-setup/plan.md`  
**Progreso:** 0% (0 / 7 tareas completadas)  

---

## Tareas de Implementación

### Fase 1: Estructura Base, Build, Calidad y Migración Inicial
- [ ] **T01: Inicializar proyecto Gradle con Version Catalog, `ktlint 1.8.x`, JetBrains Kover y `.env.example`**
  - **Archivos:** `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `.editorconfig`, `.gitignore`, `.env.example`
  - **Detalle:** Configurar Kotlin 2.4.x / JDK 21 LTS, Ktor 3.6.x (Netty), `org.jlleitschuh.gradle.ktlint` (sin wildcards, 4 espacios, trailing commas, máx. 120 chars), Kover (`minBound(90)`), `.gitignore` excluyendo `.env` y `.env.example` sin secretos reales.
  - **Verificación:** `./gradlew ktlintCheck` finaliza con código de salida 0.

- [ ] **T02: Definir migración Flyway `V1`, modelos de dominio, `DomainException` y `AppConfigBuilder` (GoF Builder)**
  - **Archivos:** `src/main/resources/db/migration/V1__create_users_table.sql`, `src/main/kotlin/.../domain/model/UserRole.kt`, `SystemHealth.kt`, `DomainException.kt`, `src/main/kotlin/.../domain/builder/AppConfigBuilder.kt`, `src/main/kotlin/.../domain/repository/HealthRepository.kt`, `UserSeedRepository.kt`
  - **Detalle:** Tabla `users` con `UUID` PK, unicidad de `document_number` y `email`, constraint de roles; `AppConfigBuilder` ocultando credenciales/secretos en `toString()`.
  - **Verificación:** `./gradlew ktlintCheck compileKotlin` pasa sin errores.

### Fase 2: Tests Unitarios y Harness de Integración (TDD Rojo)
- [ ] **T03: Configurar Testcontainers PostgreSQL 18 y escribir suite TDD inicial (en rojo)**
  - **Archivos:** `src/test/kotlin/.../IntegrationTestBase.kt`, `EnvConfigTest.kt`, `AppConfigBuilderTest.kt`, `IpAllowListTest.kt`, `CoordinatorSeedServiceTest.kt`, `HealthRoutingTest.kt`
  - **Detalle:** Levantar contenedor singleton `postgres:18-alpine`; cubrir escenarios BDD de `spec.md`: carga de `.env`, `AppConfigBuilder`, IPs exactas y CIDR en `ALLOWED_CLIENT_IPS`, CORS sin `*`, semilla idempotente con BCrypt costo 12, `GET /api/v1/health` (`200`), falla BD (`503`), ruta inexistente (`404`), JSON inválido (`400`) e IP bloqueada (`403`).
  - **Verificación:** `./gradlew test` compila y falla inicialmente en rojo por implementaciones pendientes (`./gradlew ktlintCheck` en verde).

### Fase 3: Configuración, Persistencia Exposed DSL y Servicios (TDD Verde)
- [ ] **T04: Implementar `EnvConfig` (Kotlin puro) y `DatabaseFactory` (HikariCP + Flyway)**
  - **Archivos:** `src/main/kotlin/.../config/EnvConfig.kt`, `src/main/kotlin/.../config/DatabaseFactory.kt`
  - **Detalle:** Cargar variables desde `System.getenv()` con fallback `.env` local; configurar HikariCP (`connectionTimeout=2000`, `validationTimeout=1500`), ejecutar Flyway `migrate()` y conectar Exposed.
  - **Verificación:** `./gradlew test --tests "*EnvConfigTest*" --tests "*AppConfigBuilderTest*"` pasa en verde.

- [ ] **T05: Implementar capa Exposed 1.6.x DSL y servicios (`HealthService` + `CoordinatorSeedService`)**
  - **Archivos:** `src/main/kotlin/.../repository/UsersTable.kt`, `ExposedHealthRepository.kt`, `ExposedUserSeedRepository.kt`, `src/main/kotlin/.../service/HealthService.kt`, `CoordinatorSeedService.kt`
  - **Detalle:** Consultas Exposed DSL (`SELECT 1` para salud e inserción idempotente del único Coordinador semilla desde `.env` usando `BCrypt.withDefaults().hashToString(12, ...)`).
  - **Verificación:** `./gradlew test --tests "*CoordinatorSeedServiceTest*"` pasa en verde contra PostgreSQL 18 en Testcontainers.

### Fase 4: Capa HTTP Ktor, Seguridad Perimetral y `StatusPages`
- [ ] **T06: Implementar `IpAllowListPlugin`, `SecurityPlugins` (CORS), `StatusPagesConfig`, `HealthRouting` y `Application.configureModule`**
  - **Archivos:** `src/main/kotlin/.../config/IpAllowListPlugin.kt`, `SecurityPlugins.kt`, `StatusPagesConfig.kt`, `src/main/kotlin/.../routing/dto/HealthResponseDto.kt`, `ApiErrorDto.kt`, `src/main/kotlin/.../routing/HealthRouting.kt`, `src/main/kotlin/.../Application.kt`
  - **Detalle:** Configurar zona horaria `America/Bogota`, `XForwardedHeaders` confiable tras Caddy, `IpAllowListPlugin` (IPs exactas + CIDR), CORS con `CORS_ALLOWED_ORIGINS` (sin `*`), `ContentNegotiation` (`kotlinx.serialization.json`), `StatusPages` (`400/401/403/404/409/500/503`) y endpoint `GET /api/v1/health`.
  - **Verificación:** `./gradlew test ktlintCheck` pasa al 100% en verde.

### Fase 5: Documentación Operativa, Quality Gate de Cobertura y Cierre
- [ ] **T07: Añadir `infra/Caddyfile.example`, `README.md`, actualizar `docs/version_map.md` y verificar cobertura $\ge 90\%$**
  - **Archivos:** `infra/Caddyfile.example`, `README.md`, `docs/version_map.md`, `specs/001-project-setup/checklist.md`
  - **Detalle:** Documentar terminación TLS con Caddy hacia `APP_PORT=8080`, instalación y ejecución local desde cero (RNF-08) y registrar el progreso en `version_map.md`.
  - **Verificación:** `./gradlew check koverVerify` pasa con `0` errores en `ktlintCheck`, 100% de tests en verde y cobertura global $\ge 90\%$ en JetBrains Kover.
