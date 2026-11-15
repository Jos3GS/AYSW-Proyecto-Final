# Feature Spec: 001-project-setup (Arquitectura Base, Red, Seguridad Local y Shell UI)
**Ubicación:** `specs/001-project-setup/spec.md`  
**Versión de Spec:** 1.0.0  
**Versión Destino:** `v0.1.0`  
**Estado:** Aprobada  

---

## 1. Resumen y Justificación
* **Problema:** Antes de implementar las pantallas de autenticación, registro de solicitudes y tableros por rol, el Frontend en **Compose Multiplatform 1.12.2** requiere una base arquitectónica sólida bajo **MVVM + Repository Pattern + Mapper Pattern**, capaz de compilar para **Web (`wasmJs`)** y **Android (`minSdk 26`, `targetSdk` 35/36)**, con configuración de entorno en tiempo de compilación (`BuildKonfig`), comunicación tipada vía Ktor Client 3.6.x hacia el Backend (pasando por Caddy HTTPS/TLS en producción), almacenamiento seguro de JWT (`expect/actual`), navegación base, tema Material 3 en español, formato de fechas `America/Bogota`, linter `ktlint 1.8.x` y suite de pruebas con cobertura $\ge 90\%$ verificada con **JetBrains Kover**.
* **Solución propuesta:** Inicializar el módulo multiplataforma `composeApp` (`commonMain`, `androidMain`, `wasmJsMain`, `commonTest`) usando **Gradle Version Catalog (`gradle/libs.versions.toml`)**, **BuildKonfig** y **JetBrains Kover**, configurar Koin Multiplatform, Jetpack Navigation Compose Multiplatform, tema Material 3 en español, utilidades de formato `dd/mm/aaaa hh:mm` (`America/Bogota`), abstracción `expect/actual` `SessionStorage`, cliente HTTP Ktor 3.6.x (OkHttp en Android / JS `fetch` en `wasmJs`) con `ContentNegotiation` (`kotlinx.serialization.json`) e inyector automático de Bearer JWT + interceptor de `401`, mappers de error estándar, flujo de verificación de conectividad contra `GET /api/v1/health` (sin datos simulados en runtime) y entorno de pruebas (`kotlin-test`, `kotlinx-coroutines-test`, `Ktor MockEngine`, `Compose UI Test`).
* **Fuera de alcance (Out of Scope):**
  - Pantallas funcionales de Login, Registro de Usuario Solicitante y Registro de Personal Interno (pertenecen a `002-auth-users`).
  - Formularios y bandejas de Solicitudes de Servicio e Historial (pertenecen a `003-service-requests` y `004-attention-lifecycle`).
  - Dashboard consolidado de Coordinación (pertenece a `005-coordination-reports`).
  - Cualquier funcionalidad excluida por RNF-10 (adjuntos, correos, WhatsApp, pagos).

---

## 2. Historias de Usuario / Capacidades Técnicas
* **US-01 (Esqueleto KMP & Calidad):** Como desarrollador Frontend, quiero configurar el proyecto Compose Multiplatform (`wasmJs` + Android) con `libs.versions.toml`, `BuildKonfig`, arquitectura MVVM, Koin, `ktlint 1.8.x` y **JetBrains Kover** ($\ge 90\%$), para mantener un único código base compartido y validado desde el inicio.
* **US-02 (Almacenamiento Seguro de Sesión JWT):** Como sistema de seguridad cliente, quiero disponer de una abstracción `SessionStorage` (`expect/actual`) que guarde el JWT en memoria + `sessionStorage` en Web (prohibido `localStorage`) y en `EncryptedSharedPreferences` con Android Keystore en Android, para proteger la sesión frente a XSS y extracción local.
* **US-03 (Cliente HTTP Tipado y Resiliente):** Como capa de datos, quiero un cliente Ktor 3.6.x configurado con `API_BASE_URL` (desde `BuildKonfig`), `kotlinx.serialization.json`, inyección automática de Bearer JWT, manejo global de errores (`400`, `401`, `403`, `404`, `409`, `500`, `503`) y mapeo DTO → Dominio → UI, para que ninguna pantalla consuma DTOs ni datos simulados.
* **US-04 (Shell UI Material 3 y Diagnóstico de Conectividad):** Como usuario en Web o Android, quiero ver una pantalla inicial en español bajo Material 3 que verifique la disponibilidad del Backend (`GET /api/v1/health`) y muestre mensajes claros con opción de reintento si el servicio está caído (`503` / sin red), para confirmar conectividad real sin exponer detalles técnicos.

