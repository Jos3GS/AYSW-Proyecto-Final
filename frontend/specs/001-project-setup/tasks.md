# Checklist de Tareas Técnicas: 001-project-setup (Frontend)
**Ubicación:** `specs/001-project-setup/tasks.md`  
**Plan Asociado:** `frontend/specs/001-project-setup/plan.md`  
**Progreso:** 0% (0 / 7 tareas completadas)  

---

## Tareas de Implementación

### Fase 1: Estructura Multiplataforma, BuildKonfig, Kover y Contratos de Dominio
- [ ] **T01: Inicializar proyecto Compose Multiplatform (`wasmJs` + Android), Version Catalog, `BuildKonfig`, `ktlint 1.8.x` y `Kover`**
  - **Archivos:** `settings.gradle.kts`, `build.gradle.kts`, `composeApp/build.gradle.kts`, `gradle/libs.versions.toml`, `.editorconfig`, `.gitignore`, `composeApp/src/debug/res/xml/network_security_config.xml`
  - **Detalle:** Configurar Kotlin Multiplatform 2.4.x, CMP 1.12.2 (Material 3), targets `wasmJs` y Android (`minSdk 26`, `targetSdk`/`compileSdk` 35/36), `BuildKonfig` (`IS_RELEASE`, `RELEASE_API_URL`, `WEB_DEV_API_URL`, `ANDROID_DEV_API_URL`), `ktlint 1.8.x` (sin wildcards, 4 espacios, trailing commas, máx. 120 chars), JetBrains Kover (`minBound(90)`) y `usesCleartextTraffic="false"` en `release`.
  - **Verificación:** `./gradlew ktlintCheck` finaliza con código de salida 0.

- [ ] **T02: Definir DTOs `@Serializable`, modelos de dominio, `AppResult`, `DomainError`, `SessionStorage` (`expect`) y `SystemHealthRepository`**
  - **Archivos:** `composeApp/src/commonMain/.../data/dto/HealthResponseDto.kt`, `ApiErrorDto.kt`, `domain/model/ServiceHealth.kt`, `DomainError.kt`, `AppResult.kt`, `domain/repository/SystemHealthRepository.kt`, `core/storage/SessionStorage.kt`, `SessionEventBus.kt`
  - **Detalle:** Mantener aislamiento estricto entre DTOs (`data`) y modelos puros (`domain`).
  - **Verificación:** `./gradlew ktlintCheck compileCommonMainKotlinMetadata` pasa sin errores.

### Fase 2: Suite de Pruebas Unitarias, `Ktor MockEngine` y `Compose UI Test` (TDD Rojo)
- [ ] **T03: Escribir suite TDD inicial en `commonTest` (en rojo)**
  - **Archivos:** `composeApp/src/commonTest/.../DateFormatterTest.kt`, `HealthMapperTest.kt`, `ErrorMapperTest.kt`, `HttpClientAuthTest.kt`, `SystemHealthRepositoryTest.kt`, `StartupViewModelTest.kt`, `StartupScreenTest.kt`
  - **Detalle:** Cubrir escenarios BDD de `spec.md`: formato `dd/mm/aaaa hh:mm` (`America/Bogota`), mappers `HealthMapper` y `ErrorMapper` (`400`, `401`, `403`, `404`, `409`, `500`, `503`), `Ktor MockEngine` (inyección Bearer JWT, exclusión `/health`, limpieza + `SessionExpired` ante `401`), `StartupViewModel` (`kotlinx-coroutines-test` con `StandardTestDispatcher`) y árbol semántico en `Compose UI Test` (`Loading`, `Ready`, `Error` + clic en `Reintentar`).
  - **Verificación:** `./gradlew check` compila y falla inicialmente en rojo por implementaciones pendientes (`./gradlew ktlintCheck` en verde).

