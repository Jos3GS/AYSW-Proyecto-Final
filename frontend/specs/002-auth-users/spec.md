# Feature Spec: 002-auth-users (Pantallas de Autenticación, Sesión Segura JWT, Guardias RBAC y Gestión de Personal)
**Ubicación:** `specs/002-auth-users/spec.md`  
**Versión de Spec:** 1.0.0  
**Versión Destino:** `v0.1.0`  
**Estado:** Aprobada  

---

## 1. Resumen y Justificación
* **Problema:** Los tres actores del sistema (*Usuario solicitante*, *Personal de atención* y *Coordinador*) necesitan una interfaz intuitiva en español (Web `wasmJs` y Android) para autenticarse, mantener su sesión JWT segura de 1 hora durante la navegación, cerrar sesión revocando su token en el servidor y acceder únicamente a las pantallas permitidas según su rol. Asimismo, el solicitante necesita auto-registrarse quedando autenticado inmediatamente, y el Coordinador requiere pantallas dedicadas para registrar y consultar de forma paginada al personal interno.
* **Solución propuesta:** Implementar sobre la arquitectura **MVVM + UDF + Repository Pattern + Mapper Pattern** el módulo Frontend de `002-auth-users`: pantallas Material 3 responsivas de **Inicio de Sesión**, **Registro de Solicitante** (auto-login al completar `201`), **Restauración de Sesión / Guardia RBAC por Rol** (`GET /api/v1/auth/me` y manejo global de `401`/`403`), **Cierre de Sesión Seguro** (`POST /api/v1/auth/logout` + limpieza de `SessionStorage`), y el módulo exclusivo para `COORDINADOR` con **Listado Paginado de Personal** (`20/pág`, `GET /api/v1/users/staff`) y **Formulario de Registro de Personal Interno** (`POST /api/v1/users/staff`), todo con validaciones previas alineadas a la Tabla 1, fechas `dd/mm/aaaa hh:mm` (`America/Bogota`) y pruebas con `Ktor MockEngine` + `Compose UI Test` ($\ge 90\%$ Kover).
* **Fuera de alcance (Out of Scope):**
  - Recuperación de contraseña por correo o notificaciones externas (prohibido por RNF-10).
  - Pantallas funcionales de creación/gestión de solicitudes de servicio y reportes (`003-service-requests`, `004-attention-lifecycle`, `005-coordination-reports`), para las cuales en esta épica se habilitan las vistas base/shell autenticadas por rol y navegación hacia Personal Interno.
  - Edición o eliminación de usuarios del personal interno (no requerido en HU-07 / RF-07).

---

## 2. Historias de Usuario
* **HU-01 (Registro y Sesión del Solicitante):** Como *Usuario solicitante*, quiero registrarme desde una pantalla clara con validación inmediata y quedar autenticado automáticamente, así como iniciar y cerrar sesión de forma segura, para ingresar al portal desde Web o Android.
* **US-Auth-FE-01 (Inicio de Sesión Multirrol, Persistencia Segura y Expiración 1h):** Como cualquier actor autenticado, quiero iniciar sesión con mi correo y contraseña recibiendo un nuevo JWT válido por 1 hora almacenado de forma segura por plataforma (`sessionStorage` en Web; `EncryptedSharedPreferences` en Android), y ser redirigido al login si mi sesión expira o es revocada (`401`).
* **HU-07 (Gestión Visual de Personal Interno para Coordinación):** Como *Coordinador*, quiero consultar el listado paginado del personal interno (20 por página, con filtro por rol interno y fecha `dd/mm/aaaa hh:mm`) y registrar nuevas cuentas de *Personal de atención* o *Coordinador* desde un formulario protegido por RBAC.

---

## 3. Criterios de Aceptación (Escenarios BDD / Gherkin)

### Escenario 1: Auto-registro de solicitante con auto-login exitoso (HU-01 / RF-01)
* **Given (Dado que):** El usuario se encuentra en la pantalla **Crear cuenta** (`RegisterScreen`).
* **When (Cuando):** Ingresa datos válidos según la Tabla 1 (`fullName`, `documentNumber`, `email`, `phone`, `password` enmascarada), corrige eventuales errores mostrados en línea y pulsa **"Registrarse"**.
* **Then (Entonces):**
  1. `RegisterViewModel` valida las reglas de Tabla 1 antes de invocar `AuthRepository.register(...)` (no envía campo `role`).
  2. Ante `201 Created` (`AuthResponseDto`), `SessionStorage.saveToken(accessToken)` persiste el JWT de 1 hora de forma segura por plataforma, limpia las contraseñas en memoria del `UiState` y navega automáticamente a la pantalla principal de `SOLICITANTE` limpiando el backstack de autenticación.

