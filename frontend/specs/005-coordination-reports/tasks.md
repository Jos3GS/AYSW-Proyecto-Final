# Plan de Tareas (Tasks): Consolidado y Reportes de Coordinación (Frontend)

**Feature:** `005-coordination-reports`
**Subproyecto:** Frontend (Kotlin Multiplatform + Compose Multiplatform)
**Versión:** 1.0.0
**Fecha:** 2026-11-14
**Estado:** Aprobado

---

## Fase 1: Dominio, Validaciones y Contratos de Datos

- [ ] **T5-FE-01 — Modelos de Dominio de Reportes (`ReportModels.kt`)**
  - **Archivo:** `commonMain/.../domain/model/ReportModels.kt`
  - **Detalle:** Declarar `ReportFilterQuery`, `AssigneeMetric`, `CoordinationReportSummary`, `CoordinationReportRequestItem` y `CoordinationReportRequestsPage`.
  - **Criterio de verificación:** Compila en `commonMain` sin dependencias de Ktor ni Compose.

- [ ] **T5-FE-02 — Validador de Filtros de Fecha y Rango (`ReportFilterValidator`)**
  - **Archivos:** `commonMain/.../domain/validation/ReportFilterValidator.kt`, `commonTest/.../domain/validation/ReportFilterValidatorTest.kt`
  - **Detalle:** Implementar validación de fechas opcionales en formato `dd/mm/aaaa` mediante `kotlinx-datetime` (calendario gregoriano real), verificación `fromDate <= toDate` y conversión a `YYYY-MM-DD`.
  - **Criterio de verificación:** Pruebas unitarias cubren fechas vacías, fechas válidas, `31/02/2026`, formatos inválidos (`2026-11-14`) y rango invertido (`fromDate > toDate`).

- [ ] **T5-FE-03 — DTOs `@Serializable` y Mappers (`ReportMapper` + `ReportUiMapper`)**
  - **Archivos:** `commonMain/.../data/dto/reports/ReportDtos.kt`, `commonMain/.../data/mapper/ReportMapper.kt`, `commonMain/.../data/mapper/ReportUiMapper.kt`, `commonTest/.../data/mapper/ReportMapperTest.kt`, `commonTest/.../data/mapper/ReportUiMapperTest.kt`
  - **Detalle:** Declarar DTOs compatibles con `ignoreUnknownKeys = false`; mapear DTO <-> Dominio y construir `CoordinationReportSummaryUi` / `CoordinationReportRequestItemUi` con proporciones `0f..1f`, etiquetas en español (6 estados, 4 categorías, 3 prioridades + `"Sin prioridad"`, `"Sin responsable"`, `"Dentro del plazo"`, `"Fuera del plazo"`) y fechas `dd/mm/aaaa hh:mm` (`America/Bogota`).
  - **Criterio de verificación:** Tests unitarios validan deserialización exacta y cálculo de proporciones con totales en `0` y `> 0`.

---

## Fase 2: Capa de Red, Repositorio e Inyección de Dependencias

- [ ] **T5-FE-04 — Fuente Remota Ktor y `ReportRepositoryImpl`**
  - **Archivos:** `commonMain/.../domain/repository/ReportRepository.kt`, `commonMain/.../data/remote/ReportRemoteDataSource.kt`, `commonMain/.../data/repository/ReportRepositoryImpl.kt`, `commonTest/.../data/repository/ReportRepositoryImplTest.kt`
  - **Detalle:** Implementar llamadas a `GET /api/v1/reports/summary` y `GET /api/v1/reports/requests` omitiendo query params nulos y enviando `page` + `size=20` e integrando `ErrorMapper`.
  - **Criterio de verificación:** `ReportRepositoryImplTest` con `Ktor MockEngine` verifica paths exactos, combinación de query params y códigos `400`, `401`, `403`.

- [ ] **T5-FE-05 — Registro en Koin (`AppModules.kt`)**
  - **Archivos:** `commonMain/.../core/di/AppModules.kt`
  - **Detalle:** Registrar `ReportRemoteDataSource`, `ReportRepository`, `ReportFilterValidator` y `CoordinationReportsViewModel`.
  - **Criterio de verificación:** Grafo de Koin resuelve el módulo sin errores en test de DI.

