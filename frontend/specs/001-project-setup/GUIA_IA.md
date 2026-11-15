---
proyecto: ITM Service Desk
subproyecto: Frontend
feature: 001-project-setup
categoria: Spec_Kit / Guia_IA
fecha: 2026-11-14
version: 1.0.0
estado: Aprobado
tags:
  - spec-kit
  - frontend
  - guia-ia
  - prompt-maestro
  - kmp
  - compose-multiplatform
---

# Guía de Implementación Asistida por IA — 001-project-setup (Frontend)

> **Estado:** Aprobado

---

## Prompt Maestro para la Sesión de Implementación

```text
Actúa como un Ingeniero Senior especialista en Kotlin Multiplatform (KMP), Compose Multiplatform (Android + WasmJs) y Arquitectura MVVM + UDF.
Tu misión es ejecutar EXCLUSIVAMENTE la épica `001-project-setup` del Frontend de **ITM Service Desk**, siguiendo estrictamente las especificaciones aprobadas del repositorio en `frontend/`.

PASO 0 — VERIFICACIÓN OBLIGATORIA DE PRERREQUISITOS (BLOQUEANTE):
Antes de crear, generar o modificar cualquier archivo de código o Gradle, verifica en la máquina local que todo el entorno requerido esté instalado y operativo:
1. Java / JDK 21: ejecuta `java -version` y confirma JDK 21 activo en `JAVA_HOME` (Java 21 LTS).
2. Android SDK: confirma que `ANDROID_HOME` (o `ANDROID_SDK_ROOT`) apunte a un SDK válido con `platforms;android-35` o `android-36`, `platform-tools` y `build-tools` instalados (`minSdk = 26`).
3. Entorno WebAssembly (`wasmJs`): confirma que existe un navegador moderno compatible con WasmGC (Chrome, Edge o Firefox) disponible para validar `wasmJsBrowserDistribution` y la ejecución de tests KMP.
4. Git y Estructura Spec-Kit: confirma `git --version` y verifica que existen en disco la Constitución y los 7 documentos aprobados de `frontend/specs/001-project-setup/` (`spec.md`, `research.md`, `data-model.md`, `contracts.md`, `plan.md`, `checklist.md`, `tasks.md`).
SI CUALQUIER VERIFICACIÓN DEL PASO 0 FALLA O FALTA: DETENTE INMEDIATAMENTE, reporta con exactitud qué falta y NO generes código parcial.

DOCUMENTOS DE LECTURA OBLIGATORIA (EN ESTE ORDEN, DESPUÉS DEL PASO 0):
1. `frontend/.specify/memory/constitution.md` (Constitución inmutable del Frontend)
2. `frontend/specs/001-project-setup/spec.md`
3. `frontend/specs/001-project-setup/research.md`
4. `frontend/specs/001-project-setup/data-model.md`
5. `frontend/specs/001-project-setup/contracts.md`
6. `frontend/specs/001-project-setup/plan.md`
7. `frontend/specs/001-project-setup/checklist.md`
8. `frontend/specs/001-project-setup/tasks.md`

REGLAS INNEGOCIABLES DE IMPLEMENTACIÓN:
- Arquitectura estricta: MVVM + UDF (`StateFlow<UiState>`, `Channel` de eventos one-shot) + `Repository` + `Mapper` + `RemoteDataSource` con Koin DI. Prohibido hacer llamadas HTTP o lógica de negocio dentro de `@Composable`.
- Targets obligatorios: `androidTarget()` (`minSdk 26`, `targetSdk 35/36`) y `wasmJs { browser() }`. Prohibido introducir `js()`, `ios*()`, `jvm()` desktop u otros targets fuera del alcance.
- Configuración de API con `BuildKonfig`: definir `IS_RELEASE`, `RELEASE_API_URL`, `WEB_DEV_API_URL` (`http://localhost:8080`) y `ANDROID_DEV_API_URL` (`http://10.0.2.2:8080`), resueltas vía `expect fun resolveApiBaseUrl()` / `actual`.
- Almacenamiento de sesión multiplataforma (`SessionStorage`):
  - En `wasmJsMain` (`WebSessionStorage`): memoria + `window.sessionStorage` para que la sesión no sobreviva al cierre del navegador. PROHIBIDO usar `localStorage` o cookies inseguras.
  - En `androidMain` (`AndroidSessionStorage`): `EncryptedSharedPreferences` con `MasterKey` `AES256_GCM` ejecutado fuera del hilo principal (`Dispatchers.IO`).