### Escenario 2: Errores de validación (`400`) y conflicto de duplicado (`409`) en formularios
* **Given (Dado que):** El usuario envía datos inválidos o ya existentes en `RegisterScreen` o `CreateStaffScreen`.
* **When (Cuando):** El Backend responde `HTTP 400 Bad Request` (`details[]`) o `HTTP 409 Conflict` (`ApiErrorDto`).
* **Then (Entonces):**
  1. `ErrorMapper` mapea `details[]` a cada campo específico del formulario (`fieldErrors`), mostrando los mensajes en español bajo el campo correspondiente.
  2. Ante `409 Conflict`, la UI muestra un mensaje claro en español informando que el correo o documento ya está registrado, sin perder el resto del formulario ni limpiar la sesión actual (en caso de `CreateStaffScreen`).

### Escenario 3: Inicio de sesión válido y credencial errónea en `LoginScreen`
* **Given (Dado que):** El usuario está en `LoginScreen` (Web o Android).
* **When (Cuando):** Envía correo y contraseña hacia `POST /api/v1/auth/login`.
* **Then (Entonces):**
  1. Si el Backend responde `200 OK`, guarda el **nuevo JWT** (expiración 1 hora) en `SessionStorage`, borra la contraseña del `UiState` y navega al shell/inicio correspondiente al rol (`SOLICITANTE`, `PERSONAL_ATENCION` o `COORDINADOR`).
  2. Si el Backend responde `401 Unauthorized`, la UI permanece en `LoginScreen` (sin activar bucle de `SessionExpired`), mantiene limpio `SessionStorage` y muestra el mensaje amigable `"Credenciales inválidas."`.

### Escenario 4: Restauración de sesión al iniciar y manejo de token vencido/revocado (`401`)
* **Given (Dado que):** La aplicación arranca tras verificar `GET /api/v1/health` y existe un token previo guardado en `SessionStorage` (ej. recarga de pestaña en Web o reapertura de la app Android).
* **When (Cuando):** El coordinador de arranque consulta `GET /api/v1/auth/me`.
* **Then (Entonces):**
  1. Si responde `200 OK`, restaura el perfil en memoria y navega al destino del rol sin pedir credenciales de nuevo.
  2. Si responde `401 Unauthorized` (por expiración > 1h o revocación en servidor), limpia `SessionStorage` y redirige a `LoginScreen` con aviso informativo de sesión expirada.
  3. Lo mismo ocurre si durante cualquier pantalla protegida posterior un endpoint responde `401 Unauthorized`.

### Escenario 5: Cierre de sesión seguro con confirmación (RNF-09 + Blacklist Backend)
* **Given (Dado que):** Un usuario autenticado pulsa **"Cerrar sesión"** en la barra superior o menú de navegación.
* **When (Cuando):** Confirma el diálogo *"¿Deseas cerrar tu sesión actual?"* (RNF-09).
* **Then (Entonces):**
  1. `SessionViewModel` invoca `POST /api/v1/auth/logout` con el Bearer JWT vigente para que el servidor revoque el `jti`.
  2. Tanto ante `204 No Content` como ante un eventual `401` previo, se ejecuta `SessionStorage.clearToken()`, se emite `SessionEvent.LoggedOut`, se limpia el estado de usuario y se navega a `LoginScreen` eliminando todo historial de navegación previo.

