# Investigación Técnica y Spikes: 001-project-setup (Backend)
**Ubicación:** `specs/001-project-setup/research.md`  
**Spec Asociada:** `specs/001-project-setup/spec.md`  
**Estado:** Concluido (Aprobado)  

---

## 1. Preguntas de Investigación y Objetivos del Spike
1. **Carga segura de `.env` sin violar la Constitución:** ¿Cómo leer variables desde `.env` en desarrollo local y desde el entorno del SO en producción/CI sin añadir librerías externas no aprobadas?
2. **Detección rápida de caída de PostgreSQL (`HTTP 503`):** ¿Qué configuración de HikariCP + Exposed + `StatusPages` evita bloqueos de 30s (default de HikariCP) y garantiza respuestas `503` en `< 2 segundos` (RNF-07 y RNF-08)?
3. **Resolución de IP real detrás de Caddy (`ALLOWED_CLIENT_IPS`):** ¿Cómo validar `ALLOWED_CLIENT_IPS` detrás del proxy inverso Caddy sin quedar expuesto a falsificación de cabeceras (`X-Forwarded-For` spoofing)?
4. **Semilla idempotente del Coordinador (RF-01):** ¿Cómo crear el usuario Coordinador inicial con `at.favre.lib:bcrypt` 0.10.2 sin exponer contraseñas ni hashes estáticos en scripts SQL de Flyway?
5. **Rendimiento de Testcontainers PostgreSQL 18 + JetBrains Kover ($\ge 90\%$):** ¿Cómo estructurar `testApplication` de Ktor para reutilizar el contenedor PostgreSQL 18 y garantizar ejecución rápida y cobertura real?

---

## 2. Alternativas Evaluadas y Trade-offs

| Decisión / Problema | Alternativas Evaluadas | Ventajas | Desventajas / Riesgos | Alineación con Constitución | Decisión |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Lectura de archivo `.env`** | **Opción A:** `dotenv-kotlin` | API lista para usar | Añade dependencia no aprobada en la Constitución | Requiere enmienda | Descartada |
| | **Opción B:** Loader propio `EnvConfig` en Kotlin puro (`System.getenv()` + fallback `.env`) | Cero dependencias extra, prioridad a variables reales del SO/CI, fácil de testear | Requiere ~35 líneas propias con pruebas unitarias | Cumple 100% | **Seleccionada** |
| **Timeouts de HikariCP para `HTTP 503`** | **Opción A:** Defaults de HikariCP (`connectionTimeout = 30s`) | Sin configuración adicional | Viola RNF-07 (`< 2s`) y retrasa respuesta `503` | Incumple RNF-07/08 | Descartada |
| | **Opción B:** `connectionTimeout = 2000ms`, `validationTimeout = 1500ms`, pool acotado | Falla rápido ante caída de PostgreSQL permitiendo `503` en `< 2s` | Posibles falsos positivos si la BD tarda >2s en arrancar localmente | Cumple RNF-07/08 | **Seleccionada** |
| **Filtro `ALLOWED_CLIENT_IPS` tras Caddy** | **Opción A:** Leer `X-Forwarded-For` sin validar emisor | Simple | Vulnerable a *IP spoofing* por cabeceras falsificadas | Incumple seguridad | Descartada |
| | **Opción B:** `XForwardedHeaders` de Ktor confiando solo en loopback/proxy local + `IpAllowListPlugin` | Previene spoofing, separa lógica perimetral de rutas de negocio | Requiere configurar IP del proxy en despliegue con Docker | Cumple seguridad | **Seleccionada** |
| **Semilla Coordinador inicial (RF-01)** | **Opción A:** `INSERT` en migración SQL `V1__init.sql` | Todo en Flyway | Expondría contraseña o hash fijo en Git; rompe RNF-04 | Incumple RNF-04 | Descartada |
| | **Opción B:** `CoordinatorSeedInitializer` en arranque post-Flyway vía Exposed DSL + BCrypt desde `.env` | Cero secretos en Git, salt aleatorio por servidor, idempotente | Requiere variables `SEED_COORDINATOR_*` presentes al inicializar | Cumple RF-01/RNF-04 | **Seleccionada** |
| **Ciclo de Testcontainers PostgreSQL 18** | **Opción A:** Contenedor nuevo por cada clase/test | Aislamiento total | Tiempos de ejecución de varios minutos | Cumple | Descartada |
| | **Opción B:** Contenedor singleton compartido (`postgres:18-alpine`) + limpieza transaccional/truncate controlado | Arranque único (~2-3s total), ejecución ágil de Ktor Server Tests | Requiere orden estricto de limpieza entre pruebas | Cumple | **Seleccionada** |

