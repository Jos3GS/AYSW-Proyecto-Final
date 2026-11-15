# Investigación Técnica y Spikes: 002-auth-users (Frontend)
**Ubicación:** `specs/002-auth-users/research.md`  
**Spec Asociada:** `specs/002-auth-users/spec.md`  
**Estado:** Concluido (Aprobado)  

---

## 1. Preguntas de Investigación y Objetivos del Spike
1. **Validación portable de Tabla 1 en Kotlin Multiplatform (`wasmJs` + Android):** ¿Cómo validar nombres Unicode (tildes/ñ), dígitos, correo y límite de 72 bytes UTF-8 de BCrypt en `commonMain` sin inconsistencias entre el motor Regex de JVM (Android) y ECMAScript (`wasmJs`)?
2. **Ciclo de sesión JWT (1 hora), arranque autenticado (`GET /api/v1/auth/me`) y prevención de bucles en `401`:** ¿Cómo coordinar `StartupScreen`, `SessionStorage`, `SessionEventBus` y `LoginScreen` para distinguir un `401` por credenciales inválidas en `/auth/login` de un `401` por token expirado/revocado en rutas protegidas o `/auth/logout`?
3. **Protección de rutas RBAC en Jetpack Navigation Compose Multiplatform:** ¿Cómo impedir el acceso de `SOLICITANTE` o `PERSONAL_ATENCION` a las pantallas administrativas de `COORDINADOR` (`StaffList`, `CreateStaff`) de manera declarativa y testable?
4. **Protección de contraseñas en memoria UI (`StateFlow`) y cumplimiento `RNF-04`:** ¿Cómo evitar que contraseñas digitadas aparezcan en logs, `toString()` de `UiState`, argumentos de navegación o estados persistidos del sistema operativo?
5. **Paginación (20/pág) y diálogos de confirmación (`RNF-09`) en Web + Android:** ¿Cómo diseñar la paginación de `StaffListScreen` y el componente de confirmación reutilizable garantizando responsividad Material 3 y pruebas deterministas con `Ktor MockEngine` + `Compose UI Test`?

---

## 2. Alternativas Evaluadas y Trade-offs

| Decisión / Problema | Alternativas Evaluadas | Ventajas | Desventajas / Riesgos | Alineación con Constitución | Decisión |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Validación Unicode en `commonMain` (`fullName`, `password`)** | **Opción A:** Regex Java `\p{L}` compartido en `commonMain` | Corto | En `wasmJs` el motor RegExp puede rechazar o comportarse distinto con `\p{L}` frente a JVM | Riesgo multiplataforma | Descartada |
| | **Opción B:** Funciones de `kotlin-stdlib` multiplataforma (`Char.isLetter()`, `Char.isDigit()`, `encodeToByteArray().size <= 72`) + regex ASCII simple para dígitos/email | 100% determinista e idéntico en Android y `wasmJs`, cero dependencias externas y testeable en `commonTest` | Requiere helper explícito `UserFormValidator` en `domain/validation` | Cumple 100% | **Seleccionada** |
| **Manejo de `401` en `/auth/login` y `/auth/logout`** | **Opción A:** Emitir `SessionEvent.SessionExpired` para todo `401` sin excepción | Regla uniforme | En `/auth/login` borraría errores y redirigiría en bucle; en `/auth/logout` confundiría cierre voluntario con expiración | Mala UX / bucle | Descartada |
| | **Opción B:** Excluir `/auth/login` y `/auth/register` del emisor `SessionExpired` (ya iniciado en `001-project-setup`), y en `/auth/logout` limpiar `SessionStorage` + emitir `LoggedOut` aun si el servidor devuelve `401` | Login muestra `"Credenciales inválidas."` limpiamente; logout siempre cierra sesión local aunque el JWT hubiera vencido hace segundos | Requiere manejar `401` en `AuthRepositoryImpl.logout()` como limpieza exitosa | Cumple 100% | **Seleccionada** |
| **Navegación Paginada de Personal (`GET /api/v1/users/staff`)** | **Opción A:** Scroll infinito automático | Fluido en listas móviles | Dificulta control exacto de página, pruebas con `MockEngine` y navegación en escritorio Web | Menor claridad operativa | Descartada |
| | **Opción B:** Paginación explícita de 20 en 20 con botones **Anterior / Siguiente** e indicador `Página X de Y` + chip de filtro por rol | Acopla 1:1 con query `page` y `size=20`, ideal tanto en Web desktop como en Android y 100% determinista en `Compose UI Test` | Requiere un clic para avanzar de página | Cumple RNF-07 / UX | **Seleccionada** |
| **Protección de Rutas por Rol (RBAC UI)** | **Opción A:** Solo ocultar botones en el menú | Rápido | Si el grafo navega por estado o restauración hacia `StaffList`, no habría guardia centralizada | Débil frente a cambios de estado | Descartada |
| | **Opción B:** `SessionViewModel` raíz + componente `RequireRole(allowedRoles)` en el grafo `AppNavHost` | Bloquea renderizado no autorizado, oculta menús ajenos y redirige o muestra pantalla controlada `403` sin cerrar sesión | Exige mantener `SessionState` sincronizado en el host raíz | Cumple RNF-03 / MVVM | **Seleccionada** |

