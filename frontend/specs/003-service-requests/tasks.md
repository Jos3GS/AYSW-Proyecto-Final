# Checklist de Tareas Técnicas: 003-service-requests (Frontend)
**Ubicación:** `specs/003-service-requests/tasks.md`  
**Plan Asociado:** `frontend/specs/003-service-requests/plan.md`  
**Estado:** Completado  
**Progreso:** 100% (7 / 7 tareas completadas)  

---

## Tareas de Implementación

### Fase 1: Capa Dominio Portable, Validación Tabla 2 y Contratos Data
- [x] **T01: Implementar modelos de dominio, interfaz `ServiceRequestRepository` y `RequestFormValidator` en `commonMain`**
  - **Archivos:** `composeApp/src/commonMain/.../domain/model/{RequestCategory.kt, RequestStatus.kt, RequestHistoryAction.kt, ServiceRequestModels.kt, RequestCommands.kt}`, `domain/repository/ServiceRequestRepository.kt`, `domain/validation/RequestFormValidator.kt`
  - **Detalle:** Implementar enums y entidades puras sin dependencias HTTP/Compose; implementar `RequestFormValidator` con `trim()`, longitudes `5..100` (`subject`) y `20..2000` (`description`), patrón portable `^SOL-\d{4}-\d{5,}$` para `previousRequestCode` opcional y `cancelReason <= 500`.
  - **Verificación:** `./gradlew ktlintCheck compileCommonMainKotlinMetadata` finaliza con código de salida 0.

- [x] **T02: Implementar DTOs `@Serializable`, Mappers (`RequestMapper`, `RequestUiMapper`) y `RequestRemoteDataSource`**
  - **Archivos:** `composeApp/src/commonMain/.../data/dto/requests/ServiceRequestDtos.kt`, `data/mapper/{RequestMapper.kt, RequestUiMapper.kt}`, `data/remote/RequestRemoteDataSource.kt`
  - **Detalle:** Asegurar `CreateServiceRequestDto` sin campos gestionados por el servidor (`code`, `status`, `createdAt`, `requesterId`); mapear en `RequestUiMapper` las 4 categorías, 6 estados y acciones de historial al español junto con `dd/mm/aaaa hh:mm` (`America/Bogota`) mediante `DateFormatter` y banderas `canCancel` / `canCreateRelated`.
  - **Verificación:** `./gradlew ktlintCheck compileCommonMainKotlinMetadata` pasa sin errores.

### Fase 2: Suite TDD Inicial en `commonTest` (En Rojo)
- [x] **T03: Escribir suite TDD (en rojo) para validadores, mappers, repositorio (`MockEngine`), ViewModels y `Compose UI Test`**
  - **Archivos:** `composeApp/src/commonTest/.../{RequestFormValidatorTest.kt, RequestMapperTest.kt, RequestUiMapperTest.kt, ServiceRequestRepositoryTest.kt, MyRequestsViewModelTest.kt, CreateRequestViewModelTest.kt, RequestDetailViewModelTest.kt, MyRequestsScreenTest.kt, CreateRequestScreenTest.kt, RequestDetailScreenTest.kt}`
  - **Detalle:** Cubrir todos los escenarios BDD (`Escenario 1` a `Escenario 6`): creación Tabla 2 (`201`/`400`), error remoto de `previousRequestCode` mapeado al campo, listado paginado `20/pág` con filtros por estado (`200`/reintento/vacío), detalle con respuesta oficial e historial visible (`RF-03`) + `403`/`404` + botón **"Nueva solicitud relacionada"** en `RESUELTA`/`CERRADA`, cancelación modal (`RNF-09`) exclusiva de `ABIERTA` con manejo de `409 Conflict` y recarga automática, y bloqueo RBAC (`RequireRole(SOLICITANTE)`).
  - **Verificación:** `./gradlew check` compila y falla inicialmente en rojo por implementaciones faltantes (`./gradlew ktlintCheck` pasa en verde).