---

## 3. Análisis Técnico y Hallazgos

* **Hallazgo 1 — Exposed 1.6.x DSL y Zona Horaria `America/Bogota`:**
  Para garantizar consistencia temporal entre PostgreSQL 18, Flyway y Ktor (`America/Bogota`, `UTC-5`), la JVM fijará `TimeZone.setDefault(TimeZone.getTimeZone("America/Bogota"))` en el arranque y las columnas temporales en PostgreSQL usarán `TIMESTAMPTZ` (`OffsetDateTime` / `Instant`) para evitar ambigüedades en cálculos de SLA (24h/72h/120h) y cierre automático (24h).
* **Hallazgo 2 — Factor de Costo de BCrypt (`at.favre.lib:bcrypt` 0.10.2):**
  Se fija un **factor de costo de 12** (`BCrypt.withDefaults().hashToString(12, ...)`), equilibrando resistencia criptográfica robusta con un tiempo aproximado de ~250ms por operación, manteniendo margen holgado frente al umbral de `< 2s` de RNF-07.
* **Hallazgo 3 — Formato y Comportamiento de `ALLOWED_CLIENT_IPS`:**
  El filtro `IpAllowListPlugin` soportará tanto **IPs exactas** como **subredes CIDR** (ej. `127.0.0.1,::1,192.168.1.0/24`), aplicando `127.0.0.1,::1` por defecto si la variable no está definida en el entorno.
* **Hallazgo 4 — Estrategia de Cobertura con JetBrains Kover ($\ge 90\%$):**
  Para que Kover refleje fielmente el código ejecutable, el punto de entrada `EngineMain`/`embeddedServer` mínimo en `Application.kt` se delegará a una función extensible `Application.configureModule(appConfig)` invocada directamente tanto por `main()` como por `testApplication { application { configureModule(testConfig) } }`.
* **Hallazgo 5 — Tabla mínima de usuarios en `V1` para soportar la semilla (RF-01):**
  Aunque la lógica completa de registro y login pertenece a `002-auth-users`, `001-project-setup` define la migración inicial `V1__create_users_table.sql` (con PK, unicidad de `email` y `document_number`, y rol `COORDINADOR`) para que `CoordinatorSeedInitializer` sea completamente funcional e idempotente desde `001-project-setup`.

---

## 4. Decisión Final Justificada
> Se adopta una inicialización limpia sin dependencias externas fuera de la Constitución: `EnvConfig` propio (`.env` + `System.getenv()`), `HikariCP` con `connectionTimeout = 2000ms` para garantizar `HTTP 503` ágil ante caídas de PostgreSQL 18, `Flyway` ejecutando `V1` al arrancar seguido de `CoordinatorSeedInitializer` (Exposed DSL + `at.favre.lib:bcrypt` 0.10.2 con costo 12), seguridad perimetral con `CORS` + `XForwardedHeaders` + `IpAllowListPlugin` (IPs exactas y CIDR, default `127.0.0.1,::1`), y `Application.configureModule(...)` para unificar producción (Netty) y pruebas (`testApplication` + Testcontainers `postgres:18-alpine` + JetBrains Kover $\ge 90\%$).