### Escenario 6: Gestión de personal interno exclusiva de `COORDINADOR` (HU-07 / RNF-03 / RNF-07)
* **Given (Dado que):** El usuario autenticado tiene rol `COORDINADOR`.
* **When (Cuando):** Accede a **Personal interno** (`StaffListScreen`) y opcionalmente abre **Registrar personal** (`CreateStaffScreen`).
* **Then (Entonces):**
  1. `StaffListViewModel` consulta `GET /api/v1/users/staff?page=1&size=20` (con filtro opcional `PERSONAL_ATENCION` | `COORDINADOR`), mostrando hasta 20 registros por página con nombre, correo, etiqueta de rol legible en español y fecha formateada `dd/mm/aaaa hh:mm` (`America/Bogota`), además de controles **Anterior** / **Siguiente**.
  2. En `CreateStaffScreen`, puede elegir únicamente rol *Personal de atención* o *Coordinador*, confirmar en el diálogo *"¿Confirmas el registro de este integrante del personal?"* (RNF-09), enviar `POST /api/v1/users/staff`, y tras `201 Created` regresar o refrescar el listado conservando intacta su propia sesión de Coordinador.

### Escenario 7: Protección de rutas por rol (RBAC Guard en Navegación y `403 Forbidden`)
* **Given (Dado que):** Un usuario autenticado posee rol `SOLICITANTE` o `PERSONAL_ATENCION`.
* **When (Cuando):** Navega en la aplicación o intenta acceder directa/programáticamente a la ruta de **Personal interno**.
* **Then (Entonces):**
  1. La navegación oculta las opciones administrativas de Coordinación para esos roles y el guardia de ruta bloquea la entrada mostrando acceso denegado o redirigiendo a su inicio autorizado.
  2. Si por algún flujo el Backend responde `403 Forbidden`, la UI muestra un mensaje controlado en español sin cerrar la sesión del usuario.

---

## 4. Contratos de Pantallas, Estados UI y Endpoints Consumidos

### Flujo de Navegación Tipada (`AppNavHost`)
* **Rutas Públicas:** `Startup` → `Login` ↔ `Register`
* **Rutas Protegidas (requieren sesión verificada):**
  * `ApplicantHome` (rol `SOLICITANTE`)
  * `AgentHome` (rol `PERSONAL_ATENCION`)
  * `CoordinatorHome` (rol `COORDINADOR`) → `StaffList` → `CreateStaff`

### Endpoints Backend Consumidos (`002-auth-users`)
| Acción Frontend | Método y Ruta | Uso de JWT | Respuesta Esperada |
| :--- | :--- | :---: | :--- |
| Registro + Auto-login | `POST /api/v1/auth/register` | No | `201 Created` (`AuthResponseDto`) |
| Inicio de sesión | `POST /api/v1/auth/login` | No | `200 OK` (`AuthResponseDto`) |
| Restaurar / validar sesión | `GET /api/v1/auth/me` | Sí (`Bearer`) | `200 OK` (`UserProfileDto`) |
| Cerrar sesión (revocar `jti`) | `POST /api/v1/auth/logout` | Sí (`Bearer`) | `204 No Content` |
| Listar personal interno | `GET /api/v1/users/staff` | Sí (`COORDINADOR`) | `200 OK` (`PagedStaffResponseDto`) |
| Registrar personal interno | `POST /api/v1/users/staff` | Sí (`COORDINADOR`) | `201 Created` (`UserProfileDto`) |

---

## 5. Requisitos No Funcionales (NFRs)
* **Seguridad Local (Constitución Frontend & RNF-04):** Prohibido usar `localStorage` en Web (`wasmJs` usa memoria + `sessionStorage`); Android usa `EncryptedSharedPreferences`; prohibido almacenar o loguear contraseñas o JWT en consola/Crashlytics/navigation arguments.
* **Idioma, Formato y UX (RNF-09):** 100% de textos, validaciones, roles legibles y errores en español claro; fechas mostradas como `dd/mm/aaaa hh:mm` en `America/Bogota`; diálogo de confirmación explícito antes de cerrar sesión y antes de crear un nuevo integrante de personal interno; indicadores de carga y prevención de doble envío mientras la petición HTTP está en curso.
* **Responsividad Multiplataforma:** Layout adaptable con Material 3 tanto para pantallas móviles Android (`WindowWidthSizeClass.Compact`) como navegadores Web de escritorio (`Medium` / `Expanded`).
* **Calidad y Testing:** Cero errores en `ktlintCheck` (`1.8.x`), pruebas unitarias de validadores/mappers/ViewModels/repositorios con `Ktor MockEngine`, pruebas de UI con `Compose UI Test` sobre árbol semántico y cobertura global $\ge 90\%$ con JetBrains Kover.
