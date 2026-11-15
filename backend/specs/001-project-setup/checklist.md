# Checklist de Calidad y Puertas de Aprobación (Quality Gates): 001-project-setup (Backend)
**Ubicación:** `specs/001-project-setup/checklist.md`  
**Spec Asociada:** `backend/specs/001-project-setup/spec.md`  
**Estado:** Aprobado para Código  

---

## 1. Validación contra la Constitución
- [x] El diseño técnico respeta estrictamente el stack autorizado: **Kotlin 2.4.x**, **JDK 21 LTS**, **Ktor 3.6.x (Netty)**, **`kotlinx.serialization.json`**, **PostgreSQL 18.x**, **Exposed 1.6.x (DSL)**, **HikariCP**, **Flyway**, **`at.favre.lib:bcrypt` 0.10.2**, **`ktlint` 1.8.x** y **Testcontainers PostgreSQL**.
- [x] Se gestionan las dependencias mediante **Gradle Version Catalog (`gradle/libs.versions.toml`)** y se evita introducir librerías no aprobadas (loader `.env` propio en Kotlin puro).
- [x] Se cumple **Layered Architecture** con flujo unidireccional (`routing -> service -> domain` y `service -> repository -> domain`), sin acceso directo a Exposed desde `routing` ni fugas hacia `domain`.
- [x] Se aplica el patrón GoF **Builder** (`AppConfigBuilder`) para la construcción segura de configuraciones e invisibilidad de secretos en `toString()`. *(El patrón **State** de solicitudes de negocio se integra en `004-attention-lifecycle`.)*
- [x] Se exigen las 4 reglas estrictas de `org.jlleitschuh.gradle.ktlint` (cero importaciones `*`, indentación de 4 espacios, trailing commas, máx. 120 caracteres) y cobertura global $\ge 90\%$ con **JetBrains Kover**.

## 2. Completitud de la Especificación y Seguridad
- [x] Todas las capacidades técnicas (`US-01` a `US-04`) cuentan con escenarios BDD (`Given / When / Then`) verificables en `spec.md`.
- [x] Los contratos REST JSON (`GET /api/v1/health` y `ApiErrorDto` para `400`, `401`, `403`, `404`, `409`, `500`, `503`) están completamente especificados en `contracts.md`.
- [x] Están cubiertos los casos de borde y seguridad: terminación TLS delegada a **Caddy** hacia `APP_PORT=8080`, política **CORS** sin `*` (`CORS_ALLOWED_ORIGINS`), filtro **`ALLOWED_CLIENT_IPS`** tras `XForwardedHeaders` confiable (IPs exactas y CIDR), semilla idempotente del Coordinador desde `.env` con BCrypt costo 12, `connectionTimeout=2000ms` para `HTTP 503` y zona horaria `America/Bogota`.
- [x] El esquema DDL inicial (`V1__create_users_table.sql`) define `UUID` PK, constraints de Tabla 1/RNF-05 e índices únicos en `email` y `document_number`.
- [x] No quedan puntos críticos pendientes marcados con `[NECESITA ACLARACIÓN]`.

## 3. Preparación para Implementación (`tasks.md`)
- [x] El desglose secuencial en `specs/001-project-setup/tasks.md` está aprobado y listo para ejecución TDD.
- [x] Cada tarea de `tasks.md` define su comando explícito de verificación (`ktlintCheck`, `test`, `koverVerify`).
- [x] Aprobación explícita final para iniciar la implementación de `001-project-setup` (Backend).