---

## 3. Criterios de Aceptación (Escenarios BDD / Gherkin)

### Escenario 1: Almacenamiento seguro de JWT según plataforma (`expect/actual`)
* **Given (Dado que):** El sistema recibe un token JWT válido para persistir en sesión.
* **When (Cuando):** Se invoca `SessionStorage.saveToken(token)` en Web (`wasmJs`) o en Android.
* **Then (Entonces):**
  1. En **Web (`wasmJs`)**, el token queda en memoria y en `sessionStorage`, nunca en `localStorage`, y al limpiar sesión o cerrar la pestaña desaparece.
  2. En **Android**, el token se almacena cifrado mediante `EncryptedSharedPreferences` respaldado por Android Keystore y se borra al cerrar sesión.

### Escenario 2: Resolución de `API_BASE_URL` en tiempo de compilación (`BuildKonfig`)
* **Given (Dado que):** El proyecto se compila en perfil `Debug` o `Release` para el target `wasmJs` o `android`.
* **When (Cuando):** La capa de red inicializa el cliente HTTP Ktor leyendo la constante estática generada por `BuildKonfig`.
* **Then (Entonces):**
  1. En `Debug` Web (`wasmJs`), apunta por defecto a `http://localhost:8080` (o IP de red local parametrizada).
  2. En `Debug` Android Emulador, apunta por defecto a `http://10.0.2.2:8080` (y permite parametrizar `http://192.168.X.X:8080` para dispositivo físico).
  3. En `Release` (producción), usa la URL pública HTTPS/TLS del proxy inverso (ej. `https://api.dominio-proyecto.com`) inyectada por CI sin depender de archivos externos en tiempo de ejecución.

### Escenario 3: Verificación inicial de conectividad exitosa (`200 OK`)
* **Given (Dado que):** La aplicación Frontend inicia y el Backend responde en `GET /api/v1/health`.
* **When (Cuando):** El `StartupViewModel` ejecuta la consulta a través del `SystemHealthRepository`.
* **Then (Entonces):**
  1. Ktor Client realiza la petición HTTP real sin usar datos simulados (RNF-01).
  2. El `Mapper` transforma `HealthResponseDto` en el modelo de dominio `ServiceHealth` y luego a `StartupUiState.Ready` con fecha formateada en `dd/mm/aaaa hh:mm` (`America/Bogota`).
  3. La UI Material 3 renderiza el estado operativo en español con atributos semánticos verificables en `Compose UI Test`.

### Escenario 4: Indisponibilidad del Backend / Base de Datos (`503` o error de red)
* **Given (Dado que):** El Backend devuelve `HTTP 503 Service Unavailable` con el contrato estándar de error o no es alcanzable en red.
* **When (Cuando):** El `StartupViewModel` intenta verificar el servicio.
* **Then (Entonces):**
  1. El `ErrorMapper` traduce el código `503` o fallo de transporte a un mensaje amigable en español sin mostrar trazas, URLs internas ni stacktraces (RNF-04).
  2. La UI pasa a estado de error controlado y muestra un botón **"Reintentar"** verificable mediante árbol semántico.

### Escenario 5: Inyección automática de JWT e invalidación por `401 Unauthorized`
* **Given (Dado que):** Existe un token JWT almacenado en `SessionStorage`.
* **When (Cuando):** El cliente HTTP envía una petición a un endpoint protegido y el servidor responde `HTTP 401 Unauthorized` (token expirado tras 8h o inválido).
* **Then (Entonces):**
  1. La petición saliente incluyó la cabecera `Authorization: Bearer <token>`.
  2. Ante `401`, el interceptor limpia automáticamente el token en `SessionStorage` y emite el evento de sesión expirada para redirigir al flujo de autenticación.

