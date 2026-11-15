# Contratos de APIs e Interfaces: 002-auth-users (Backend)
**Ubicación:** `specs/002-auth-users/contracts.md`  
**Spec Asociada:** `specs/002-auth-users/spec.md`  
**Protocolo:** REST / JSON (`application/json; charset=UTF-8`, zona `America/Bogota` `-05:00`, ISO-8601)  
**Estado:** Aprobado  

---

## 1. Contrato de Token JWT (Claims) y Política de Sesión
Todos los tokens emitidos por `POST /api/v1/auth/register` y `POST /api/v1/auth/login` utilizan firma `HMAC256` con `JWT_SECRET` y tienen una duración fija de **1 hora (`3600` segundos)**.

```json
{
  "iss": "itm-service-desk",
  "aud": "itm-service-desk-clients",
  "sub": "550e8400-e29b-41d4-a716-446655440000",
  "jti": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "role": "SOLICITANTE",
  "email": "laura.gomez@correo.itm.edu.co",
  "iat": 1794673800,
  "exp": 1794677400
}
```

* Cada llamada a `register` o `login` genera un **`jti` UUID único** y una nueva ventana `exp = iat + 3600`.
* Cabecera requerida en endpoints protegidos: `Authorization: Bearer <accessToken>`.
* Todo error (`400`, `401`, `403`, `409`, `500`, `503`) sigue el esquema transversal **`ApiErrorDto`** definido en `001-project-setup/contracts.md`.

---

## 2. Matriz de Autorización RBAC

| Endpoint | Público | `SOLICITANTE` | `PERSONAL_ATENCION` | `COORDINADOR` |
| :--- | :---: | :---: | :---: | :---: |
| `POST /api/v1/auth/register` | Sí | — | — | — |
| `POST /api/v1/auth/login` | Sí | — | — | — |
| `GET /api/v1/auth/me` | No (`401`) | Sí | Sí | Sí |
| `POST /api/v1/auth/logout` | No (`401`) | Sí | Sí | Sí |
| `POST /api/v1/users/staff` | No (`401`) | No (`403`) | No (`403`) | Sí |
| `GET /api/v1/users/staff` | No (`401`) | No (`403`) | No (`403`) | Sí |

---

## 3. Endpoints de Autenticación (`/api/v1/auth`)

### 3.1 `POST /api/v1/auth/register`
* **Descripción:** Registra un nuevo *Usuario solicitante* validando Tabla 1, hasheando la contraseña en servidor con BCrypt costo 12 e iniciando sesión automáticamente con un JWT de 1 hora.
* **Autenticación:** Pública (sujeta a perímetro CORS y `ALLOWED_CLIENT_IPS`).
* **Política de rol:** El DTO no admite campo `role` (`ignoreUnknownKeys = false`; cualquier campo extra o intento de inyectar `role` es rechazado con `400 Bad Request`). El servidor asigna siempre `SOLICITANTE`.

#### Request Body (`RegisterRequestDto`)
```json
{
  "fullName": "Laura Gómez Pérez",
  "documentNumber": "1020304050",
  "email": "Laura.Gomez@correo.itm.edu.co",
  "phone": "3001234567",
  "password": "ClaveSegura2026"
}
```

#### Response `201 Created` (`AuthResponseDto`)
```json
{
  "accessToken": "<jwt-hmac256>",
  "tokenType": "Bearer",
  "expiresInSeconds": 3600,
  "expiresAt": "2026-11-14T12:55:00-05:00",
  "user": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "fullName": "Laura Gómez Pérez",
    "documentNumber": "1020304050",
    "email": "laura.gomez@correo.itm.edu.co",
    "phone": "3001234567",
    "role": "SOLICITANTE",
    "createdAt": "2026-11-14T11:55:00-05:00"
  }
}
```

