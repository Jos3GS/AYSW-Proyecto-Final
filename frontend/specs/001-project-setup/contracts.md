# Contratos de APIs e Interfaces: 001-project-setup (Frontend)
**Ubicación:** `specs/001-project-setup/contracts.md`  
**Spec Asociada:** `frontend/specs/001-project-setup/spec.md`  
**Protocolo:** REST / JSON vía Ktor Client 3.6.x (`kotlinx.serialization.json`, `API_BASE_URL` vía `BuildKonfig`)  
**Estado:** Aprobado  

---

## 1. Endpoints Consumidos

### `GET {API_BASE_URL}/api/v1/health`
* **Descripción:** Consulta la disponibilidad real del Backend y de PostgreSQL durante el arranque o ante reintento del usuario (RNF-01, RNF-08).
* **Autenticación:** Pública (exenta de cabecera `Authorization`).
* **Headers enviados:**
  * `Accept`: `application/json`
* **Resolución de `API_BASE_URL` (`BuildKonfig` + `expect/actual`):**
  * Web `wasmJs` (Debug): `http://localhost:8080` (o IP local configurada).
  * Android Emulador (Debug): `http://10.0.2.2:8080`.
  * Android Dispositivo Físico (Debug): `-PapiBaseUrl=http://192.168.X.X:8080`.
  * Release (Web & Android): `https://api.dominio-proyecto.com` (terminación TLS en Caddy).

#### Response `200 OK` (`HealthResponseDto` → `ServiceHealth`)
```json
{
  "status": "UP",
  "service": "itm-service-desk-backend",
  "database": "UP",
  "timestamp": "2026-11-14T11:24:00-05:00"
}
```
* **Transformación (`HealthMapper` + `StartupUiMapper`):** `timestamp` se parsea con `kotlinx-datetime` y se convierte a zona `America/Bogota` con formato visible `dd/mm/aaaa hh:mm` (`StartupUiState.Ready`).

#### Response `503 Service Unavailable` (`ApiErrorDto` → `DomainError.ServiceUnavailable`)
```json
{
  "timestamp": "2026-11-14T11:24:00-05:00",
  "status": 503,
  "error": "SERVICE_UNAVAILABLE",
  "message": "El servicio de base de datos no se encuentra disponible temporalmente.",
  "path": "/api/v1/health",
  "details": []
}
```

---

## 2. Contrato Transversal de Cliente HTTP (`HttpClientFactory` & `ErrorMapper`)

### Inyección Automática de JWT (Rutas Protegidas)
* **Rutas excluidas de token:** `/api/v1/health`, `/api/v1/auth/login`, `/api/v1/auth/register`.
* **Resto de rutas:** El interceptor lee `SessionStorage.getToken()`; si existe, inyecta automáticamente:
  * `Authorization`: `Bearer <token>`
  * `Content-Type`: `application/json` (en peticiones con cuerpo)

### Matriz de Mapeo Contractual de Errores HTTP (Simulable vía `Ktor MockEngine`)

| Condición / Código HTTP | Mapeo a `DomainError` | Efecto Secundario / Acción en Cliente |
| :---: | :--- | :--- |
| **`400 Bad Request`** | `DomainError.BadRequest(message, fieldErrors)` | Extrae `details[]` para pintar mensajes junto a cada campo (RNF-09). |
| **`401 Unauthorized`** | `DomainError.Unauthorized(message)` | Invoca `SessionStorage.clearToken()` y emite `SessionEvent.SessionExpired` en `SessionEventBus` (en rutas protegidas). |
| **`403 Forbidden`** | `DomainError.Forbidden(message)` | Muestra mensaje controlado de permisos insuficientes sin detalles internos. |
| **`404 Not Found`** | `DomainError.NotFound(message)` | Muestra mensaje controlado del recurso o ruta no encontrada. |
| **`409 Conflict`** | `DomainError.Conflict(message)` | Muestra conflicto de negocio/unicidad (ej. correo, documento o transición inválida). |
| **`503 Service Unavailable`** | `DomainError.ServiceUnavailable(message)` | Muestra pantalla/estado controlado de indisponibilidad con botón **Reintentar** (RNF-08). |
| **`500` / otros `5xx` / JSON corrupto** | `DomainError.UnexpectedServerError(message)` | Sustituye por mensaje genérico seguro en español; jamás expone JSON crudo ni trazas (RNF-04). |
| **Timeout / Sin red** | `DomainError.NetworkUnavailable(message)` | Muestra estado de conectividad fallida con botón **Reintentar**. |

---

## 3. Contratos Internos Multiplataforma (`expect/actual` y Eventos)

### `SessionStorage` (`core/storage`)
* **`wasmJsMain`:** Respaldado en memoria + `window.sessionStorage` (prohibido `localStorage`).
* **`androidMain`:** Respaldado en `EncryptedSharedPreferences` (`MasterKey AES256_GCM` en Android Keystore).

### `SessionEventBus` (Eventos Internos en `commonMain`)
* **Canal:** `SharedFlow<SessionEvent>` (observado por el host raíz `AppNavHost`).
* **Eventos:**
  * `SessionEvent.SessionExpired`: Navega al flujo de login limpiando la pila trasera tras un `401`.
  * `SessionEvent.LoggedOut`: Limpia `SessionStorage` y reinicia el grafo de navegación.
