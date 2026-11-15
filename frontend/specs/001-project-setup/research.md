# Investigación Técnica y Spikes: 001-project-setup (Frontend)
**Ubicación:** `specs/001-project-setup/research.md`  
**Spec Asociada:** `specs/001-project-setup/spec.md`  
**Estado:** Concluido (Aprobado)  

---

## 1. Preguntas de Investigación y Objetivos del Spike
1. **Resolución de `API_BASE_URL` multi-target con `BuildKonfig`:** ¿Cómo generar constantes estáticas en tiempo de compilación que distingan `Debug` Web (`http://localhost:8080`), `Debug` Android Emulador (`http://10.0.2.2:8080`), `Debug` Android Dispositivo Físico (`-PapiBaseUrl=http://192.168.X.X:8080`) y `Release` (`https://api.dominio-proyecto.com`)?
2. **Tráfico HTTP local en Android Debug vs HTTPS obligatorio en Release:** ¿Cómo permitir pruebas locales en `10.0.2.2:8080` sin relajar la exigencia de HTTPS/TLS en `Release` (Android API 28+ bloquea cleartext por defecto)?
3. **Almacenamiento seguro `SessionStorage` (`expect/actual`) en `wasmJs` y Android:** ¿Cómo operar `window.sessionStorage` en `wasmJs` y `EncryptedSharedPreferences` con Android Keystore en Android sin acoplar el código compartido (`commonMain`) a `Context` ni bloquear la UI?
4. **Formateo de fechas `dd/mm/aaaa hh:mm` (`America/Bogota`, RNF-09) en `wasmJs` + Android:** ¿Cómo convertir timestamps ISO-8601 del Backend a `dd/mm/aaaa hh:mm` en `commonMain` sin `java.time` (no disponible en `wasmJs`)?
5. **Inyección automática de JWT, `401` global y pruebas con `Ktor MockEngine` + `Kover` ($\ge 90\%$):** ¿Cómo diseñar `HttpClientFactory` para que funcione igual con `OkHttp` (Android), `JS fetch` (`wasmJs`) y `MockEngine` (`commonTest`)?

---

## 2. Alternativas Evaluadas y Trade-offs

| Decisión / Problema | Alternativas Evaluadas | Ventajas | Desventajas / Riesgos | Alineación con Constitución | Decisión |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Resolución de `API_BASE_URL` en `BuildKonfig`** | **Opción A:** Una única propiedad `BuildKonfig.API_BASE_URL` | Simple | No diferencia `localhost` (Web) de `10.0.2.2` (Emulador Android) en el mismo proyecto KMP | Limita DX multiplataforma | Descartada |
| | **Opción B:** `BuildKonfig` genera `IS_RELEASE`, `RELEASE_API_URL`, `WEB_DEV_API_URL`, `ANDROID_DEV_API_URL` (sobreescribible vía `-PapiBaseUrl`) + `expect fun resolveApiBaseUrl(): String` | Compilación estática segura, soporta Web dev, Android Emulador, Android físico y Release CI sin archivos externos | Requiere implementar `actual` trivial en `androidMain` y `wasmJsMain` | Cumple 100% | **Seleccionada** |
| **Formateo de fechas `America/Bogota` en KMP** | **Opción A:** `java.time` con `expect/actual` por plataforma | Usa SDK nativo en Android | Requiere interop JS `Intl.DateTimeFormat` en `wasmJs`, difícil de probar unitariamente en `commonTest` | Mayor complejidad | Descartada |
| | **Opción B:** **`kotlinx-datetime`** (ecosistema oficial JetBrains Kotlin) | Soporte multiplataforma nativo (`android` y `wasmJs`), zonas horarias explícitas (`TimeZone.of("America/Bogota")`), 100% testeable en `commonTest` y reutilizable para cálculos SLA | Requiere registrar la librería oficial en `gradle/libs.versions.toml` | Cumple ecosistema JetBrains | **Seleccionada** |
| **Inyección JWT y manejo de `401` en Ktor Client 3.6.x** | **Opción A:** Cabecera manual en cada función de `RemoteDataSource` | Explícito por llamada | Propenso a omisiones y código duplicado | Riesgo de seguridad | Descartada |
| | **Opción B:** Constructor `createHttpClient(engine, sessionStorage, sessionEvents)` usando plugin `HttpSend` + `HttpResponseValidator` | Inyección automática de `Bearer <jwt>`, limpieza automática y emisión de evento `SessionExpired` ante `401`, reutilizable 1:1 con `MockEngine` | Requiere excluir rutas públicas (`/health`, `/auth/login`, `/auth/register`) del envío de token expirado | Cumple 100% | **Seleccionada** |
| **Cleartext HTTP en Android Debug vs TLS Release** | **Opción A:** `android:usesCleartextTraffic="true"` en `AndroidManifest` principal | Rápido en local | Deja tráfico HTTP permitido en `Release` (vulnerabilidad grave) | Viola Constitución | Descartada |
| | **Opción B:** `network_security_config.xml` exclusivo en `src/debug/res/xml/` permitiendo `10.0.2.2`, `localhost` y red local; `usesCleartextTraffic="false"` estricto en `main`/`release` | Desarrollo ágil contra `10.0.2.2:8080` y seguridad TLS estricta en producción | Requiere definir dominio/IP de red local en debug si se usa dispositivo físico | Cumple 100% | **Seleccionada** |
| **Pruebas UI & Cobertura KMP ($\ge 90\%$)** | **Opción A:** Tests instrumentados Android únicamente | Prueba en dispositivo real | Lento, requiere emulador en cada corrida | Dificulta CI rápido | Descartada |
| | **Opción B:** `commonTest` con `kotlin-test`, `kotlinx-coroutines-test`, `Ktor MockEngine` y `Compose UI Test` (`runComposeUiTest`) + `JetBrains Kover` | Ejecución rápida sin servidor real, valida códigos `200/201/400/401/403/404/409/500/503` y árbol semántico | Algunas implementaciones `actual` de plataforma (`EncryptedSharedPreferences` / `sessionStorage`) requieren tests específicos de plataforma o fakes en `commonTest` | Cumple 100% | **Seleccionada** |

