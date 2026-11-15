---
proyecto: ITM Service Desk
subproyecto: Frontend
feature: 002-auth-users
categoria: Spec_Kit / Guia_IA
fecha: 2026-11-14
version: 1.0.0
estado: Aprobado
tags:
  - spec-kit
  - frontend
  - guia-ia
  - prompt-maestro
  - auth
  - navigation
---

# Guía de Implementación Asistida por IA — 002-auth-users (Frontend)

> **Estado:** Aprobado

---

## Prompt Maestro para la Sesión de Implementación

```text
Actúa como un Ingeniero Senior especialista en Kotlin Multiplatform (KMP), Compose Multiplatform (Android + WasmJs) y Arquitectura MVVM + UDF.
Tu misión es implementar EXCLUSIVAMENTE la épica `002-auth-users` del Frontend de **ITM Service Desk**, sobre la base verificada de `001-project-setup`.

VERIFICACIÓN DE CONTINUIDAD ANTES DE INICIAR:
Antes de escribir código nuevo, confirma que:
1. `001-project-setup` compila y pasa `./gradlew ktlintCheck commonTest koverVerify`.
2. El Backend local (si se realizan pruebas de integración manual) o los contratos `contracts.md` de `002-auth-users` son compatibles (JWT TTL = 1h / `3600s` + expiración automática manejada por `401 Unauthorized`).

DOCUMENTOS DE LECTURA OBLIGATORIA:
1. `frontend/.specify/memory/constitution.md`
2. `frontend/specs/002-auth-users/spec.md`
3. `frontend/specs/002-auth-users/research.md`
4. `frontend/specs/002-auth-users/data-model.md`
5. `frontend/specs/002-auth-users/contracts.md`
6. `frontend/specs/002-auth-users/plan.md`
7. `frontend/specs/002-auth-users/checklist.md`
8. `frontend/specs/002-auth-users/tasks.md`

REGLAS INNEGOCIABLES DE IMPLEMENTACIÓN:
- Validación de formularios en `UserFormValidator` (`domain/validation/`):
  - Usar `Char.isLetter()` / `Char.isDigit()` para soportar acentos y Ñ (prohibido usar regex ASCII `[A-Za-z]`).
  - Validar la contraseña de entrada no solo por longitud mínima (`>= 8`), sino comprobando estrictamente `password.encodeToByteArray().size <= 72` (límite de BCrypt en UTF-8).
- Seguridad de registro público: `RegisterRequestDto` NO debe incluir el campo `role` bajo ninguna circunstancia (el backend asigna `SOLICITANTE`).
- Manejo de errores en `/auth/login` y `/auth/register`: EXCLUIR estas dos rutas de la emisión automática del evento global `SessionExpired` ante HTTP `401` (deben mostrar error de credenciales o cuenta en el formulario sin disparar redirección recursiva).
- Protección de secretos: sobrescribir `toString()` en DTOs/modelos que contengan `password`, `accessToken` o `token` para enmascarar los valores (`***`).
- Cierre de sesión resiliente (`logout`): si `POST /api/v1/auth/logout` devuelve `204 No Content` O `401 Unauthorized`, limpiar siempre la sesión almacenada en `SessionStorage` y emitir navegación limpia a `LoginRoute`.
- Control de acceso en UI y rutas tipadas (`Routes.kt`, `AppNavHost.kt`):
  - Envolver las pantallas protegidas con `RequireRole(...)` y verificar permisos en `SessionViewModel`.
  - La gestión de staff (`StaffListScreen`, `CreateStaffScreen`) es exclusiva de `COORDINADOR`, con paginación fija `size = 20` y `ConfirmationDialog` para activar/desactivar usuarios (`PATCH /api/v1/users/{id}/status`).
- Alcance estricto: implementa únicamente `T01..T07` de `002-auth-users`. No implementes solicitudes ni atención aún.

METODOLOGÍA DE TRABAJO:
- Trabaja de forma secuencial siguiendo `T01` a `T07` de `tasks.md`.
- Al terminar, ejecuta obligatoriamente:
  `./gradlew ktlintCheck commonTest koverVerify compileKotlinWasmJs assembleDebug`
```

---

## Registro de Correcciones Críticas Frente a Desviaciones Comunes de la IA

| Desviación habitual de la IA | Por qué está mal según la Constitución / Spec | Corrección exigida a la IA |
|---|---|---|
| Validar nombres/contraseñas con `[a-zA-Z]` o `.length <= 72` | Rechaza nombres con tildes/`ñ` en español y permite contraseñas multibyte mayores a 72 bytes UTF-8 que truncan o fallan en BCrypt | Usar `Char.isLetter()` / `Char.isDigit()` y verificar `password.encodeToByteArray().size <= 72` en `UserFormValidator`. |
| Incluir `role = "SOLICITANTE"` dentro de `RegisterRequestDto` | Viola el contrato estricto del backend y expone campos no permitidos en registro público | Omitir por completo `role` en `RegisterRequestDto`. |
| Interceptar todo HTTP `401` para emitir `SessionExpired` sin filtrar rutas | Convierte un fallo de contraseña incorrecta en `/auth/login` en un loop de `"sesión expirada"` | Excluir `/api/v1/auth/login` y `/api/v1/auth/register` del disparador de `SessionExpired`. |
| Dejar el token vivo en `SessionStorage` cuando `POST /auth/logout` devuelve `401` | Si el JWT acaba de vencer (`1h`), el usuario queda atrapado en sesión local sin poder salir | Limpiar `SessionStorage` y volver a `LoginRoute` tanto en `204` como en `401`. |
| Implementar `StaffListScreen` sin `ConfirmationDialog` o cambiando `size = 10` | Incumple el contrato de paginación uniforme (`size = 20`) y el requisito de confirmación en acciones críticas de estado | Usar siempre `size = 20` y abrir el `ConfirmationDialog` reutilizable antes de activar o desactivar un usuario de staff. |
| Usar `toString()` por defecto de Kotlin `data class` en `LoginRequestDto`, `RegisterRequestDto` o `AuthResponseDto` | Expone contraseñas y tokens JWT en logs o crash reports | Sobrescribir `toString()` ocultando campos sensibles con `"***"`. |
