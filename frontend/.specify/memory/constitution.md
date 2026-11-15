# ITM Service Desk (Frontend) - Constitución del Proyecto
**Ubicación:** `.specify/memory/constitution.md`  
**Versión de Constitución:** 1.0.0  
**Fecha de Aprobación:** 2026-11-13  
**Estado:** Activa  

---

## 1. Visión y Propósito Fundamental
> **ITM Service Desk (Frontend)** es la aplicación multiplataforma (**Web WebAssembly y Móvil Android**) construida con **Compose Multiplatform** que brinda acceso unificado, seguro y trazable a la gestión de solicitudes de servicio de la organización, consumiendo la API RESTful del Backend.
>
> Permite a cada actor operar según su rol:
> - **Usuario solicitante:** registrar nuevas solicitudes, consultar si fueron recibidas, visualizar su estado actual y leer la respuesta final.
> - **Personal de atención:** visualizar casos pendientes, filtrar/consultar solicitudes asignadas y registrar acciones de atención y cierre.
> - **Coordinación:** consultar tableros y vistas consolidadas sobre el estado general del servicio y la atención prestada.

---

## 2. Principios Inmutables (Core Non-Negotiables)
1. **Especificación Primero:** Ninguna pantalla, ViewModel, repositorio o flujo de navegación se implementa sin una especificación (`spec.md`) aprobada y tareas atómicas (`tasks.md`).
2. **Cero Dependencias No Aprobadas:** Prohibido añadir librerías multiplataforma, SDKs o plugins de Gradle fuera del stack aprobado sin justificación y enmienda explícita.
3. **Seguridad por Defecto:**
   - Todas las peticiones HTTP hacia el Backend deben ejecutarse sobre **HTTPS/TLS** y portar el token **JWT** en la cabecera `Authorization` (salvo endpoints públicos de autenticación/salud).
   - Las contraseñas capturadas en la UI nunca se almacenan localmente, cachean ni registran en logs; viajan protegidas por HTTPS/TLS hacia el Backend.
   - **Almacenamiento seguro de sesión (JWT) mediante `expect/actual`:**
     - **Web (`wasmJs`):** Token mantenido en memoria durante la ejecución y respaldado exclusivamente en `sessionStorage` para destruirse al cerrar la pestaña (mitigación XSS). **Prohibido el uso de `localStorage`.**
     - **Android:** Token almacenado usando `EncryptedSharedPreferences` (AndroidX Security Crypto), cifrado en reposo mediante **Android Keystore**.
   - Manejo automático de expiración/invalidación de sesión (`401 Unauthorized` → limpieza segura del JWT y redirección al login).
4. **Arquitectura y Patrones Obligatorios:**
   - Respeto estricto a **MVVM (Model - View - ViewModel)** con flujo unidireccional de datos (UDF): las Vistas (`@Composable`) son declarativas y pasivas respecto a lógica de negocio; únicamente observan `UiState` y emiten intenciones al `ViewModel`.
   - Uso obligatorio de **Repository Pattern** (abstrae la fuente de datos HTTP/almacenamiento respecto al dominio y `ViewModel`) y **Mapper Pattern** (aísla completamente los DTOs JSON de red respecto a los modelos de dominio y UI).
5. **Calidad, Estilo y Determinismo en Pruebas:** Todo código Kotlin multiplataforma debe cumplir estrictamente la guía de estilo oficial de Kotlin verificada con `org.jlleitschuh.gradle.ktlint` (**ktlint 1.8.x**) sin errores y alcanzar una cobertura mínima de **90% de forma general en el proyecto**.

---

## 3. Stack Tecnológico Aprobado (Frontend)

| Componente | Tecnología / Framework | Versión Requerida | Restricciones / Notas |
| :--- | :--- | :--- | :--- |
| **Lenguaje** | Kotlin Multiplatform | 2.4.x | Código compartido en `commonMain`; adaptadores nativos con `expect/actual` en `androidMain` y `wasmJsMain`. |
| **UI Framework** | Compose Multiplatform | 1.12.2 | Design System **Material 3** con UI compartida y responsiva para Web y Android. |
| **Target Web** | Kotlin/Wasm (`wasmJs`) | Compatible con Kotlin 2.4.x | Motor WebAssembly (WasmGC); descartado el target heredado Kotlin/JS. |
| **Target Android** | Android SDK | `minSdk` 26 (Android 8.0) / `targetSdk` y `compileSdk` 35/36 | Garantiza compatibilidad con AndroidX Security Crypto y políticas recientes de Google Play. |
| **Cliente HTTP** | Ktor Client | 3.6.x | Motor **OkHttp** en Android y motor **JS** (`fetch` del navegador) en Web (`wasmJs`). Plugin `ContentNegotiation` + inyección automática de Bearer JWT. |
| **Serialización** | `kotlinx.serialization.json` | Alineado con Backend | DTOs `@Serializable` alineados 1:1 con los contratos REST del Backend. |
| **Concurrencia / Estado** | Kotlinx Coroutines + `StateFlow` | Compatible con Kotlin 2.4.x | Flujo unidireccional de datos (UDF) con estados `UiState` inmutables. |
| **Navegación** | Jetpack Navigation Compose Multiplatform | Última estable compatible con CMP 1.12.2 | Navegación tipada con protección de rutas por sesión y rol. |
| **Inyección de Dependencias** | Koin Multiplatform | Última estable compatible con Kotlin 2.4.x | Módulos compartidos y específicos por plataforma (`expect/actual`). |
| **Linting** | `org.jlleitschuh.gradle.ktlint` (`ktlint`) | ktlint 1.8.x | Misma versión y reglas estrictas del Backend. |
| **Testing** | `kotlin-test` + `kotlinx-coroutines-test` + `Ktor MockEngine` + `Compose UI Test` | Ecosistema JetBrains/Kotlin | Cobertura mínima obligatoria del **90% general**; simulación estricta de respuestas HTTP (`200`, `201`, `400`, `403`, `409`, `500`) y validación semántica de UI. |

