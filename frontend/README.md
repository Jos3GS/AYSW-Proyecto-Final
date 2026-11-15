# ITM Service Desk — Frontend (Compose Multiplatform)

Frontend multiplataforma para **Web (`wasmJs`)** y **Android (`minSdk 26`, `targetSdk 35`)** construido con **Kotlin Multiplatform**, **Compose Multiplatform (Material 3)**, **MVVM + UDF**, **Koin**, **Ktor Client** y **BuildKonfig**.

## Arquitectura y Stack (`001-project-setup`)

- **Kotlin Multiplatform:** `2.4.21`
- **Compose Multiplatform:** `1.12.2` (Material 3 `1.9.0`, textos de UI 100% en español)
- **Targets autorizados:** `androidTarget()` (`minSdk 26`, `targetSdk 35`) y `wasmJs { browser() }`
- **Patrón de presentación:** MVVM + UDF (`StateFlow<StartupUiState>`, eventos globales por `SessionEventBus`)
- **Capa de datos:** `Repository` (`SystemHealthRepositoryImpl`), `RemoteDataSource` (`SystemHealthRemoteDataSource`) y `Mapper` (`HealthMapper`, `ErrorMapper`)
- **Red y serialización:** Ktor Client `3.6.1` (`OkHttp` en Android, `JS fetch` en `wasmJs`, `MockEngine` en pruebas) con `Json { ignoreUnknownKeys = false; explicitNulls = false }`
- **Almacenamiento de sesión JWT (`SessionStorage`):**
  - **Web (`wasmJsMain`):** memoria + `window.sessionStorage` (prohibido `localStorage` o cookies inseguras).
  - **Android (`androidMain`):** `EncryptedSharedPreferences` con `MasterKey` `AES256_GCM` sobre `Dispatchers.IO`.
- **Fechas:** `DateFormatter` basado en `kotlinx-datetime` en zona horaria fija `America/Bogota` con formato `dd/mm/aaaa hh:mm`.
- **Calidad:** `ktlint 1.8.0` + **JetBrains Kover** (`minBound(90)`).

## Estructura Principal (`frontend/composeApp/src`)

- `commonMain/kotlin/com/itm/servicedesk/`
  - `core/config/` — `expect fun resolveApiBaseUrl()`
  - `core/di/` — Módulos Koin (`appModules`, `networkModule`, `repositoryModule`, `viewModelModule`, `platformModule()`)
  - `core/network/` — `HttpClientFactory` (`DefaultApiJson`, interceptor JWT y detección global de `401`) y `PlatformHttpEngine`
  - `core/storage/` — `SessionStorage`, `SessionEventBus`
  - `core/time/` — `DateFormatter` (`America/Bogota`, `dd/mm/aaaa hh:mm`)
  - `data/` — `dto/`, `mapper/`, `remote/`, `repository/`
  - `domain/` — `model/` (`ServiceHealth`, `DomainError`, `AppResult`), `repository/`
  - `viewmodel/` — `StartupUiState`, `StartupUiMapper`, `StartupViewModel`
  - `ui/` — `theme/Theme.kt`, `navigation/AppNavHost.kt`, `screens/StartupScreen.kt`
- `androidMain/` — `ServiceDeskApp`, `MainActivity`, `AndroidSessionStorage`, `PlatformActuals`
- `debug/res/xml/network_security_config.xml` — Permite HTTP cleartext en desarrollo (`10.0.2.2`, `localhost`, `127.0.0.1`); `release` mantiene `usesCleartextTraffic="false"`
- `wasmJsMain/` — `Main.kt`, `WebSessionStorage`, `PlatformActuals`, `resources/index.html`

## Configuración de API con `BuildKonfig`

Por defecto, en compilación `debug` (`IS_RELEASE = false`):
- **Web (`wasmJs`):** `WEB_DEV_API_URL = "http://localhost:8080"`
- **Android Emulador:** `ANDROID_DEV_API_URL = "http://10.0.2.2:8080"`

### Overrides por línea de comandos

- **Dispositivo físico Android (LAN):**
  ```bash
  ./gradlew :composeApp:installDebug -PapiBaseUrl=http://192.168.1.50:8080
  ```
- **Compilación Release (HTTPS estricto):**
  ```bash
  ./gradlew :composeApp:assembleRelease :composeApp:wasmJsBrowserDistribution \
    -PisRelease=true \
    -PreleaseApiUrl=https://api.dominio-proyecto.com
  ```

## Comandos de Ejecución y Verificación

Ejecutar desde el directorio `frontend/`:

### Desarrollo Web (`wasmJs`)
```bash
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
```

### Build de Distribución Web (`wasmJs`)
```bash
./gradlew :composeApp:wasmJsBrowserDistribution
```

### Build e Instalación Android (`debug`)
```bash
./gradlew :composeApp:assembleDebug
# Para instalar en emulador o dispositivo conectado:
./gradlew :composeApp:installDebug
```

### Suite Completa de Calidad, Tests y Cobertura
```bash
./gradlew ktlintCheck commonTest koverVerify compileKotlinWasmJs assembleDebug wasmJsBrowserDistribution
```

- Reporte XML de cobertura:
  ```bash
  ./gradlew koverXmlReport
  ```
