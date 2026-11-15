# Checklist de Calidad y Puertas de Aprobación (Quality Gates): 002-auth-users (Frontend)
**Ubicación:** `specs/002-auth-users/checklist.md`  
**Spec Asociada:** `specs/002-auth-users/spec.md`  
**Estado:** Aprobado  

---

## 1. Validación contra la Constitución
- [x] El diseño técnico respeta estrictamente el stack autorizado: **Kotlin Multiplatform 2.4.x**, **Compose Multiplatform 1.12.2 (Material 3)**, **Web `wasmJs`**, **Android (`minSdk 26`, `targetSdk`/`compileSdk` 35/36)**, **Ktor Client 3.6.x**, **`kotlinx.serialization.json`**, **`kotlinx-datetime`**, **Jetpack Navigation Compose Multiplatform**, **Koin Multiplatform**, **`ktlint 1.8.x`** y **JetBrains Kover**.
- [x] No se incorporan librerías externas no aprobadas para validación de formularios o gestión de estado (`UserFormValidator` usa utilidades estándar portables de `kotlin-stdlib` en `commonMain`).
- [x] Se cumple estrictamente **MVVM + UDF** (`StateFlow`), **Repository Pattern** (`AuthRepository`, `StaffRepository`) y **Mapper Pattern** (`AuthMapper`, `StaffMapper`, `StaffUiMapper`, `ErrorMapper`), aislando los DTOs `@Serializable` en la capa `data`.
- [x] Se mantienen las reglas de `org.jlleitschuh.gradle.ktlint` (cero importaciones `*`, 4 espacios, trailing commas, máx. 120 caracteres) y cobertura global $\ge 90\%$ con **JetBrains Kover**.

## 2. Completitud de la Especificación, Seguridad y UX
- [x] Todos los escenarios BDD (`Escenario 1` a `Escenario 7`) de `spec.md` cubren HU-01 (registro/auto-login/cierre de sesión), US-Auth-FE-01 (login multirrol, JWT 1h, restauración `/auth/me`, `401`) y HU-07 (listado paginado y creación de personal para `COORDINADOR`).
- [x] Los contratos REST consumidos (`/api/v1/auth/{register,login,me,logout}` y `/api/v1/users/staff`) están alineados 1:1 con el Backend, incluyendo `RegisterRequestDto` sin campo `role`, `expiresInSeconds = 3600` y mapeo de `details[]` (`400`) y `409 Conflict`.
- [x] La seguridad en cliente protege sesiones y credenciales: JWT guardado únicamente en `SessionStorage` (`sessionStorage`+memoria en `wasmJs`, prohibido `localStorage`; `EncryptedSharedPreferences` en Android); contraseñas ocultas con `PasswordVisualTransformation`, enmascaradas en `UiState.toString()`, excluidas de `rememberSaveable`/rutas y limpiadas tras el envío exitoso.
- [x] Se evitan bucles de `401`: `/auth/login` muestra `"Credenciales inválidas."` sin disparar `SessionExpired`; `/auth/logout` limpia sesión y backstack aunque reciba `401`; y rutas protegidas limpian el token redirigiendo a `LoginRoute`.
- [x] Se garantiza la UX y RBAC en español (`RNF-03`, `RNF-07`, `RNF-09`): guardia `RequireRole` para rutas de `COORDINADOR`, paginación 20/pág con filtros por rol interno, fechas en formato `dd/mm/aaaa hh:mm` (`America/Bogota`), confirmaciones modales antes de cerrar sesión y antes de crear personal, y etiquetas `Modifier.testTag` estables para `Compose UI Test`.
- [x] No quedan puntos pendientes marcados con `[NECESITA ACLARACIÓN]`.

## 3. Preparación para Implementación (`tasks.md`)
- [ ] El desglose secuencial en `specs/002-auth-users/tasks.md` está redactado y aprobado en orden TDD.
- [ ] Cada tarea de `tasks.md` define su criterio de verificación automatizada (`ktlintCheck`, `Ktor MockEngine`, `kotlinx-coroutines-test`, `Compose UI Test`, `koverVerify`).
- [ ] Aprobación explícita final para iniciar la implementación de `002-auth-users` (Frontend) *(una vez completada la planificación del resto de épicas o autorizada la fase de código)*.