- Cliente HTTP (`HttpClientFactory`): Ktor Client (`OkHttp` en Android, `JS` en wasmJs, `MockEngine` en `commonTest`) con `Json { ignoreUnknownKeys = false; explicitNulls = false }`.
- Formato de fechas (`DateFormatter`): usar `kotlinx-datetime` en zona fija `America/Bogota` con formato exacto `dd/mm/aaaa hh:mm`.
- Calibración de calidad: código formateado según `ktlint` 1.8.x y cobertura verificada con Kover >= 90% (`koverVerify` con `minBound(90)`).
- Alcance estricto: implementa únicamente `T01..T07` de `001-project-setup` (estructura Gradle/libs.versions.toml, BuildKonfig, Core Network/Storage/DateFormatter, DI Koin, `StartupScreen` y pruebas unitarias). No implementes login, usuarios ni solicitudes todavía.

METODOLOGÍA DE TRABAJO:
- Trabaja de forma secuencial siguiendo `T01` a `T07` de `tasks.md`.
- Al finalizar las tareas, ejecuta obligatoriamente en consola:
  `./gradlew ktlintCheck commonTest koverVerify compileKotlinWasmJs assembleDebug wasmJsBrowserDistribution`
- No consideres terminada la épica si algún test unitario, `ktlintCheck`, `koverVerify` o compilación de ambos targets falla.
```

---

## Registro de Correcciones Críticas Frente a Desviaciones Comunes de la IA

| Desviación habitual de la IA | Por qué está mal según la Constitución / Spec | Corrección exigida a la IA |
|---|---|---|
| Iniciar a escribir `build.gradle.kts` o Kotlin sin validar JDK 21 ni `ANDROID_HOME` | Falla en la primera compilación de `assembleDebug` o usa una JVM incompatible con Kotlin 2.3.x / Gradle 8.x/9.x | Ejecutar el **PASO 0** verificando JDK 21, `ANDROID_HOME`, navegador WasmGC y existencia previa de la Constitución y los 7 docs de `001-project-setup`. |
| Guardar el JWT en `window.localStorage` en `wasmJs` o `SharedPreferences` en texto plano en Android | Viola los requisitos de seguridad de la Constitución (`RNF` de almacenamiento volátil en web y cifrado en Android) | Exigir `window.sessionStorage` + memoria en `WebSessionStorage` y `EncryptedSharedPreferences` + `MasterKey AES256_GCM` en `Dispatchers.IO` para `AndroidSessionStorage`. |
| Hardcodear `"http://localhost:8080"` dentro de `HttpClientFactory` | Rompe el emulador Android (que requiere `10.0.2.2:8080`) y los despliegues Release | Usar `BuildKonfig` + `expect/actual resolveApiBaseUrl()` leyendo `IS_RELEASE`, `RELEASE_API_URL`, `WEB_DEV_API_URL` y `ANDROID_DEV_API_URL`. |
| Configurar `Json { ignoreUnknownKeys = true }` en Ktor `ContentNegotiation` | Oculta desviaciones silenciosas entre los contratos del Backend y los DTOs del Frontend | Configurar estrictamente `ignoreUnknownKeys = false` y `explicitNulls = false` según `contracts.md` y la Constitución. |
| Usar `java.time.format.DateTimeFormatter` o `SimpleDateFormat` en `commonMain` | No compila en target `wasmJsMain` por depender de APIs JVM | Implementar `DateFormatter` exclusivamente con `kotlinx-datetime` en `America/Bogota` con formato `dd/mm/aaaa hh:mm`. |
| Omitir `koverVerify` o bajar el umbral por debajo de `90%` | Viola la regla constitucional de cobertura mínima del `90%` | Configurar `minBound(90)` en Kover y validar pruebas de `SessionStorage`, `HttpClientFactory` (con `MockEngine`), `DateFormatter` y mapeo de errores. |
| Inventar versiones de Kotlin, Compose Multiplatform, Ktor o Koin desactualizadas | Desalinea el `libs.versions.toml` frente al stack aprobado (`version_map.md` / `research.md`) | Respetar exactamente las versiones fijadas en `frontend/gradle/libs.versions.toml` definidas en `plan.md` y `research.md`. |
