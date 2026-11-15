# Checklist de Tareas Técnicas: 002-auth-users (Frontend)
**Ubicación:** `specs/002-auth-users/tasks.md`  
**Plan Asociado:** `frontend/specs/002-auth-users/plan.md`  
**Estado:** Aprobado  
**Progreso:** 0% (0 / 7 tareas completadas)  

---

## Tareas de Implementación

### Fase 1: Capa Dominio Portable, Validación Tabla 1 y Contratos Data
- [ ] **T01: Implementar modelos de dominio, interfaces de repositorio y `UserFormValidator` multiplataforma**
  - **Archivos:** `composeApp/src/commonMain/.../domain/model/{UserRole.kt, UserProfile.kt, AuthSession.kt, StaffMember.kt, StaffPage.kt, AuthCommands.kt}`, `domain/repository/{AuthRepository.kt, StaffRepository.kt}`, `domain/validation/UserFormValidator.kt`
  - **Detalle:** Enmascarar contraseñas y `accessToken` en `toString()`; implementar `UserFormValidator` usando `Char.isLetter()`, `Char.isDigit()` y `encodeToByteArray().size <= 72` para paridad exacta entre Android y `wasmJs` sin regex propietarios.
  - **Verificación:** `./gradlew ktlintCheck compileCommonMainKotlinMetadata` finaliza con código de salida 0.

- [ ] **T02: Implementar DTOs `@Serializable`, Mappers (`DTO -> Domain -> UiModel`), RemoteDataSources y ajuste en `HttpClientFactory`**
  - **Archivos:** `composeApp/src/commonMain/.../data/dto/{AuthDtos.kt, StaffDtos.kt}`, `data/mapper/{AuthMapper.kt, StaffMapper.kt, StaffUiMapper.kt, ErrorMapper.kt}`, `data/remote/{AuthRemoteDataSource.kt, StaffRemoteDataSource.kt}`, `core/network/HttpClientFactory.kt`
  - **Detalle:** Asegurar `RegisterRequestDto` sin campo `role`, fechas en `dd/mm/aaaa hh:mm` (`America/Bogota`) y etiquetas de rol en español; excluir `/api/v1/auth/login` y `/api/v1/auth/register` del emisor global `SessionExpired`.
  - **Verificación:** `./gradlew ktlintCheck compileCommonMainKotlinMetadata` pasa sin errores.

### Fase 2: Suite TDD Inicial en `commonTest` (En Rojo)
- [ ] **T03: Escribir suite TDD (en rojo) para validadores, mappers, repositorios (`MockEngine`), ViewModels y `Compose UI Test`**
  - **Archivos:** `composeApp/src/commonTest/.../{UserFormValidatorTest.kt, AuthMapperTest.kt, StaffUiMapperTest.kt, AuthRepositoryTest.kt, StaffRepositoryTest.kt, SessionViewModelTest.kt, LoginViewModelTest.kt, RegisterViewModelTest.kt, StaffListViewModelTest.kt, CreateStaffViewModelTest.kt, LoginScreenTest.kt, RegisterScreenTest.kt, StaffScreensTest.kt, RoleGuardAndLogoutTest.kt}`
  - **Detalle:** Cubrir todos los escenarios BDD de `spec.md`: auto-login tras registro (`201`), errores Tabla 1 y `409 Conflict`, login (`200`/`401` sin bucle), restauración de sesión (`/auth/me`), confirmación y cierre seguro (`/auth/logout` con `204` y `401`), paginación `20/pág` con filtros y fechas `America/Bogota`, confirmación al crear personal (`COORDINADOR`) y bloqueo RBAC (`RequireRole`) para roles no autorizados.
  - **Verificación:** `./gradlew check` compila y falla inicialmente en rojo por implementaciones pendientes (`./gradlew ktlintCheck` pasa en verde).

