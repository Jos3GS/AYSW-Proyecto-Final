# Feature Spec: 002-auth-users (Registro, Autenticación JWT, RBAC y Personal Interno)
**Ubicación:** `specs/002-auth-users/spec.md`  
**Versión de Spec:** 1.0.0  
**Versión Destino:** `v0.1.0`  
**Estado:** Aprobada  

---

## 1. Resumen y Justificación
* **Problema:** Los tres actores del sistema (*Usuario solicitante*, *Personal de atención* y *Coordinador*) necesitan autenticarse de forma segura y operar bajo permisos estrictos según su rol. Además, los solicitantes deben poder auto-registrarse quedando autenticados de inmediato, mientras que las cuentas de personal interno (*Personal de atención* y nuevos *Coordinadores*) solo pueden ser creadas y consultadas por la Coordinación (RF-01, RF-07).
* **Solución propuesta:** Implementar la capa de usuarios, registro público de solicitantes con inicio de sesión automático (`POST /api/v1/auth/register` devolviendo JWT), inicio de sesión generando siempre un nuevo JWT de **1 hora** (`POST /api/v1/auth/login`), consulta del usuario autenticado (`GET /api/v1/auth/me`), cierre de sesión con **blacklist por `jti` en PostgreSQL y purga automática de tokens expirados** (`POST /api/v1/auth/logout`), y módulo exclusivo de Coordinación para crear (`POST /api/v1/users/staff`) y listar (`GET /api/v1/users/staff`, paginado 20/pág) personal interno, con validaciones estrictas de la Tabla 1, hashing **BCrypt** en servidor (`at.favre.lib:bcrypt` 0.10.2, costo 12) y control RBAC en cada ruta.
* **Fuera de alcance (Out of Scope):**
  - Recuperación de contraseña por correo o notificaciones externas (prohibido por RNF-10).
  - Gestión de solicitudes de servicio (`003-service-requests`, `004-attention-lifecycle`).
  - Edición/eliminación de cuentas de personal (no contemplado en HU-07 / RF-07).

---

## 2. Historias de Usuario
* **HU-01 (Parte Registro / Sesión):** Como usuario solicitante, quiero registrarme en el sistema quedando autenticado automáticamente e iniciar/cerrar sesión de forma segura, para presentar solicitudes de servicio y consultar su estado con trazabilidad.
* **HU-07 (Registro y Consulta de Personal Interno):** Como coordinador, quiero registrar nuevos usuarios con rol *Personal de atención* o *Coordinador* y visualizar el listado del personal interno registrado, para controlar la disponibilidad del equipo de atención.
* **US-Auth-01 (Autenticación Transversal y RBAC):** Como los tres actores (*Usuario solicitante*, *Personal de atención*, *Coordinador*), quiero autenticarme con correo y contraseña obteniendo un nuevo JWT válido por 1 hora y que el servidor valide mi rol en cada endpoint, para acceder únicamente a las operaciones autorizadas.

---

## 3. Criterios de Aceptación (Escenarios BDD / Gherkin)

### Escenario 1: Registro público exitoso con inicio de sesión automático (HU-01 / RF-01)
* **Given (Dado que):** No existen usuarios con el correo ni el número de documento enviados.
* **When (Cuando):** Se envía `POST /api/v1/auth/register` con `fullName` (3..100 letras/espacios), `documentNumber` (5..15 dígitos), `email` (formato válido, $\le 100$ chars), `phone` (7..15 dígitos) y `password` ($\ge 8$ chars, al menos 1 letra y 1 número).
* **Then (Entonces):**
  1. El servidor asigna obligatoriamente el rol `SOLICITANTE` (ignorando o rechazando cualquier intento de enviar otro rol).
  2. La contraseña se hashea con BCrypt costo 12 y salt aleatorio en servidor; nunca se almacena en texto plano ni se devuelve ni se loguea.
  3. El servidor persiste el usuario, genera un token JWT válido por **1 hora** (`3600s`) con `jti` único y responde `HTTP 201 Created` con el token y los datos públicos del usuario.

