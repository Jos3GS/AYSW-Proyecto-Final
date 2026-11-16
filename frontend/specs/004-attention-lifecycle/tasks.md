# Plan de Tareas (Tasks): Ciclo de Atención y Escalamiento (Frontend)

**Feature:** `004-attention-lifecycle`  
**Subproyecto:** Frontend (Kotlin Multiplatform + Compose Multiplatform)  
**Versión:** 1.0.0  
**Fecha:** 2026-11-14  
**Estado:** Aprobado  

---

## Fase 1: Dominio, Validaciones y Contratos de Datos

- [ ] **T4-FE-01 — Modelos de Dominio y Comandos de Atención**
  - **Archivos:** `commonMain/.../domain/model/AttentionModels.kt`
  - **Detalle:** Implementar `RequestPriority`, `HistoryActorRole`, `AttentionRequestSummary`, `AttentionRequestsPage`, `InternalHistoryEntry`, `AttentionRequestDetail` y comandos (`AssignRequestCommand`, `ReassignRequestCommand`, `AddInterventionCommand`, `EscalateRequestCommand`, `RespondRequestCommand`).
  - **Criterio de verificación:** Compila en `commonMain` sin dependencias de Ktor ni Compose.

- [ ] **T4-FE-02 — Validador de Formularios de Atención (`AttentionFormValidator`)**
  - **Archivos:** `commonMain/.../domain/validation/AttentionFormValidator.kt`, `commonTest/.../domain/validation/AttentionFormValidatorTest.kt`
  - **Detalle:** Implementar validaciones con `trim()` para `assigneeId` (no vacío), `priority` (obligatoria en asignación), `reason` (`10..500`), `comment` (`10..1000`) y `responseText` (`20..2000`), devolviendo mensajes claros en español.
  - **Criterio de verificación:** Pruebas unitarias verdes cubriendo exactamente todos los límites (`9/10`, `500/501`, `1000/1001`, `19/20`, `2000/2001`).

- [ ] **T4-FE-03 — DTOs `@Serializable` y Mappers (`AttentionMapper` + `AttentionUiMapper`)**
  - **Archivos:** `commonMain/.../data/dto/attention/AttentionDtos.kt`, `commonMain/.../data/mapper/AttentionMapper.kt`, `commonMain/.../data/mapper/AttentionUiMapper.kt`, `commonTest/.../data/mapper/AttentionMapperTest.kt`, `commonTest/.../data/mapper/AttentionUiMapperTest.kt`
  - **Detalle:** Declarar DTOs estrictos compatibles con `ignoreUnknownKeys = false`; mapear DTO <-> Dominio y formatear etiquetas en español (`"Alta (24 h)"`, `"Media (72 h)"`, `"Baja (120 h)"`, `"Vencida"`, `"Respondida dentro del plazo"`, `"Respondida fuera del plazo"`, `"Sistema"`) y fechas `dd/mm/aaaa hh:mm` (`America/Bogota`) vía `DateFormatter`.
  - **Criterio de verificación:** Tests de serialización JSON exacta y mapeo UI pasan al 100%.

---

## Fase 2: Capa de Red, Repositorio e Inyección de Dependencias

- [ ] **T4-FE-04 — Fuente Remota Ktor y Repositorio de Atención**
  - **Archivos:** `commonMain/.../domain/repository/AttentionRepository.kt`, `commonMain/.../data/remote/AttentionRemoteDataSource.kt`, `commonMain/.../data/repository/AttentionRepositoryImpl.kt`, `commonTest/.../data/repository/AttentionRepositoryImplTest.kt`
  - **Detalle:** Implementar los 8 endpoints (`/api/v1/attention/coordination/pending`, `/api/v1/attention/my-assigned`, `/api/v1/attention/requests/{code}`, `.../assign`, `.../reassign`, `.../interventions`, `.../escalate`, `.../respond`) integrando `ErrorMapper`.
  - **Criterio de verificación:** `AttentionRepositoryImplTest` con `Ktor MockEngine` verifica paths, query params (`size=20`), cuerpos JSON y códigos `400`, `403`, `404`, `409`.

- [ ] **T4-FE-05 — Registro en Koin (`AppModules.kt`)**
  - **Archivos:** `commonMain/.../core/di/AppModules.kt`
  - **Detalle:** Registrar `AttentionRemoteDataSource`, `AttentionRepository`, `AttentionFormValidator`, `CoordinationPendingViewModel`, `AgentInboxViewModel` y `AttentionDetailViewModel`.
  - **Criterio de verificación:** Grafo de Koin resuelve todas las dependencias sin errores en tests de verificación DI.

---

## Fase 3: ViewModels y Máquinas de Estado UDF

- [ ] **T4-FE-06 — `CoordinationPendingViewModel` y `CoordinationPendingUiState`**
  - **Archivos:** `commonMain/.../viewmodel/attention/CoordinationPendingUiState.kt`, `commonMain/.../viewmodel/attention/CoordinationPendingViewModel.kt`, `commonTest/.../viewmodel/attention/CoordinationPendingViewModelTest.kt`
  - **Detalle:** Carga inicial paginada (`size=20`), filtros de estado `ABIERTA` | `ESCALADA` | Todos, paginación y recarga manual.
  - **Criterio de verificación:** Tests verifican transiciones de carga, filtrado, cambio de página y manejo de errores.