### Fase 3: Core Multiplataforma (`expect/actual`), Red Ktor y Capa Data (TDD Verde)
- [ ] **T04: Implementar `DateFormatter` (`kotlinx-datetime`), `resolveApiBaseUrl()`, `WebSessionStorage`, `AndroidSessionStorage` y motores nativos**
  - **Archivos:** `composeApp/src/commonMain/.../core/time/DateFormatter.kt`, `core/config/ApiEndpointConfig.kt`, `composeApp/src/androidMain/.../AndroidSessionStorage.kt`, `PlatformActuals.kt`, `composeApp/src/wasmJsMain/.../WebSessionStorage.kt`, `PlatformActuals.kt`
  - **Detalle:** `WebSessionStorage` respaldado únicamente en memoria + `window.sessionStorage` (prohibido `localStorage`); `AndroidSessionStorage` con `EncryptedSharedPreferences` (`MasterKey AES256_GCM`) en `Dispatchers.IO`; motores `OkHttp` (Android) y `JS fetch` (`wasmJs`).
  - **Verificación:** `./gradlew :composeApp:testDebugUnitTest --tests "*DateFormatterTest*"` pasa en verde y compilan ambos targets (`compileDebugKotlinAndroid`, `compileKotlinWasmJs`).

- [ ] **T05: Implementar `HttpClientFactory`, Mappers, `SystemHealthRemoteDataSource` y `SystemHealthRepositoryImpl`**
  - **Archivos:** `composeApp/src/commonMain/.../core/network/HttpClientFactory.kt`, `data/mapper/HealthMapper.kt`, `ErrorMapper.kt`, `data/remote/SystemHealthRemoteDataSource.kt`, `data/repository/SystemHealthRepositoryImpl.kt`
  - **Detalle:** `createHttpClient(engine, baseUrl, sessionStorage, sessionEventBus)` compartido entre producción y `MockEngine`; traducción limpia de todos los códigos HTTP y errores de red.
  - **Verificación:** `./gradlew :composeApp:testDebugUnitTest --tests "*MapperTest*" --tests "*HttpClientAuthTest*" --tests "*SystemHealthRepositoryTest*"` pasa al 100% en verde.

### Fase 4: MVVM (`StartupViewModel`), DI Koin y UI Material 3 (`wasmJs` + Android)
- [ ] **T06: Implementar `StartupUiState`, `StartupViewModel`, módulos Koin, tema Material 3, `AppNavHost`, `StartupScreen` y entry points**
  - **Archivos:** `composeApp/src/commonMain/.../viewmodel/StartupUiState.kt`, `StartupViewModel.kt`, `core/di/AppModules.kt`, `ui/theme/Theme.kt`, `ui/navigation/AppNavHost.kt`, `ui/screens/StartupScreen.kt`, `androidMain/.../ServiceDeskApp.kt`, `MainActivity.kt`, `wasmJsMain/.../main.kt`, `resources/index.html`
  - **Detalle:** Textos 100% en español, diseño responsivo Web/Móvil, semántica explícita (`testTag` / `contentDescription`), observador global de `SessionEventBus`.
  - **Verificación:** `./gradlew :composeApp:testDebugUnitTest ktlintCheck` pasa al 100% en verde (`StartupViewModelTest` y `StartupScreenTest`), y compilan `assembleDebug` y `wasmJsBrowserDistribution`.

### Fase 5: Quality Gate de Cobertura $\ge 90\%$ y Cierre Documental
- [ ] **T07: Verificar cobertura $\ge 90\%$ con JetBrains Kover, validar targets Web/Android y actualizar documentación**
  - **Archivos:** `docs/version_map.md`, `specs/001-project-setup/checklist.md`, `README.md`
  - **Detalle:** Documentar comandos de ejecución Web (`wasmJs`) y Android (emulador/dispositivo físico con `-PapiBaseUrl`) y actualizar progreso en `version_map.md`.
  - **Verificación:** `./gradlew check koverVerify` pasa con `0` errores en `ktlintCheck`, 100% de tests en verde y cobertura global $\ge 90\%$.