### Escenario 2: Registro público rechazado por duplicado o datos inválidos (HU-01 / RNF-06)
* **Given (Dado que):** Ya existe un usuario registrado con ese correo electrónico o número de documento, o el payload incumple las reglas de la Tabla 1.
* **When (Cuando):** Se envía `POST /api/v1/auth/register`.
* **Then (Entonces):**
  1. Si el correo o documento ya existen, la cuenta no se crea y el servidor responde `HTTP 409 Conflict`.
  2. Si algún campo es inválido o faltante, responde `HTTP 400 Bad Request` con `details[]` indicando el error específico de cada campo.

### Escenario 3: Inicio de sesión válido y emisión de nuevo JWT de 1 hora (RF-01 / RNF-02)
* **Given (Dado que):** Existe un usuario activo (Solicitante, Personal de atención o Coordinador) registrado en la base de datos (incluso si ya inició sesión hace menos de 1 hora).
* **When (Cuando):** Se envía `POST /api/v1/auth/login` con correo y contraseña correctos.
* **Then (Entonces):**
  1. El servidor verifica el hash BCrypt, genera **siempre un nuevo token JWT** con nuevo `jti` y expiración (`exp`) exacta de **1 hora** (`3600` segundos) desde ese login, y responde `HTTP 200 OK` con el token y los datos del usuario (`id`, `fullName`, `email`, `role`).
  2. Las peticiones posteriores a rutas protegidas sin token o con token expirado responden `HTTP 401 Unauthorized`.

### Escenario 4: Credenciales inválidas en login (RF-01)
* **Given (Dado que):** El correo no existe o la contraseña no coincide con el hash BCrypt.
* **When (Cuando):** Se envía `POST /api/v1/auth/login`.
* **Then (Entonces):** El servidor responde `HTTP 401 Unauthorized` con un mensaje genérico (`"Credenciales inválidas."`), sin revelar si el correo está registrado.

### Escenario 5: Cierre de sesión con blacklist por `jti` y purga de expirados
* **Given (Dado que):** El usuario posee un JWT activo válido.
* **When (Cuando):** Envía `POST /api/v1/auth/logout` con su token en `Authorization: Bearer <token>` y posteriormente intenta reutilizar ese mismo JWT en `GET /api/v1/auth/me` u otro endpoint protegido.
* **Then (Entonces):**
  1. `POST /api/v1/auth/logout` registra el `jti` y `expiresAt` en la tabla `revoked_tokens`, purga registros expirados (`expires_at < NOW()`) y responde `HTTP 204 No Content`.
  2. Cualquier petición posterior con ese mismo JWT revocado es rechazada con `HTTP 401 Unauthorized`, mientras que un nuevo login posterior del mismo usuario emite otro `jti` totalmente válido.

### Escenario 6: Creación de personal interno por Coordinador (HU-07 / RF-07)
* **Given (Dado que):** Existe una sesión autenticada con rol `COORDINADOR`.
* **When (Cuando):** Envía `POST /api/v1/users/staff` con datos válidos de Tabla 1 y `role` igual a `PERSONAL_ATENCION` o `COORDINADOR`.
* **Then (Entonces):**
  1. El servidor valida unicidad de `documentNumber` y `email` (`409` si hay conflicto) y reglas Tabla 1 (`400` si hay errores).
  2. Guarda al empleado con contraseña hasheada en BCrypt costo 12 y responde `HTTP 201 Created`; el nuevo empleado puede iniciar sesión inmediatamente.