---

## 3. Análisis Técnico y Hallazgos

* **Hallazgo 1 — Interoperabilidad `wasmJs` con `sessionStorage` y `Ktor JS Engine`:**
  En Kotlin/Wasm (`wasmJs`), el acceso a `window.sessionStorage` se encapsula en `wasmJsMain` mediante un adaptador mínimo (`external object` o `js(...)` tipado), manteniendo un espejo en memoria para lecturas O(1) y asegurando que **nunca** se invoque `localStorage`. Para la red, `ktor-client-js` utiliza `fetch` del navegador respetando automáticamente las políticas CORS configuradas en el Backend (`CORS_ALLOWED_ORIGINS`).
* **Hallazgo 2 — Android `EncryptedSharedPreferences` e Inicialización Segura:**
  En `androidMain`, `AndroidSessionStorage(context: Context)` recibido vía `Koin` (`androidContext`) instancia `MasterKey` (`AES256_GCM`) y `EncryptedSharedPreferences`. Para evitar trabajo en el hilo principal en el arranque en frío, las lecturas/escrituras se ejecutan en `Dispatchers.IO` manteniendo una caché en memoria sincronizada tras la carga inicial.
* **Hallazgo 3 — Arquitectura de `HttpClientFactory` compatible con `MockEngine`:**
  Aislar la creación del `HttpClient` en `createHttpClient(engine: HttpClientEngine, ...)` en `commonMain` permite que `androidMain` provea `OkHttp.create()`, `wasmJsMain` provea `Js.create()`, y `commonTest` inyecte `MockEngine { ... }` sin duplicar la configuración de `ContentNegotiation`, `Json { ignoreUnknownKeys = true }`, `HttpTimeout` ni el interceptor JWT/`401`.
* **Hallazgo 4 — Semántica de Accesibilidad en Compose Multiplatform (`Compose UI Test`):**
  Todos los componentes interactivos y mensajes de estado en Material 3 definirán `Modifier.testTag(...)` y/o `semantics { contentDescription = ... }` estables en `commonMain`, garantizando pruebas deterministas independientes del idioma de dispositivo o resolución.
* **Hallazgo 5 — Manejo Multiplataforma de Fechas (`kotlinx-datetime`):**
  Se incorpora **`kotlinx-datetime`** en `gradle/libs.versions.toml` para convertir timestamps ISO-8601 a `TimeZone.of("America/Bogota")` y formatear `dd/mm/aaaa hh:mm` en `commonMain` (`DateFormatter`), dejando una única implementación compartida y 100% testeable para Web y Android.

---

## 4. Decisión Final Justificada
> Se adopta `BuildKonfig` combinado con `expect fun resolveApiBaseUrl()` para aislar por perfil/target la URL del Backend, `src/debug/res/xml/network_security_config.xml` en Android para mantener TLS estricto en `Release`, `createHttpClient(engine, ...)` compartido entre `OkHttp`/`JS`/`MockEngine` con inyección automática de Bearer JWT y manejo centralizado de `401` + códigos `400..503`, `SessionStorage` `expect/actual` (`sessionStorage`+memoria en `wasmJs` y `EncryptedSharedPreferences` en Android), `kotlinx-datetime` para `dd/mm/aaaa hh:mm` (`America/Bogota`), y `commonTest` + `JetBrains Kover` para validar cobertura $\ge 90\%$.
