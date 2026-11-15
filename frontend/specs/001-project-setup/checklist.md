# Checklist de Calidad y Puertas de Aprobación (Quality Gates): 001-project-setup (Frontend)
**Ubicación:** `specs/001-project-setup/checklist.md`  
**Spec Asociada:** `frontend/specs/001-project-setup/spec.md`  
**Estado:** Aprobado para Código  

---

## 1. Validación contra la Constitución
- [x] El diseño técnico respeta el stack autorizado: **Kotlin Multiplatform 2.4.x**, **Compose Multiplatform 1.12.2 (Material 3)**, **Web `wasmJs`** (descartado Kotlin/JS), **Android (`minSdk 26`, `targetSdk`/`compileSdk` 35/36)**, **Ktor Client 3.6.x** (`OkHttp` / `JS fetch`), **`kotlinx.serialization.json`**, **`kotlinx-datetime`**, **Jetpack Navigation Compose Multiplatform**, **Koin Multiplatform**, **BuildKonfig**, **`ktlint 1.8.x`** y **JetBrains Kover**.
- [x] Todas las dependencias están centralizadas en **Gradle Version Catalog (`gradle/libs.versions.toml`)** y no se introducen librerías fuera de lo aprobado en la Constitución y `research.md`.
- [x] Se cumple estrictamente **MVVM + UDF** (`StateFlow`), **Repository Pattern** (`SystemHealthRepository`) y **Mapper Pattern** (`HealthMapper`, `ErrorMapper`), aislando los DTOs `@Serializable` dentro de la capa `data`.
- [x] Se exigen las 4 reglas estrictas de `org.jlleitschuh.gradle.ktlint` (cero importaciones `*`, 4 espacios, trailing commas, máx. 120 caracteres) y cobertura general $\ge 90\%$ verificada con **JetBrains Kover**.

## 2. Completitud de la Especificación, Seguridad y UX
- [x] Todas las historias técnicas (`US-01` a `US-04`) cuentan con escenarios BDD (`Given / When / Then`) verificables en `spec.md`.
- [x] Los contratos HTTP (`GET /api/v1/health`, `HealthResponseDto`, `ApiErrorDto` para `400`, `401`, `403`, `404`, `409`, `500`, `503` y errores de red) y `BuildKonfig` (`Debug` Web/Android Emulador/Físico y `Release` HTTPS) están definidos en `contracts.md` y `plan.md`.
- [x] La estrategia de almacenamiento seguro de JWT (`SessionStorage` `expect/actual`) prohíbe `localStorage` en `wasmJs` (memoria + `sessionStorage`) y exige `EncryptedSharedPreferences` (`AES256_GCM`) en Android, con `network_security_config.xml` limitado exclusivamente al source set `src/debug/`.
- [x] La interfaz en español, el formato temporal `dd/mm/aaaa hh:mm` (`America/Bogota` vía `kotlinx-datetime`), el botón de reintento ante `503`/fallo de red y las etiquetas semánticas para `Compose UI Test` están especificados.
- [x] No quedan puntos pendientes marcados con `[NECESITA ACLARACIÓN]`.

## 3. Preparación para Implementación (`tasks.md`)
- [x] El desglose secuencial en `specs/001-project-setup/tasks.md` está aprobado y listo para ejecución TDD.
- [x] Cada tarea de `tasks.md` define verificación automatizada (`ktlintCheck`, `Ktor MockEngine`, `kotlinx-coroutines-test`, `Compose UI Test`, `koverVerify`).
- [x] Aprobación explícita final para iniciar la implementación de `001-project-setup` (Frontend).