### Escenario 7: Restricciones de rol y seguridad en endpoints de personal (HU-07 / RNF-03)
* **Given (Dado que):** Un usuario sin sesión, o autenticado con rol `SOLICITANTE` o `PERSONAL_ATENCION`, o un `COORDINADOR` que envía un rol inválido (`SOLICITANTE`) en `POST /api/v1/users/staff`.
* **When (Cuando):** Intenta acceder a `POST /api/v1/users/staff` o `GET /api/v1/users/staff`.
* **Then (Entonces):**
  1. Sin sesión activa → `HTTP 401 Unauthorized`.
  2. Con rol `SOLICITANTE` o `PERSONAL_ATENCION` → `HTTP 403 Forbidden`.
  3. Un `COORDINADOR` enviando `role = "SOLICITANTE"` o valor desconocido en `/users/staff` → `HTTP 400 Bad Request`.

### Escenario 8: Listado paginado de personal interno (HU-07 / RNF-07)
* **Given (Dado que):** El usuario autenticado tiene rol `COORDINADOR`.
* **When (Cuando):** Consulta `GET /api/v1/users/staff?page=1&size=20` (opcionalmente filtrando por `role`).
* **Then (Entonces):** Devuelve `HTTP 200 OK` con máximo 20 registros por página incluyendo `id`, `fullName`, `email`, `role` y `createdAt` (excluyendo usuarios `SOLICITANTE` y nunca devolviendo `passwordHash`).

---

## 4. Contratos de Datos e Interfaces

### Entradas y Salidas Clave
* `POST /api/v1/auth/register` (Público):
  * Input: `{ fullName, documentNumber, email, phone, password }`
  * Output (`201 Created`): `{ accessToken, tokenType: "Bearer", expiresInSeconds: 3600, expiresAt, user: { id, fullName, documentNumber, email, phone, role: "SOLICITANTE", createdAt } }`
* `POST /api/v1/auth/login` (Público):
  * Input: `{ email, password }`
  * Output (`200 OK`): `{ accessToken, tokenType: "Bearer", expiresInSeconds: 3600, expiresAt, user: { id, fullName, documentNumber, email, phone, role, createdAt } }`
* `GET /api/v1/auth/me` (JWT requerido — cualquier rol):
  * Output (`200 OK`): `{ id, fullName, documentNumber, email, phone, role, createdAt }`
* `POST /api/v1/auth/logout` (JWT requerido — cualquier rol):
  * Output (`204 No Content`) — registra `jti` en blacklist PostgreSQL hasta su `expiresAt`.
* `POST /api/v1/users/staff` (JWT requerido — solo `COORDINADOR`):
  * Input: `{ fullName, documentNumber, email, phone, password, role }` (`PERSONAL_ATENCION` | `COORDINADOR`)
  * Output (`201 Created`): `{ id, fullName, documentNumber, email, phone, role, createdAt }`
* `GET /api/v1/users/staff` (JWT requerido — solo `COORDINADOR`):
  * Query params: `page` (default `1`), `size` (default/máx `20`), `role` (opcional: `PERSONAL_ATENCION` | `COORDINADOR`)
  * Output (`200 OK`): `{ items: [{ id, fullName, email, role, createdAt }], page, size, totalItems, totalPages }`

---

## 5. Requisitos No Funcionales (NFRs)
* **Seguridad de Autenticación (RNF-02 ajustado por política de seguridad):** BCrypt (`at.favre.lib:bcrypt` 0.10.2, costo 12) con salt aleatorio en servidor; JWT firmado (`HMAC256` con `JWT_SECRET`) con expiración exacta de **1 hora (`3600` segundos)** y `jti` único por emisión; blacklist de `jti` revocados en PostgreSQL con purga automática de registros expirados (`expires_at < NOW()`); prohibido devolver o loguear contraseñas en texto plano o hashes.
* **Autorización RBAC (RNF-03):** Validación de rol en servidor en cada endpoint protegido; respuestas `401` y `403` consistentes con `ApiErrorDto`.
* **Validación e Integridad (RNF-05 / RNF-06):** Todas las reglas de Tabla 1 validadas en servidor; `email` normalizado a minúsculas; unicidad garantizada por constraints PostgreSQL y mapeo `409 Conflict`.
* **Rendimiento y Paginación (RNF-07):** Listados paginados de 20 en 20 (`size = 20`) y tiempos `< 2 segundos`.