### Escenario 6: Calidad de código y cobertura con `Ktor MockEngine`, `Compose UI Test` y Kover
* **Given (Dado que):** La capa de red, mappers, repositorios, ViewModel y pantalla inicial están implementados.
* **When (Cuando):** Se ejecutan `ktlintCheck`, la suite de pruebas multiplataforma (`commonTest`) y la verificación de **JetBrains Kover**.
* **Then (Entonces):**
  1. `ktlintCheck` (1.8.x) pasa con 0 errores (sin wildcards, 4 espacios, trailing commas, máx. 120 caracteres).
  2. Los tests con `Ktor MockEngine` verifican respuestas `200`, `400`, `401`, `403`, `404`, `409`, `500` y `503`.
  3. `Compose UI Test` verifica estados Loading, Ready y Error en el árbol semántico y JetBrains Kover confirma cobertura global $\ge 90\%$.

---

## 4. Contratos de Datos e Interfaces

### Configuración de Entorno Cliente (`BuildKonfig` — `API_BASE_URL`)
| Perfil | Target / Entorno | Valor por defecto / Inyección |
| :--- | :--- | :--- |
| **Debug** | Web (`wasmJs`) | `http://localhost:8080` (o IP local vía propiedad de build) |
| **Debug** | Android (Emulador) | `http://10.0.2.2:8080` |
| **Debug** | Android (Dispositivo físico) | `http://192.168.X.X:8080` (inyectado vía propiedad Gradle/entorno en compilación) |
| **Release** | Web (`wasmJs`) & Android | `https://api.dominio-proyecto.com` (inyectado por CI con TLS en Caddy) |

### DTOs de Entrada/Salida Esperados (Alineados 1:1 con Backend)
#### `HealthResponseDto` (`GET /api/v1/health` → `200 OK`)
```json
{
  "status": "UP",
  "service": "itm-service-desk-backend",
  "database": "UP",
  "timestamp": "2026-11-13T20:03:00-05:00"
}
```

#### `ApiErrorDto` (`400`, `401`, `403`, `404`, `409`, `500`, `503`)
```json
{
  "timestamp": "2026-11-13T20:03:00-05:00",
  "status": 503,
  "error": "SERVICE_UNAVAILABLE",
  "message": "El servicio de base de datos no se encuentra disponible temporalmente.",
  "path": "/api/v1/health",
  "details": []
}
```

### Contrato de Abstracción Multiplataforma (`SessionStorage`)
* `saveToken(token: String)`
* `getToken(): String?`
* `clearToken()`

---

## 5. Requisitos No Funcionales (NFRs)
* **Build y Cobertura (Constitución):** Gestión centralizada de dependencias con **Gradle Version Catalog (`gradle/libs.versions.toml`)**; `BuildKonfig` para constantes de entorno; `ktlint 1.8.x` sin errores; verificación automática de cobertura $\ge 90\%$ con **JetBrains Kover**.
* **Seguridad (Constitución / RNF-02 / RNF-04):** Prohibido `localStorage` en Web; uso obligatorio de `sessionStorage` + memoria (`wasmJs`) y `EncryptedSharedPreferences` (`androidMain`); cero contraseñas almacenadas o logueadas; errores amigables sin exposición técnica.
* **Usabilidad e Internacionalización (RNF-09):** Textos de interfaz 100% en español; formateador compartido `dd/mm/aaaa hh:mm` en zona horaria `America/Bogota`; diseño responsivo Material 3 adaptable a Web (`Chrome`, `Firefox`, `Edge` en `wasmJs`) y Android (`minSdk 26`, `targetSdk` 35/36).
* **Independencia de Capas (Constitución):** Los DTOs (`@Serializable`) nunca salen de la capa `data`; `ViewModel` y `View` operan únicamente con modelos de dominio / UI.
