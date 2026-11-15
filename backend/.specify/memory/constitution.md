# ITM Service Desk (Backend) - Constitución del Proyecto
**Ubicación:** `.specify/memory/constitution.md`  
**Versión de Constitución:** 1.0.0  
**Fecha de Aprobación:** 2026-11-13  
**Estado:** Activa  

---

## 1. Visión y Propósito Fundamental
> **ITM Service Desk (Backend)** es la API RESTful encargada de centralizar, orquestar y garantizar la trazabilidad integral de las solicitudes de servicio de la organización, desde su recepción hasta su respuesta y finalización.
> 
> El sistema reemplaza la gestión dispersa actual (correo electrónico, llamadas telefónicas y WhatsApp), permitiendo que:
> - **Usuario solicitante:** registre necesidades de servicio y consulte de forma transparente si su solicitud fue recibida y cuál es su estado actual y respuesta.
> - **Personal de atención:** identifique casos pendientes, gestione solicitudes asignadas y registre todas las acciones realizadas.
> - **Coordinación:** supervise el proceso completo mediante información consolidada del estado general y la atención prestada.

---

## 2. Principios Inmutables (Core Non-Negotiables)
1. **Especificación Primero:** Ningún endpoint, tabla, servicio o cambio estructural se implementa sin una especificación (`spec.md`) aprobada y tareas atómicas (`tasks.md`).
2. **Cero Dependencias No Aprobadas:** Prohibido incorporar librerías, plugins de Ktor o paquetes externos fuera del stack aprobado sin justificación y enmienda explícita.
3. **Seguridad por Defecto:**
   - Toda comunicación HTTP entre Frontend y Backend debe estar protegida mediante **JWT** (salvo endpoints públicos estrictamente necesarios como autenticación/salud).
   - Las contraseñas deben viajar protegidas mediante cifrado de canal de red (**HTTPS/TLS**).
   - Una vez en el backend, las contraseñas deben almacenarse aplicando derivación fuerte con **BCrypt** (`at.favre.lib:bcrypt` 0.10.2) con generación de **salt aleatorio exclusivamente en el servidor**.
   - Las contraseñas en texto plano nunca deben registrarse en logs ni devolverse en ninguna respuesta de la API.
   - Control de acceso estricto basado en roles (RBAC) para los actores (*Usuario solicitante*, *Personal de atención*, *Coordinación*).
4. **Arquitectura y Patrones Obligatorios:**
   - Respeto estricto a **Layered Architecture** (sin saltarse capas ni acoplar rutas HTTP directamente a consultas SQL/Exposed).
   - Uso obligatorio de los patrones GoF **State** (para controlar transiciones válidas del ciclo de vida de las solicitudes) y **Builder** (para construcción controlada de entidades complejas, respuestas compuestas o filtros de consulta).
5. **Calidad, Estilo y Determinismo en Pruebas:** Todo código debe cumplir estrictamente con la guía de estilo oficial de Kotlin verificada por `org.jlleitschuh.gradle.ktlint` (**ktlint 1.8.x**) sin errores y mantener una cobertura mínima de **90% en general en el Backend** mediante **Ktor Server Tests** y **Testcontainers PostgreSQL**.

---

## 3. Stack Tecnológico Aprobado (Backend)

| Componente | Tecnología / Framework | Versión Requerida | Restricciones / Notas |
| :--- | :--- | :--- | :--- |
| **Lenguaje / Runtime** | Kotlin (JVM) / JDK 21 LTS | Kotlin 2.4.x / JDK 21 LTS | Cumplimiento estricto de la guía oficial de estilo Kotlin. |
| **Backend / API** | Ktor (API RESTful) | 3.6.x | Motor embebido **Netty**. Solo endpoints REST JSON. |
| **Serialización** | `kotlinx.serialization.json` | Compatible con Kotlin 2.4.x / Ktor 3.6.x | Contratos JSON tipados mediante DTOs `@Serializable`. |
| **Base de Datos** | PostgreSQL | 18.x | Motor relacional único de producción y pruebas de integración. |
| **Acceso a Datos** | JetBrains Exposed + HikariCP | Exposed 1.6.x | Estilo obligatorio **DSL (Domain Specific Language)**. Pool gestionado exclusivamente por HikariCP. |
| **Migraciones BD** | Flyway | Última compatible con PostgreSQL 18.x | Evolución de esquema versionada y determinista; prohibido usar `SchemaUtils.create` en producción. |
| **Seguridad / Auth** | Ktor Auth JWT + BCrypt | `at.favre.lib:bcrypt` 0.10.2 | JWT en todas las llamadas protegidas; BCrypt con salt aleatorio en servidor y transporte exclusivo sobre HTTPS/TLS. |
| **Linting** | `org.jlleitschuh.gradle.ktlint` (`ktlint`) | ktlint 1.8.x | Configurado en `build.gradle.kts`; validación obligatoria con cero errores. |
| **Testing** | Ktor Server Tests + Testcontainers PostgreSQL | Compatible con Ktor 3.6.x | Cobertura mínima obligatoria del **90% general en el Backend** ejecutada contra PostgreSQL 18 real vía Testcontainers. |

