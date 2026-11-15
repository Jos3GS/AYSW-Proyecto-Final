# Checklist de Calidad y Puertas de Aprobación (Quality Gates): 003-service-requests (Backend)
**Ubicación:** `specs/003-service-requests/checklist.md`  
**Spec Asociada:** `specs/003-service-requests/spec.md`  
**Estado:** Completado  

---

## 1. Validación contra la Constitución
- [x] El diseño técnico utiliza únicamente el stack autorizado: **Kotlin 2.4.x**, **JDK 21 LTS**, **Ktor 3.6.x (Netty)**, **`kotlinx.serialization.json`**, **PostgreSQL 18.x**, **Exposed 1.6.x (DSL)**, **HikariCP**, **Flyway**, **Ktor Auth JWT**, **`ktlint` 1.8.x** y **Testcontainers PostgreSQL**.
- [x] No se introducen librerías ni dependencias externas no aprobadas para generación de secuencias, máquinas de estado, mapeo ni paginación.
- [x] Se respeta estrictamente **Layered Architecture** (`routing -> service -> domain` y `service -> repository -> domain`), manteniendo `domain` completamente desacoplado de `Ktor` y `Exposed`.
- [x] Se aplican los patrones GoF exigidos por la Constitución: **State** (`ServiceRequestState` con los 6 estados oficiales y transición controlada de cancelación) y **Builder** (`ServiceRequestBuilder`, `MyRequestsQueryBuilder`).
- [x] Se mantienen las reglas estrictas de `org.jlleitschuh.gradle.ktlint` (sin importaciones `*`, 4 espacios, trailing commas, máx. 120 caracteres) y cobertura global $\ge 90\%$ verificada con **JetBrains Kover**.

## 2. Completitud de la Especificación, Contratos y Reglas de Negocio
- [x] Todos los escenarios BDD de `spec.md` cubren **HU-01** (radicación, código `SOL-AAAA-NNNNN`, solicitud previa en `RESUELTA`/`CERRADA`) y **HU-02** (consulta paginada, filtro por estado, detalle con historial y cancelación desde `ABIERTA`), integrando el JWT de `1h` con blacklist de `002-auth-users`.
- [x] Los contratos REST JSON en `contracts.md` especifican explícitamente `POST /api/v1/requests`, `GET /api/v1/requests`, `GET /api/v1/requests/{code}` y `POST /api/v1/requests/{code}/cancel`, con payloads completos y respuestas de error (`400`, `401`, `403`, `404`, `409`) alineadas con `ApiErrorDto`.
- [x] Todas las validaciones de **Tabla 2** están cubiertas por `RequestValidator`: `category` obligatorio (4 categorías válidas), `subject` (`5..100` con `trim()`), `description` (`20..2000` con `trim()`), `reason` opcional (`<= 500`), rechazo de campos desconocidos o gestionados por el servidor (`ignoreUnknownKeys = false` → `400`), y validación integral de `previousRequestCode` (formato `^SOL-\d{4}-\d{5,}$`, existencia, pertenencia al mismo solicitante y estado `RESUELTA` o `CERRADA`, retornando `400` sin mutar la solicitud previa).
- [x] La generación del radicado único `SOL-AAAA-NNNNN` es atómica e incremental por año (`America/Bogota`) mediante `request_code_sequences` dentro de la misma transacción JDBC que inserta en `service_requests` y `request_history`.
- [x] Se cumplen la inmutabilidad y trazabilidad (**RNF-05**) y la privacidad del historial (**RF-03**): `request_history` es estrictamente append-only, la cancelación ejecuta validación de propiedad (`403`) + `ServiceRequestState` + actualización condicional atómica `WHERE id = ? AND status = 'ABIERTA'` (`409`), y el detalle del solicitante excluye eventos `INTERVENCION` y anula `comment` en `ESCALAMIENTO`/`REASIGNACION`.
- [x] El rendimiento y control de acceso están cubiertos (**RNF-07**, **RBAC**): rutas protegidas con `withRole(UserRole.SOLICITANTE)`, paginación estricta `size in 1..20` respaldada por índices compuestos en `V3__create_service_requests_tables.sql` y prueba con `1000` solicitudes bajo `< 2s`.
- [x] No quedan puntos pendientes marcados con `[NECESITA ACLARACIÓN]`.

## 3. Preparación para Implementación (`tasks.md`)
- [x] El plan de tareas atómicas (`specs/003-service-requests/tasks.md`) está redactado y aprobado en orden TDD.
- [x] Cada tarea de `tasks.md` define su criterio de verificación ejecutable (`ktlintCheck`, `./gradlew test`, `koverVerify`).
- [x] Aprobación explícita final para iniciar la implementación de `003-service-requests` (Backend) *(implementación completada y validada con `./gradlew ktlintCheck check koverVerify`)*.