#### Response `400 Bad Request` (`ApiErrorDto` - Validación Tabla 1)
```json
{
  "timestamp": "2026-11-14T11:55:00-05:00",
  "status": 400,
  "error": "VALIDATION_ERROR",
  "message": "Los datos proporcionados contienen errores de validación.",
  "path": "/api/v1/auth/register",
  "details": [
    {
      "field": "documentNumber",
      "message": "El documento debe contener entre 5 y 15 dígitos numéricos."
    },
    {
      "field": "password",
      "message": "La contraseña debe tener al menos 8 caracteres, una letra y un número."
    }
  ]
}
```

#### Response `409 Conflict` (`ApiErrorDto` - Correo o Documento Duplicado)
```json
{
  "timestamp": "2026-11-14T11:55:00-05:00",
  "status": 409,
  "error": "CONFLICT",
  "message": "Ya existe una cuenta registrada con el correo electrónico o número de documento indicado.",
  "path": "/api/v1/auth/register",
  "details": []
}
```

---

### 3.2 `POST /api/v1/auth/login`
* **Descripción:** Autentica a cualquiera de los tres actores (`SOLICITANTE`, `PERSONAL_ATENCION`, `COORDINADOR`) y emite **siempre un nuevo JWT** con `jti` único y expiración a 1 hora (`3600s`).
* **Autenticación:** Pública.

#### Request Body (`LoginRequestDto`)
```json
{
  "email": "laura.gomez@correo.itm.edu.co",
  "password": "ClaveSegura2026"
}
```

#### Response `200 OK` (`AuthResponseDto`)
```json
{
  "accessToken": "<nuevo-jwt-hmac256>",
  "tokenType": "Bearer",
  "expiresInSeconds": 3600,
  "expiresAt": "2026-11-14T12:55:00-05:00",
  "user": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "fullName": "Laura Gómez Pérez",
    "documentNumber": "1020304050",
    "email": "laura.gomez@correo.itm.edu.co",
    "phone": "3001234567",
    "role": "SOLICITANTE",
    "createdAt": "2026-11-14T11:55:00-05:00"
  }
}
```

#### Response `400 Bad Request`
* Cuando faltan `email` o `password`, están en blanco o el JSON es inválido.

#### Response `401 Unauthorized` (`ApiErrorDto` - Credenciales Inválidas o Usuario Inactivo)
```json
{
  "timestamp": "2026-11-14T11:55:00-05:00",
  "status": 401,
  "error": "UNAUTHORIZED",
  "message": "Credenciales inválidas.",
  "path": "/api/v1/auth/login",
  "details": []
}
```

---

### 3.3 `GET /api/v1/auth/me`
* **Descripción:** Retorna el perfil del usuario autenticado actual consultando su registro vigente en `users`.
* **Autenticación:** JWT requerido (`SOLICITANTE` | `PERSONAL_ATENCION` | `COORDINADOR`).