---

## 4. Arquitectura y Estructura de Directorios
* **Patrón de Arquitectura:** **Layered Architecture** (Arquitectura en Capas).
  1. **Capa de Presentación / API (`routing` / `dtos`):** Definición de rutas Ktor, validación de entrada, extracción de claims JWT y mapeo HTTP ↔ DTOs.
  2. **Capa de Aplicación / Servicios (`service`):** Orquestación de casos de uso, reglas de autorización por rol y coordinación transaccional.
  3. **Capa de Dominio (`domain`):** Modelos de dominio, reglas de negocio y patrones GoF obligatorios:
     - **Patrón State:** Modela los estados de la solicitud de servicio y encapsula las transiciones permitidas e invariantes por estado.
     - **Patrón Builder:** Construye objetos de dominio complejos, historiales de trazabilidad o criterios de búsqueda de forma segura e inmutable.
  4. **Capa de Persistencia / Datos (`repository` / `database`):** Implementación con Exposed 1.6.x (estilo **DSL**) + HikariCP, migraciones Flyway y mapeo Tabla ↔ Dominio.
* **Regla de Dependencias:** Flujo unidireccional estricto: `Presentation -> Application/Service -> Domain` y `Application/Service -> Repository -> Domain`. La capa de Presentación nunca invoca a Exposed ni a los Repositorios directamente; el Dominio no depende de Ktor ni de Exposed.
* **Estructura Base:**
```text
backend/
├── .specify/
│   └── memory/
│       └── constitution.md       # Esta Constitución
├── docs/
│   └── version_map.md            # Mapa de versiones y roadmap Backend
├── specs/                        # Especificaciones funcionales por feature
│   └── 001-feature-name/
│       ├── spec.md
│       ├── research.md
│       ├── data-model.md
│       ├── contracts.md
│       ├── plan.md
│       ├── checklist.md
│       ├── tasks.md
│       └── converge.md
├── src/
│   ├── main/
│   │   ├── kotlin/com/itm/servicedesk/
│   │   │   ├── config/           # Configuración Ktor, Netty, JWT, HikariCP, Flyway
│   │   │   ├── routing/          # Endpoints RESTful Ktor y DTOs serializables
│   │   │   ├── service/          # Lógica de aplicación y casos de uso
│   │   │   ├── domain/           # Entidades, State Pattern, Builder Pattern
│   │   │   └── repository/       # Tablas Exposed (DSL) y repositorios
│   │   └── resources/
│   │       └── db/migration/     # Scripts SQL versionados de Flyway
│   └── test/kotlin/com/itm/servicedesk/
│       └── ...                   # Ktor Server Tests + Testcontainers PostgreSQL
├── .editorconfig
└── build.gradle.kts
```

---

## 5. Estándares de Calidad, Estilo, Seguridad y Testing
* **Cobertura Mínima de Tests:** **90% general en el Backend** (verificada automáticamente en el pipeline/build).
* **Linter & Guía de Estilo (`org.jlleitschuh.gradle.ktlint` / ktlint 1.8.x):**
  - Cumplimiento estricto de la guía de estilo oficial de Kotlin.
  - **Prohibición absoluta de importaciones con comodín** (ej. prohibido `import io.ktor.server.application.*`).
  - **Indentación estricta de 4 espacios** (prohibido el uso de tabulaciones).
  - **Uso obligatorio de trailing commas** (comas finales) en declaraciones, parámetros y argumentos largos para garantizar diffs limpios en Pull Requests de GitHub.
  - **Longitud máxima de línea de 120 caracteres** para mantener la legibilidad.
* **Manejo de Errores:** Uso de plugin `StatusPages` de Ktor con excepciones tipadas de dominio y respuestas JSON de error estandarizadas; prohibido exponer stacktraces, secretos o contraseñas en respuestas o logs.
* **Trazabilidad:** Toda acción sobre una solicitud debe registrar marca temporal, actor responsable y cambio de estado/detalle para auditoría.

---

## 6. Proceso de Enmiendas
Cualquier cambio a esta Constitución requiere:
1. Justificación formal del impacto técnico y de deuda técnica.
2. Actualización de la versión de la constitución (SemVer: incremento mayor para cambios incompatibles).
3. Aprobación explícita del Tech Lead / Usuario antes de modificar este archivo.