---

## 3. Análisis Técnico y Hallazgos

* **Hallazgo 1 — Paridad Multiplataforma en Validación de Tabla 1 (`UserFormValidator`):**
  * En `commonMain`, validar `fullName` iterando caracteres con `trimmed.length in 3..100 && trimmed.all { it == ' ' || it.isLetter() }` garantiza idéntico soporte para tildes y `ñ` en Android (`JVM`) y Web (`wasmJs`) sin depender de flags Regex propietarios.
  * Para `password`, validar `password.length >= 8`, `password.encodeToByteArray().size <= 72` (límite exacto de BCrypt en Backend), `password.any { it.isLetter() }` y `password.any { it.isDigit() }` mantiene coherencia 1:1 con el servidor, permitiendo pintar errores por campo antes de enviar el formulario y combinarlos con los `fieldErrors` devueltos por `HTTP 400`.

* **Hallazgo 2 — Flujo de Arranque Autenticado tras `StartupScreen`:**
  * En `001-project-setup`, `StartupViewModel` verifica `GET /api/v1/health`. Una vez en `StartupUiState.Ready`, el coordinador de sesión (`SessionViewModel`) consulta `SessionStorage.getToken()`:
    - Si no hay token guardado → navega a `LoginRoute`.
    - Si existe token guardado → invoca `GET /api/v1/auth/me`: si responde `200 OK`, carga el `UserProfile` en `SessionState.Authenticated` y navega al Home correspondiente al rol; si responde `401 Unauthorized`, `HttpClientFactory` limpia `SessionStorage`, emite `SessionExpired` y lleva a `LoginRoute` mostrando el aviso `"Tu sesión expiró. Por favor, inicia sesión de nuevo."`.

* **Hallazgo 3 — Seguridad de Contraseñas en `UiState` y Sin Persistencia Local:**
  * Los estados de formulario (`LoginUiState`, `RegisterUiState`, `CreateStaffUiState`) sobreescribirán `toString()` ocultando el valor de `password` (`password=***`) y permanecerán únicamente en memoria dentro del `ViewModel` (nunca en `rememberSaveable`, `SessionStorage`, ni argumentos de ruta).
  * Al recibir respuesta exitosa (`200` en login o `201` en registro/alta de personal), el `ViewModel` limpia inmediatamente el campo `password` del estado.

* **Hallazgo 4 — Confirmaciones RNF-09 mediante Componente Material 3 Reutilizable:**
  * Se diseñará un componente compartido `ConfirmationDialog` en `ui/components/` con `testTag` semánticos para confirmar acciones críticas en `002-auth-users`:
    - **Cerrar sesión:** *"¿Deseas cerrar tu sesión actual?"* → dispara `POST /api/v1/auth/logout`, limpia `SessionStorage` (incluso ante `401`) y reinicia el backstack hacia `Login`.
    - **Registrar personal interno:** *"¿Confirmas el registro de <nombre> con rol <rol>?"* → previene altas accidentales de cuentas con privilegios de atención o coordinación.

* **Hallazgo 5 — Responsividad Web (`wasmJs`) y Android en Formularios y Tablas/Tarjetas:**
  * Las pantallas de autenticación (`LoginScreen`, `RegisterScreen`, `CreateStaffScreen`) centrarán tarjetas con ancho máximo acotado (`widthIn(max = 480.dp)`) para verse limpias en monitores Web y ocupar el ancho cómodo en Android.
  * `StaffListScreen` renderizará una vista adaptativa (tabla/fila compacta en escritorio `width >= 600.dp` y tarjetas Material 3 en móvil `< 600.dp`) usando `DateFormatter` (`America/Bogota`, `dd/mm/aaaa hh:mm`) y filtros por chip (`Todos`, `Personal de atención`, `Coordinador`).

---

## 4. Decisión Final Justificada
> Se adopta una validación compartida en `commonMain` basada en utilidades multiplataforma de `kotlin-stdlib` (`Char.isLetter()`, `encodeToByteArray().size <= 72`) alineada 1:1 con la Tabla 1 del Backend, combinada con enmascaramiento de contraseñas en `UiState.toString()`, limpieza inmediata tras envío exitoso y manejo diferenciado de `401` (credenciales inválidas en `/auth/login`, expiración automática en rutas protegidas y cierre seguro en `/auth/logout`).  
> La navegación protegida se orquesta desde `SessionViewModel` + guardia `RequireRole` en `AppNavHost`, integrando confirmación modal (`RNF-09`) en cierre de sesión y creación de personal, paginación explícita de 20 en 20 (`GET /api/v1/users/staff`), y pruebas automatizadas con `Ktor MockEngine` y `Compose UI Test` manteniendo la cobertura global $\ge 90\%$ con JetBrains Kover.
