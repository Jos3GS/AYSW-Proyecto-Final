# Checklist de Calidad y Puertas de Aprobación (Quality Gates): 003-service-requests (Frontend)
**Ubicación:** `specs/003-service-requests/checklist.md`  
**Spec Asociada:** `specs/003-service-requests/spec.md`  
**Estado:** Completado  

---

## 1. Validación contra la Constitución
- [x] El diseño técnico respeta estrictamente el stack autorizado: **Kotlin Multiplatform 2.4.x**, **Compose Multiplatform 1.12.2 (Material 3)**, **Web `wasmJs`**, **Android (`minSdk 26`, `targetSdk`/`compileSdk` 35/36)**, **Ktor Client 3.6.x**, **`kotlinx.serialization.json`**, **`kotlinx-datetime`**, **Jetpack Navigation Compose Multiplatform**, **Koin Multiplatform**, **`ktlint 1.8.x`** y **JetBrains Kover**.
- [x] No se incorporan librerías externas no aprobadas para validación ni manejo de formularios (`RequestFormValidator` emplea utilidades estándar portables de `kotlin-stdlib` en `commonMain`).
- [x] Se cumple estrictamente **MVVM + UDF** (`StateFlow`), **Repository Pattern** (`ServiceRequestRepository`) y **Mapper Pattern** (`RequestMapper`, `RequestUiMapper`, `ErrorMapper`), aislando los DTOs `@Serializable` en `data`.
- [x] Se mantienen las reglas de `org.jlleitschuh.gradle.ktlint` (cero importaciones `*`, 4 espacios, trailing commas, máx. 120 caracteres) y cobertura global $\ge 90\%$ con **JetBrains Kover**.

## 2. Completitud de la Especificación, Contratos y UX
- [x] Todos los escenarios BDD (`Escenario 1` a `Escenario 6`) de `spec.md` cubren **HU-01** (radicación Tabla 2 + solicitud previa opcional), **HU-02** (bandeja paginada, detalle, historial y cancelación desde `ABIERTA`) y **HU-05** (consulta de respuesta oficial y creación de nueva solicitud relacionada sin alterar la previa).
- [x] Los contratos REST consumidos (`POST /api/v1/requests`, `GET /api/v1/requests/my`, `GET /api/v1/requests/{code}`, `POST /api/v1/requests/{code}/cancel`) están alineados 1:1 con el Backend, incluyendo `CreateServiceRequestDto` sin campos automáticos del servidor y manejo de `400`, `401`, `403`, `404` y `409`.
- [x] Las validaciones en cliente (`RequestFormValidator`) tienen paridad 1:1 con **Tabla 2** (`category` obligatoria, `subject` `5..100`, `description` `20..2000`, `previousRequestCode` opcional con patrón `^SOL-\d{4}-\d{5,}$`, `cancelReason <= 500`) e inyectan errores de negocio del servidor (`400`) directamente en `fieldErrors`.
- [x] Se cumplen íntegramente las exigencias de UX, privacidad e idioma (**RNF-03**, **RNF-07**, **RNF-09**, **RNF-10**, **RF-03**):
  - 100% de categorías, estados y acciones de historial traducidos al español (`RequestUiMapper`) y fechas formateadas `dd/mm/aaaa hh:mm` (`America/Bogota`).
  - Paginación explícita de `20` ítems/página con filtro por estado y botones **Anterior / Siguiente**.
  - Botón **"Cancelar solicitud"** visible únicamente en `ABIERTA`, confirmación modal obligatoria (`CancelRequestDialog`) con prevención anti-doble envío y recarga automática ante `409 Conflict`.
  - Protección de rutas bajo `RequireRole(UserRole.SOLICITANTE)`, manejo de `401 -> SessionExpired`, y ausencia total de adjuntos/notificaciones externas (`RNF-10`).
- [x] Se definen `Modifier.testTag` estables para verificación automática con `Compose UI Test` tanto en `MyRequestsScreen`, `CreateRequestScreen`, `RequestDetailScreen` como en `CancelRequestDialog`.
- [x] No quedan puntos pendientes marcados con `[NECESITA ACLARACIÓN]`.

## 3. Preparación para Implementación (`tasks.md`)
- [x] El desglose secuencial en `specs/003-service-requests/tasks.md` está redactado y aprobado en orden TDD.
- [x] Cada tarea de `tasks.md` define su criterio de verificación automatizada (`ktlintCheck`, `Ktor MockEngine`, `kotlinx-coroutines-test`, `Compose UI Test`, `koverVerify`).
- [x] Aprobación explícita final e implementación completada de `003-service-requests` (`T01..T07`, Frontend `SOLICITANTE`).
