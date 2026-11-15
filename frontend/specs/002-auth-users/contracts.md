# Contratos de APIs e Interfaces: 002-auth-users (Frontend)
**Ubicación:** `specs/002-auth-users/contracts.md`  
**Spec Asociada:** `specs/002-auth-users/spec.md`  
**Protocolo:** REST / JSON vía Ktor Client 3.6.x (`kotlinx.serialization.json`, `API_BASE_URL` vía `BuildKonfig`)  
**Estado:** Aprobado  

---

## 1. Endpoints Backend Consumidos

### 1.1 `POST {API_BASE_URL}/api/v1/auth/register`
* **Autenticación:** Pública (exenta de cabecera `Authorization`).
* **Request (`RegisterRequestDto`):**
```json
{
  "fullName": "Laura Gómez Pérez",
  "documentNumber": "1020304050",
  "email": "laura.gomez@correo.itm.edu.co",
  "phone": "3001234567",
  "password": "ClaveSegura2026"
}
```
* **Response `201 Created` (`AuthResponseDto` → `AuthSession` vía `AuthMapper`):**
```json
{
  "accessToken": "<jwt-hmac256>",
  "tokenType": "Bearer",
  "expiresInSeconds": 3600,
  "expiresAt": "2026-11-14T18:15:00-05:00",
  "user": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "fullName": "Laura Gómez Pérez",
    "documentNumber": "1020304050",
    "email": "laura.gomez@correo.itm.edu.co",
    "phone": "3001234567",
    "role": "SOLICITANTE",
    "createdAt": "2026-11-14T17:15:00-05:00"
  }
}
```
* **Efecto en Cliente:** `AuthRepositoryImpl` persiste `accessToken` en `SessionStorage.saveToken(...)`; `RegisterViewModel` limpia `password` de `RegisterUiState` y `SessionViewModel` autentica y navega a `ApplicantHomeRoute`.
* **Errores mapeados (`ErrorMapper`):** `400 Bad Request` → pinta `fieldErrors` en cada campo; `409 Conflict` → mensaje amigable de correo/documento ya registrado; `500`/`503`/Red → mensaje seguro controlado.

---

### 1.2 `POST {API_BASE_URL}/api/v1/auth/login`
* **Autenticación:** Pública (exenta de cabecera `Authorization` y excluida del interceptor automático de `SessionExpired`).
* **Request (`LoginRequestDto`):**
```json
{
  "email": "laura.gomez@correo.itm.edu.co",
  "password": "ClaveSegura2026"
}
```
* **Response `200 OK` (`AuthResponseDto` → `AuthSession`):**
  * Guarda siempre el nuevo JWT (`expiresInSeconds: 3600`) en `SessionStorage`, limpia `password` del estado y navega al Home del rol correspondiente.
* **Response `401 Unauthorized` (`ApiErrorDto`):**
  * No emite `SessionExpired`; `LoginViewModel` muestra `"Credenciales inválidas."` en `generalError`.

---