- [ ] **T4-FE-07 — `AgentInboxViewModel` y `AgentInboxUiState`**
  - **Archivos:** `commonMain/.../viewmodel/attention/AgentInboxUiState.kt`, `commonMain/.../viewmodel/attention/AgentInboxViewModel.kt`, `commonTest/.../viewmodel/attention/AgentInboxViewModelTest.kt`
  - **Detalle:** Carga paginada (`size=20`) de `/api/v1/attention/my-assigned` con filtros `EN_REVISION`, `ESCALADA`, `RESUELTA`, `CERRADA`.
  - **Criterio de verificación:** Tests unitarios verifican filtros por estado, paginación y errores HTTP.

- [ ] **T4-FE-08 — `AttentionDetailViewModel` y `AttentionDetailUiState`**
  - **Archivos:** `commonMain/.../viewmodel/attention/AttentionDetailUiState.kt`, `commonMain/.../viewmodel/attention/AttentionDetailViewModel.kt`, `commonTest/.../viewmodel/attention/AttentionDetailViewModelTest.kt`
  - **Detalle:** Orquestar carga de detalle interno, carga multipágina condicional de `StaffRepository` **solo** cuando `COORDINADOR` + `status IN {ABIERTA, ESCALADA}`, aislamiento de borradores en `AgentActionTab` (`INTERVENTION`, `ESCALATE`, `RESPOND`), flujo `PendingConfirmationAction` y manejo de `409 Conflict` con cierre de diálogo y recarga automática de `getInternalDetail(code)`.
  - **Criterio de verificación:** Tests unitarios cubren `COORDINADOR` (>20 agentes en 2+ páginas), `PERSONAL_ATENCION` (sin llamada a `/users/staff`), cambio de pestaña preservando borradores y recuperación `409`.

---

## Fase 4: Interfaz Compose Multiplatform y Navegación

- [ ] **T4-FE-09 — Componentes Reutilizables (`OverdueBadge` y `AttentionActionPanel`)**
  - **Archivos:** `commonMain/.../ui/components/OverdueBadge.kt`, `commonMain/.../ui/components/AttentionActionPanel.kt`
  - **Detalle:** Construir `OverdueBadge` (`"Vencida"`) estrictamente informativo y `AttentionActionPanel` con vistas condicionales por rol/estado (`Assign`, `Reassign`, `AgentActionTab` de 3 pestañas, solo lectura en `RESUELTA`/`CERRADA`), conectado a `ConfirmationDialog`.
  - **Criterio de verificación:** Tests de componentes verifican visibilidad por rol/estado y que `OverdueBadge` no deshabilita ningún botón.

- [ ] **T4-FE-10 — Pantallas `CoordinationPendingScreen`, `AgentInboxScreen` y `AttentionDetailScreen`**
  - **Archivos:** `commonMain/.../ui/screens/attention/CoordinationPendingScreen.kt`, `commonMain/.../ui/screens/attention/AgentInboxScreen.kt`, `commonMain/.../ui/screens/attention/AttentionDetailScreen.kt`
  - **Detalle:** Construir bandejas paginadas (20/página) en español, acceso a `StaffListRoute` desde coordinación y detalle interno completo (SLA, bloque de respuesta oficial, historial interno con `"Sistema"`, `AttentionActionPanel`, `ConfirmationDialog`).
  - **Criterio de verificación:** Compila y renderiza correctamente sin lógica de red en UI.

- [ ] **T4-FE-11 — Actualización de Homes (`002`) y Navegación Tipada (`AppNavHost` / `Routes`)**
  - **Archivos:** `commonMain/.../ui/navigation/Routes.kt`, `commonMain/.../ui/navigation/AppNavHost.kt`, `commonMain/.../ui/screens/.../CoordinatorHomeScreen.kt`, `commonMain/.../ui/screens/.../AgentHomeScreen.kt`
  - **Detalle:** Reemplazar los placeholders de `CoordinatorHomeRoute` y `AgentHomeRoute`, agregar `AttentionDetailRoute(val code: String)` y proteger con `RequireRole`.
  - **Criterio de verificación:** Navegación RBAC impide el acceso de `SOLICITANTE` a rutas de atención y enlaza bandejas <-> detalle.

---

## Fase 5: Pruebas de UI y Puertas Finales de Calidad

- [ ] **T4-FE-12 — Suite `Compose UI Test` para Flujos de Atención**
  - **Archivos:** `commonTest/.../ui/screens/attention/CoordinationPendingScreenTest.kt`, `commonTest/.../ui/screens/attention/AgentInboxScreenTest.kt`, `commonTest/.../ui/screens/attention/AttentionDetailScreenTest.kt`
  - **Detalle:** Probar interacción completa: filtros, selección de agente/prioridad, confirmación en `ConfirmationDialog`, cambio entre pestañas de `EN_REVISION`, visualización de `OverdueBadge`, historial `"Sistema"` y recarga tras error `409`.
  - **Criterio de verificación:** Suite completa de UI tests pasa en verde.

- [ ] **T4-FE-13 — Verificación Integral (Lint, Cobertura $\ge 90\%$ y Multiplataforma)**
  - **Comandos:**
    - `./gradlew ktlintCheck`
    - `./gradlew koverVerify`
    - `./gradlew compileKotlinWasmJs assembleDebug`
  - **Criterio de verificación:** Todos los comandos finalizan con código de salida `0` y cobertura global y de nuevos paquetes $\ge 90\%$.