#### Response `200 OK` (`UserProfileDto`)
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "fullName": "Laura Gómez Pérez",
  "documentNumber": "1020304050",
  "email": "laura.gomez@correo.itm.edu.co",
  "phone": "3001234567",
  "role": "SOLICITANTE",
  "createdAt": "2026-11-14T11:55:00-05:00"
}
```

#### Response `401 Unauthorized`
* Token ausente, malformado, expirado (`> 1h`), firma inválida, usuario inactivo o `jti` presente en `revoked_tokens`.

---

### 3.4 `POST /api/v1/auth/logout`
* **Descripción:** Revoca inmediatamente el JWT actual registrando `(jti, expiresAt)` en `revoked_tokens` y purga registros expirados (`expires_at < NOW()`).
* **Autenticación:** JWT requerido (`SOLICITANTE` | `PERSONAL_ATENCION` | `COORDINADOR`).
* **Request Body:** Ninguno.

#### Response `204 No Content`
* Sin cuerpo de respuesta.

#### Response `401 Unauthorized`
* Token ausente, expirado, firma inválida o ya revocado anteriormente (`jti` ya en `revoked_tokens`).

---

## 4. Endpoints de Personal Interno (`/api/v1/users/staff`)

### 4.1 `POST /api/v1/users/staff`
* **Descripción:** Crea un usuario de personal interno con rol `PERSONAL_ATENCION` o `COORDINADOR` (HU-07 / RF-07). No genera nuevo JWT ni altera la sesión del Coordinador que ejecuta la acción.
* **Autenticación y Autorización:** JWT requerido + Rol obligatorio **`COORDINADOR`**.

#### Request Body (`CreateStaffRequestDto`)
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

#### Response `201 Created` (`UserProfileDto`)
```json
{
  "id": "a1b2c3d4-e5f6-47a8-b9c0-112233445566",
  "fullName": "Carlos Andrés Mejía",
  "documentNumber": "71829304",
  "email": "carlos.mejia@itm.edu.co",
  "phone": "3119876543",
  "role": "PERSONAL_ATENCION",
  "createdAt": "2026-11-14T11:55:00-05:00"
}
```

#### Errores Específicos
* **`400 Bad Request`:** Errores de Tabla 1 o envío de `"role": "SOLICITANTE"` o valor de rol desconocido.
* **`401 Unauthorized`:** Token ausente, expirado o revocado.
* **`403 Forbidden`:** Token válido cuyo rol sea `SOLICITANTE` o `PERSONAL_ATENCION`.
* **`409 Conflict`:** `email` o `documentNumber` ya registrado en `users`.

---

### 4.2 `GET /api/v1/users/staff`
* **Descripción:** Lista de forma paginada el personal interno registrado (`PERSONAL_ATENCION` y `COORDINADOR`), excluyendo siempre usuarios `SOLICITANTE` y campos sensibles (HU-07 / RNF-07).
* **Autenticación y Autorización:** JWT requerido + Rol obligatorio **`COORDINADOR`**.
* **Query Parameters:**
  * `page`: `Int` opcional (por defecto `1`, mínimo `1`).
  * `size`: `Int` opcional (por defecto `20`, rango `1..20`).
  * `role`: `String` opcional (`PERSONAL_ATENCION` | `COORDINADOR`).

#### Response `200 OK` (`PagedStaffResponseDto`)
```json
{
  "items": [
    {
      "id": "a1b2c3d4-e5f6-47a8-b9c0-112233445566",
      "fullName": "Carlos Andrés Mejía",
      "email": "carlos.mejia@itm.edu.co",
      "role": "PERSONAL_ATENCION",
      "createdAt": "2026-11-14T11:55:00-05:00"
    }
  ],
  "page": 1,
  "size": 20,
  "totalItems": 1,
  "totalPages": 1
}
```
*(Si no hay resultados, retorna `"items": []`, `"totalItems": 0`, `"totalPages": 0`.)*

#### Errores Específicos
* **`400 Bad Request`:** `page < 1`, `size` fuera de `1..20`, parámetros no numéricos o `role=SOLICITANTE` / desconocido.
* **`401 Unauthorized`:** Token ausente, expirado o revocado.
* **`403 Forbidden`:** Token válido de `SOLICITANTE` o `PERSONAL_ATENCION`.

---

## 5. Catálogo de DTOs Kotlin (`@Serializable`)

| DTO | Paquete | Campos |
| :--- | :--- | :--- |
| `RegisterRequestDto` | `routing.dto.auth` | `fullName`, `documentNumber`, `email`, `phone`, `password` |
| `LoginRequestDto` | `routing.dto.auth` | `email`, `password` |
| `AuthResponseDto` | `routing.dto.auth` | `accessToken`, `tokenType`, `expiresInSeconds`, `expiresAt`, `user: UserProfileDto` |
| `UserProfileDto` | `routing.dto.users` | `id`, `fullName`, `documentNumber`, `email`, `phone`, `role`, `createdAt` |
| `CreateStaffRequestDto` | `routing.dto.users` | `fullName`, `documentNumber`, `email`, `phone`, `password`, `role` |
| `StaffSummaryDto` | `routing.dto.users` | `id`, `fullName`, `email`, `role`, `createdAt` |
| `PagedStaffResponseDto` | `routing.dto.users` | `items: List<StaffSummaryDto>`, `page`, `size`, `totalItems`, `totalPages` |
