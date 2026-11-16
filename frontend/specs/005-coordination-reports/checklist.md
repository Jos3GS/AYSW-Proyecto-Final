# Checklist de Verificación: Consolidado y Reportes de Coordinación (Frontend)

**Feature:** `005-coordination-reports`
**Subproyecto:** Frontend (Kotlin Multiplatform + Compose Multiplatform)
**Versión:** 1.0.0
**Fecha:** 2026-11-14
**Estado:** Aprobado

---

## 1. Cumplimiento Constitucional y Arquitectónico

- [ ] **C1.1** La arquitectura respeta estrictamente MVVM + UDF + Repository + Mapper; cero llamadas HTTP ni serialización dentro de composables.
- [ ] **C1.2** Todos los gráficos/desgloses se construyen con componentes nativos Compose Material 3 sin introducir librerías de terceros fuera de la Constitución.
- [ ] **C1.3** El grafo Koin (`AppModules.kt`) registra `ReportRemoteDataSource`, `ReportRepository`, `ReportFilterValidator` y `CoordinationReportsViewModel`.
- [ ] **C1.4** El 100% de textos visibles, encabezados, etiquetas de estados/categorías/prioridades, mensajes vacíos y errores de validación están en español.
- [ ] **C1.5** Todas las fechas mostradas (`generatedAt`, `createdAt`, `dueAt`, `respondedAt`) usan formato colombiano `dd/mm/aaaa hh:mm` en zona `America/Bogota` vía `DateFormatter` y `ReportUiMapper`.

---

## 2. Contratos API, DTOs y Serialización Estricta

- [ ] **C2.1** Todos los DTOs en `data/dto/reports/ReportDtos.kt` cumplen `ignoreUnknownKeys = false` y coinciden campo por campo con los contratos de `005-coordination-reports` (Backend).
- [ ] **C2.2** `GET /api/v1/reports/summary` y `GET /api/v1/reports/requests` omiten query parameters nulos y envían únicamente filtros seleccionados (`createdFrom`, `createdTo` en `YYYY-MM-DD`, `status`, `category`, `priority`, `assignedToId`), además de `page` (`>= 1`) y `size=20` en `/requests`.
- [ ] **C2.3** `ReportUiMapper` renderiza siempre las 6 categorías de estado, 4 categorías de solicitud, 3 prioridades + `"Sin prioridad"`, y carga por responsable + `"Sin responsable"` aun cuando existan contadores en `0`.

---

## 3. Navegación, RBAC y Comportamiento del Tablero

- [ ] **C3.1** `CoordinationReportsRoute` está protegida por `RequireRole(UserRole.COORDINADOR)` bloqueando a `PERSONAL_ATENCION` y `SOLICITANTE` sin disparar peticiones HTTP.
- [ ] **C3.2** `CoordinationPendingScreen` expone acceso visible (`"Reportes y consolidado"`) hacia `CoordinationReportsRoute`.
- [ ] **C3.3** Al seleccionar cualquier solicitud en el listado general de `CoordinationReportsScreen`, navega a `AttentionDetailRoute(code)` (`004-attention-lifecycle`) para visualizar detalle e historial interno completo.
- [ ] **C3.4** El selector de responsables carga todas las páginas de `StaffRepository` (`role = PERSONAL_ATENCION`) hasta `totalPages` cuando existen más de 20 agentes.

---

## 4. Validación de Filtros, Sincronización y Paginación

- [ ] **C4.1** `ReportFilterValidator` valida formato estricto `dd/mm/aaaa` (calendario gregoriano real vía `kotlinx-datetime`, rechazando por ejemplo `31/02/2026`), valida `fromDate <= toDate` y convierte a `YYYY-MM-DD` antes de tocar red.
- [ ] **C4.2** Existe separación clara entre `draftFilter` (edición) y `appliedFilter` (vigente): cambiar controles en pantalla no dispara requests automáticos hasta pulsar `"Aplicar filtros"`.
- [ ] **C4.3** En carga inicial, `"Aplicar filtros"` y `"Limpiar filtros"`, `CoordinationReportsViewModel` ejecuta en paralelo `getSummary` y `getRequests(..., page = 1, size = 20)`.
- [ ] **C4.4** Al cambiar de página (`onPageSelected`), solo se consulta `getRequests(appliedFilter, targetPage, 20)` sin recargar `/summary` y manteniendo coherencia con el criterio vigente.
- [ ] **C4.5** En las filas del listado general se muestra `OverdueBadge` (`"Vencida"`) cuando `isOverdue == true` y badge SLA (`"Dentro del plazo"` / `"Fuera del plazo"`) cuando `respondedWithinSla != null`.

---

## 5. Pruebas y Puertas de Calidad

- [ ] **C5.1** Pruebas unitarias verdes en `commonTest` para `ReportFilterValidator`, `ReportMapper`, `ReportUiMapper`, `ReportRepositoryImpl` (con `Ktor MockEngine`) y `CoordinationReportsViewModel` (carga paralela, filtros, paginación y errores).
- [ ] **C5.2** Pruebas de interfaz (`Compose UI Test`) verdes para `CoordinationReportsScreen`, `ReportFilterPanel`, `ReportSummarySection` y acceso desde `CoordinationPendingScreen`.
- [ ] **C5.3** `./gradlew ktlintCheck` pasa con 0 errores de estilo.
- [ ] **C5.4** `./gradlew koverVerify` verifica cobertura $\ge 90\%$ y `./gradlew compileKotlinWasmJs assembleDebug` compila ambos targets sin errores.