### 1.3 `GET {API_BASE_URL}/api/v1/auth/me`
* **Autenticación:** Requerida (`Authorization: Bearer <token>` inyectada por `HttpClientFactory`).
* **Response `200 OK` (`UserProfileDto` → `UserProfile`):**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "fullName": "Laura Gómez Pérez",
  "documentNumber": "1020304050",
  "email": "laura.gomez@correo.itm.edu.co",
  "phone": "3001234567",
  "role": "SOLICITANTE",
  "createdAt": "2026-11-14T17:15:00-05:00"
}
```
* **Response `401 Unauthorized`:** `HttpClientFactory` limpia `SessionStorage`, emite `SessionEvent.SessionExpired` y redirige a `LoginRoute` con aviso de sesión expirada.

---

### 1.4 `POST {API_BASE_URL}/api/v1/auth/logout`
* **Autenticación:** Requerida (`Authorization: Bearer <token>`).
* **Request Body:** Vacío.
* **Response `204 No Content` (o `401` si el JWT expiró antes de confirmar):**
  * `AuthRepositoryImpl.logout()` invoca `SessionStorage.clearToken()` y emite `SessionEvent.LoggedOut`, limpiando la sesión en memoria y reiniciando el stack de navegación a `LoginRoute`.

---

### 1.5 `POST {API_BASE_URL}/api/v1/users/staff`
* **Autenticación y Rol:** JWT requerido con rol **`COORDINADOR`**.
* **Request (`CreateStaffRequestDto`):**
```json
{
  "fullName": "Carlos Andrés Mejía",
  "documentNumber": "71829304",
  "email": "carlos.mejia@itm.edu.co",
  "phone": "3119876543",
  "password": "StaffPassword2026",
  "role": "PERSONAL_ATENCION"
}
```
* **Response `201 Created` (`UserProfileDto` → `UserProfile`):**
  * Limpia `password` del `CreateStaffUiState`, mantiene intacta la sesión del Coordinador y notifica éxito / refresca `StaffList`.
* **Errores mapeados:**
  * `400 Bad Request` → errores por campo (`fieldErrors`).
  * `401 Unauthorized` → `SessionExpired` → `LoginRoute`.
  * `403 Forbidden` → mensaje controlado de permisos insuficientes sin cerrar sesión.
  * `409 Conflict` → mensaje de correo o documento ya registrado sin limpiar el formulario.

---

### 1.6 `GET {API_BASE_URL}/api/v1/users/staff`
* **Autenticación y Rol:** JWT requerido con rol **`COORDINADOR`**.
* **Query Parameters:** `page` (entero $\ge 1$, default `1`), `size` (`20`), `role` (opcional: `PERSONAL_ATENCION` | `COORDINADOR`).
* **Response `200 OK` (`PagedStaffResponseDto` → `StaffPage` → `List<StaffItemUiModel>`):**
```json
{
  "items": [
    {
      "id": "a1b2c3d4-e5f6-47a8-b9c0-112233445566",
      "fullName": "Carlos Andrés Mejía",
      "email": "carlos.mejia@itm.edu.co",
      "role": "PERSONAL_ATENCION",
      "createdAt": "2026-11-14T17:15:00-05:00"
    }
  ],
  "page": 1,
  "size": 20,
  "totalItems": 1,
  "totalPages": 1
}
```
* **Transformación (`StaffUiMapper`):**
  * `PERSONAL_ATENCION` → `"Personal de atención"`, `COORDINADOR` → `"Coordinador"`.
  * `createdAt` → `dd/mm/aaaa hh:mm` en `America/Bogota` mediante `DateFormatter`.

---

## 2. Contrato de Navegación Tipada y Guardias RBAC (`AppNavHost`)

| Ruta (`@Serializable`) | Acceso Permitido | Comportamiento si No Cumple |
| :--- | :--- | :--- |
| `StartupRoute` | Público | Verifica `/health` y luego sesión existente (`/auth/me`) |
| `LoginRoute` | No autenticado | Si ya está autenticado redirige a Home según rol |
| `RegisterRoute` | No autenticado | Si ya está autenticado redirige a `ApplicantHomeRoute` |
| `ApplicantHomeRoute` | `SOLICITANTE` | Sin sesión → `LoginRoute`; otro rol → su Home |
| `AgentHomeRoute` | `PERSONAL_ATENCION` | Sin sesión → `LoginRoute`; otro rol → su Home |
| `CoordinatorHomeRoute` | `COORDINADOR` | Sin sesión → `LoginRoute`; otro rol → su Home |
| `StaffListRoute` | `COORDINADOR` | Sin sesión → `LoginRoute`; otro rol → bloqueo RBAC / Home |
| `CreateStaffRoute` | `COORDINADOR` | Sin sesión → `LoginRoute`; otro rol → bloqueo RBAC / Home |

---

## 3. Contrato de Componentes UI y Confirmaciones (`RNF-09` & `Compose UI Test`)

### Diálogos de Confirmación Obligatorios
1. **Cierre de Sesión (`ConfirmActionDialog`):**
   * Título: `"Cerrar sesión"` | Mensaje: `"¿Deseas cerrar tu sesión actual?"` | Acciones: `"Cancelar"` / `"Cerrar sesión"`.
2. **Registro de Personal Interno (`ConfirmActionDialog`):**
   * Título: `"Confirmar registro de personal"` | Mensaje: `"¿Confirmas el registro de <fullName> con rol <roleLabel>?"` | Acciones: `"Cancelar"` / `"Confirmar"`.

### Etiquetas Semánticas Estables (`Modifier.testTag`)
* **Login:** `login_email_input`, `login_password_input`, `login_submit_button`, `login_go_register_button`, `login_error_banner`, `login_notice_banner`.
* **Registro:** `register_fullname_input`, `register_document_input`, `register_email_input`, `register_phone_input`, `register_password_input`, `register_submit_button`, `register_go_login_button`.
* **Sesión / Logout:** `top_bar_logout_button`, `confirm_dialog_accept_button`, `confirm_dialog_cancel_button`.
* **Personal Interno:** `staff_filter_all`, `staff_filter_agent`, `staff_filter_coordinator`, `staff_list_container`, `staff_prev_page_button`, `staff_next_page_button`, `staff_new_button`, `create_staff_role_selector`, `create_staff_submit_button`.