---

## Fase 3: ViewModel y Máquina de Estado UDF

- [ ] **T5-FE-06 — `CoordinationReportsViewModel` y `CoordinationReportsUiState`**
  - **Archivos:** `commonMain/.../viewmodel/reports/CoordinationReportsUiState.kt`, `commonMain/.../viewmodel/reports/CoordinationReportsViewModel.kt`, `commonTest/.../viewmodel/reports/CoordinationReportsViewModelTest.kt`
  - **Detalle:** Implementar carga inicial paralela (`getSummary` + `getRequests(page=1, size=20)` + carga multipágina completa de `StaffRepository`), separación `draftFilter` / `appliedFilter`, `onApplyFilters()` con validación previa, `onClearFilters()` y `onPageSelected(page)` consultando únicamente `/requests`.
  - **Criterio de verificación:** Tests unitarios validan ejecución paralela, selector de >20 agentes, bloqueo de red cuando la fecha es inválida, limpieza total y paginación sin reconsultar `/summary`.

---

## Fase 4: Interfaz Compose Material 3 y Navegación

- [ ] **T5-FE-07 — Componentes `ReportSummarySection` y `ReportFilterPanel`**
  - **Archivos:** `commonMain/.../ui/components/ReportSummarySection.kt`, `commonMain/.../ui/components/ReportFilterPanel.kt`
  - **Detalle:** Construir tarjetas KPI, barras horizontales proporcionales M3 nativas en español y panel de filtros con campos `dd/mm/aaaa`, errores inline, selectores desplegables y botones `"Aplicar filtros"` / `"Limpiar filtros"`.
  - **Criterio de verificación:** Componentes renderizan correctamente en ancho Web (`wasmJs`) y móvil Android sin desbordamientos.

- [ ] **T5-FE-08 — Pantalla `CoordinationReportsScreen` y Acceso desde `CoordinationPendingScreen`**
  - **Archivos:** `commonMain/.../ui/screens/reports/CoordinationReportsScreen.kt`, `commonMain/.../ui/screens/attention/CoordinationPendingScreen.kt`
  - **Detalle:** Integrar `ReportFilterPanel`, `ReportSummarySection`, listado general paginado (`20/pág`) reutilizando `OverdueBadge` y estado vacío en español; agregar acceso `"Reportes y consolidado"` en `CoordinationPendingScreen`.
  - **Criterio de verificación:** Cero lógica de red en composables; clic en fila emite código hacia detalle.

- [ ] **T5-FE-09 — Ruta Tipada y Guardia RBAC (`Routes.kt` / `AppNavHost.kt`)**
  - **Archivos:** `commonMain/.../ui/navigation/Routes.kt`, `commonMain/.../ui/navigation/AppNavHost.kt`
  - **Detalle:** Declarar `CoordinationReportsRoute`, protegerla con `RequireRole(UserRole.COORDINADOR)` y conectar clic de filas hacia `AttentionDetailRoute(code)` (`004`).
  - **Criterio de verificación:** `PERSONAL_ATENCION` y `SOLICITANTE` no pueden acceder a `CoordinationReportsRoute`; `COORDINADOR` navega libremente reporte <-> detalle interno.

---

## Fase 5: Pruebas de UI y Puertas Finales de Calidad

- [ ] **T5-FE-10 — Suite `Compose UI Test` de Consolidado y Reportes**
  - **Archivos:** `commonTest/.../ui/screens/reports/CoordinationReportsScreenTest.kt`
  - **Detalle:** Probar visualización de tarjetas KPI y desgloses, validación inline de fecha inválida, aplicación/limpieza de filtros, paginación `20/pág`, `OverdueBadge` y navegación al detalle de una solicitud.
  - **Criterio de verificación:** Suite completa `Compose UI Test` pasa en verde.

- [ ] **T5-FE-11 — Verificación Integral (Lint, Cobertura $\ge 90\%$ y Targets)**
  - **Comandos:**
    - `./gradlew ktlintCheck`
    - `./gradlew koverVerify`
    - `./gradlew compileKotlinWasmJs assembleDebug`
  - **Criterio de verificación:** Código de salida `0` en todos los comandos y cobertura $\ge 90\%$ verificada.