### Fase 3: Capa Data (`RepositoryImpl`) y ViewModels MVVM (TDD Verde)
- [ ] **T04: Implementar `AuthRepositoryImpl` y `StaffRepositoryImpl`**
  - **Archivos:** `composeApp/src/commonMain/.../data/repository/{AuthRepositoryImpl.kt, StaffRepositoryImpl.kt}`
  - **Detalle:** Guardar JWT de 1h en `SessionStorage.saveToken(...)` en `register` y `login`; en `logout` limpiar `SessionStorage` y emitir `SessionEvent.LoggedOut` tanto ante `204` como ante `401`; mantener intacta la sesión del Coordinador al consumir `/api/v1/users/staff`.
  - **Verificación:** `./gradlew :composeApp:testDebugUnitTest --tests "*AuthRepositoryTest*" --tests "*StaffRepositoryTest*" --tests "*MapperTest*" --tests "*ValidatorTest*"` pasa al 100% en verde.

- [ ] **T05: Implementar `SessionViewModel`, `LoginViewModel`, `RegisterViewModel`, `StaffListViewModel`, `CreateStaffViewModel` y cableado Koin**
  - **Archivos:** `composeApp/src/commonMain/.../viewmodel/{SessionViewModel.kt, LoginViewModel.kt, RegisterViewModel.kt, StaffListViewModel.kt, CreateStaffViewModel.kt}`, `core/di/AppModules.kt`
  - **Detalle:** `UiState` inmutables con `toString()` enmascarado y limpieza inmediata de contraseñas tras éxito; inyección de `CoroutineDispatcher` para determinismo en tests; paginación estricta `size = 20` y manejo de confirmaciones `RNF-09`.
  - **Verificación:** `./gradlew :composeApp:testDebugUnitTest --tests "*ViewModelTest*" ktlintCheck` pasa al 100% en verde.

### Fase 4: Componentes Material 3, Pantallas Responsivas y Guardias RBAC (`AppNavHost`)
- [ ] **T06: Implementar componentes compartidos (`ConfirmationDialog`, `RequireRole`), pantallas (`Login`, `Register`, `Homes`, `StaffList`, `CreateStaff`) y `AppNavHost`**
  - **Archivos:** `composeApp/src/commonMain/.../ui/components/{ConfirmationDialog.kt, PasswordTextField.kt, AuthenticatedTopBar.kt, RequireRole.kt}`, `ui/screens/auth/{LoginScreen.kt, RegisterScreen.kt}`, `ui/screens/home/{ApplicantHomeScreen.kt, AgentHomeScreen.kt, CoordinatorHomeScreen.kt}`, `ui/screens/staff/{StaffListScreen.kt, CreateStaffScreen.kt}`, `ui/navigation/{Routes.kt, AppNavHost.kt}`
  - **Detalle:** Textos 100% en español, diseño responsivo Web (`wasmJs`) y Android, etiquetas `Modifier.testTag` estables, diálogos de confirmación en cierre de sesión y creación de personal, y navegación protegida con limpieza de backstack en login/logout/expiración.
  - **Verificación:** `./gradlew :composeApp:testDebugUnitTest ktlintCheck` pasa al 100% en verde (incluyendo `LoginScreenTest`, `RegisterScreenTest`, `StaffScreensTest`, `RoleGuardAndLogoutTest`).

### Fase 5: Verificación Multiplataforma, Cobertura Kover $\ge 90\%$ y Cierre
- [ ] **T07: Verificar compilación `wasmJs` + Android, cobertura $\ge 90\%$ en Kover y actualizar `docs/version_map.md`**
  - **Archivos:** `docs/version_map.md`, `specs/002-auth-users/checklist.md`
  - **Detalle:** Verificar compilación de targets reales (`assembleDebug`, `wasmJsBrowserDistribution`), validar ausencia de `localStorage` y de fugas de contraseñas/tokens, y registrar avance de `002-auth-users` en `version_map.md`.
  - **Verificación:** `./gradlew check koverVerify compileKotlinWasmJs assembleDebug` pasa con `0` errores en `ktlintCheck`, 100% de tests en verde y cobertura global $\ge 90\%$ en JetBrains Kover.