---

## 4. Arquitectura y Estructura de Directorios
* **Patrón de Arquitectura:** **MVVM (Model - View - ViewModel)** con flujo unidireccional de datos (UDF).
  1. **View (`ui/screens`, `ui/components`, `ui/navigation`):** Funciones `@Composable` compartidas (Material 3) que renderizan `UiState` y envían eventos al `ViewModel`. Prohibido invocar repositorios o clientes HTTP desde la UI.
  2. **ViewModel (`viewmodel`):** Orquesta las acciones de usuario mediante Coroutines, consume interfaces de repositorio y expone `StateFlow<UiState>` inmutable.
  3. **Model / Domain (`domain/model`, `domain/repository`):** Modelos de dominio del cliente e interfaces puras de repositorio.
  4. **Data (`data/remote`, `data/dto`, `data/mapper`, `data/repository`, `core/storage`):**
     - **Repository Pattern:** Implementaciones concretas que coordinan Ktor Client y el almacenamiento seguro de sesión (`expect/actual`).
     - **Mapper Pattern:** Mappers explícitos (`Dto -> Domain` y `Domain -> UiModel`) para evitar acoplar la UI o el `ViewModel` a cambios de contrato JSON.
* **Regla de Dependencias:** `View -> ViewModel -> Domain (Models & Repository Interfaces) <- Data (Repository Impl, DTOs, Mappers, Ktor Client)`. Los DTOs nunca atraviesan hacia `ViewModel` ni `View`.
* **Estructura Base:**
```text
frontend/
├── .specify/
│   └── memory/
│       └── constitution.md               # Esta Constitución
├── docs/
│   └── version_map.md                    # Mapa de versiones y roadmap Frontend
├── specs/                                # Especificaciones funcionales por feature
│   └── 001-feature-name/
│       ├── spec.md
│       ├── research.md
│       ├── data-model.md
│       ├── contracts.md
│       ├── plan.md
│       ├── checklist.md
│       ├── tasks.md
│       └── converge.md
├── composeApp/
│   └── src/
│       ├── commonMain/kotlin/com/itm/servicedesk/
│       │   ├── core/
│       │   │   ├── di/                   # Módulos Koin Multiplatform
│       │   │   ├── network/              # Configuración Ktor Client + Auth JWT
│       │   │   └── storage/              # Abstracción expect SessionStorage
│       │   ├── data/
│       │   │   ├── dto/                  # Contratos JSON (@Serializable)
│       │   │   ├── mapper/               # Mapper Pattern (DTO <-> Domain <-> UI)
│       │   │   ├── remote/               # Data sources HTTP
│       │   │   └── repository/           # Repository Pattern (Implementaciones)
│       │   ├── domain/
│       │   │   ├── model/                # Modelos de dominio Frontend
│       │   │   └── repository/           # Interfaces de repositorio
│       │   ├── viewmodel/                # ViewModels (MVVM + StateFlow)
│       │   └── ui/                       # Screens, Components, Navigation, Theme M3
│       ├── androidMain/kotlin/...        # Actual OkHttp, EncryptedSharedPreferences, MainActivity
│       ├── wasmJsMain/kotlin/...         # Actual JS fetch engine, sessionStorage, entry point Wasm
│       └── commonTest/kotlin/...         # kotlin-test, coroutines-test, MockEngine, Compose UI Test
├── .editorconfig
└── build.gradle.kts
```

---

## 5. Estándares de Calidad, Estilo, Seguridad y Testing
* **Cobertura Mínima de Tests:** **90% de forma general para el proyecto** (lógica pura, Mappers, Repositories con `Ktor MockEngine`, ViewModels con `kotlinx-coroutines-test` y pantallas con `Compose UI Test`).
* **Linter & Guía de Estilo (`org.jlleitschuh.gradle.ktlint` / ktlint 1.8.x):**
  - Cumplimiento estricto de la guía de estilo oficial de Kotlin.
  - **Prohibición absoluta de importaciones con comodín** (`*`).
  - **Indentación estricta de 4 espacios** (prohibido el uso de tabulaciones).
  - **Uso obligatorio de trailing commas** en declaraciones, parámetros y argumentos largos para diffs limpios en PRs de GitHub.
  - **Longitud máxima de línea de 120 caracteres**.
* **Manejo de Errores y UX:** Todos los códigos de error (`400`, `401`, `403`, `409`, `500`, fallos de red/timeout) deben mapearse en la capa de datos/ViewModel a estados de error amigables e inspeccionables en el árbol semántico de UI, sin exponer stacktraces ni detalles internos.
* **Accesibilidad y Responsividad:** Componentes Material 3 con semántica explícita (descripciones de contenido, etiquetas y estados verificables vía `Compose UI Test`) adaptados a Web Desktop y Móvil Android.

---

## 6. Proceso de Enmiendas
Cualquier cambio a esta Constitución requiere:
1. Justificación formal del impacto técnico y de deuda técnica.
2. Actualización de la versión de la constitución (SemVer: incremento mayor para cambios incompatibles).
3. Aprobación explícita del Tech Lead / Usuario antes de modificar este archivo.