### Fase 3: Repositorio Data y ViewModels MVVM + Koin (TDD Verde)
- [x] **T04: Implementar `ServiceRequestRepositoryImpl` y registrar módulo en Koin (`AppModules.kt`)**
  - **Archivos:** `composeApp/src/commonMain/.../data/repository/ServiceRequestRepositoryImpl.kt`, `core/di/AppModules.kt`
  - **Detalle:** Implementar `create`, `getMyRequests`, `getDetail` y `cancel` propagando errores tipados (`400` `Validation`, `401` `Unauthorized`, `403` `Forbidden`, `404` `NotFound`, `409` `Conflict`) mediante `ErrorMapper` sin alterar el token vigente.
  - **Verificación:** `./gradlew :composeApp:testDebugUnitTest --tests "*RequestFormValidatorTest*" --tests "*RequestMapperTest*" --tests "*RequestUiMapperTest*" --tests "*ServiceRequestRepositoryTest*"` pasa al 100% en verde.

- [x] **T05: Implementar `MyRequestsViewModel`, `CreateRequestViewModel` y `RequestDetailViewModel` con sus `*UiState`**
  - **Archivos:** `composeApp/src/commonMain/.../viewmodel/requests/{MyRequestsUiState.kt, MyRequestsViewModel.kt, CreateRequestUiState.kt, CreateRequestViewModel.kt, RequestDetailUiState.kt, RequestDetailViewModel.kt}`, `core/di/AppModules.kt`
  - **Detalle:** Paginación estricta `size = 20` y reinicio a `page = 1` al cambiar filtro; prevención anti-doble clic (`isSubmitting`, `isCancelling`); pre-carga opcional de `previousRequestCode`; y manejo de `409` en cancelación cerrando el diálogo, mostrando `actionError` y recargando `getDetail(code)`.
  - **Verificación:** `./gradlew :composeApp:testDebugUnitTest --tests "*RequestsViewModelTest*" --tests "*RequestViewModelTest*" ktlintCheck` pasa al 100% en verde.

### Fase 4: Componentes UI Material 3, Pantallas Responsivas y Navegación Tipada
- [x] **T06: Implementar `RequestStatusChip`, `CancelRequestDialog`, pantallas (`MyRequests`, `CreateRequest`, `RequestDetail`), actualización de `ApplicantHomeScreen` y `AppNavHost`**
  - **Archivos:** `composeApp/src/commonMain/.../ui/components/{RequestStatusChip.kt, CancelRequestDialog.kt}`, `ui/screens/requests/{MyRequestsScreen.kt, CreateRequestScreen.kt, RequestDetailScreen.kt}`, `ui/screens/home/ApplicantHomeScreen.kt`, `ui/navigation/{Routes.kt, AppNavHost.kt}`
  - **Detalle:** Reemplazar placeholder de `ApplicantHomeScreen` con `MyRequestsScreen` (tabla desktop / tarjetas móvil + paginación `20/pág`); construir `CreateRequestScreen` con selector de 4 categorías, contadores `5..100` y `20..2000`; construir `RequestDetailScreen` con respuesta oficial destacada, enlace a solicitud previa y diálogo modal `RNF-09` con motivo opcional `<= 500`; envolver las rutas bajo `RequireRole(UserRole.SOLICITANTE)` manteniendo los `Modifier.testTag` estables.
  - **Verificación:** `./gradlew :composeApp:testDebugUnitTest ktlintCheck` pasa al 100% en verde (incluyendo `MyRequestsScreenTest`, `CreateRequestScreenTest`, `RequestDetailScreenTest`).

### Fase 5: Verificación Multiplataforma, Cobertura Kover $\ge 90\%$ y Cierre
- [x] **T07: Verificar compilación `wasmJs` + Android, cobertura $\ge 90\%$ y actualizar `docs/version_map.md`**
  - **Archivos:** `docs/version_map.md`, `specs/003-service-requests/checklist.md`
  - **Detalle:** Comprobar compilación real Web `wasmJs` y Android `debug`, validar cumplimiento de `RF-02`, `RF-03`, `RNF-07`, `RNF-09` y `RNF-10`, y actualizar el estado de `003-service-requests` en `version_map.md`.
  - **Verificación:** `./gradlew check koverVerify compileKotlinWasmJs assembleDebug` finaliza con `0` errores en `ktlintCheck`, 100% de tests en verde y cobertura global $\ge 90\%$ en JetBrains Kover.
