# Investigación Técnica y Decisiones Arquitectónicas: Consolidado y Reportes de Coordinación (Frontend)

**Feature:** `005-coordination-reports`
**Subproyecto:** Frontend (Kotlin Multiplatform + Compose Multiplatform)
**Spec Asociada:** `specs/005-coordination-reports/spec.md`
**Versión:** 1.0.0
**Fecha:** 2026-11-14
**Estado:** Aprobado

---

## 1. Decisiones Arquitectónicas y Análisis de Alternativas

### D1. Visualización de Desgloses con Componentes Nativos Compose Material 3
- **Contexto:** `RF-06` requiere mostrar totales por estado, categoría, prioridad, responsable, vencidas y cumplimiento SLA en Web (`wasmJs`) y Android sin introducir dependencias externas no autorizadas en la Constitución Frontend.
- **Decisión:** Implementar tarjetas KPI y barras de proporción horizontales nativas en **Compose Material 3** (`LinearProgressIndicator` / `BoxWithConstraints` + `Surface` responsivos con layout adaptativo `FlowRow`/columnas).
- **Beneficio:** Cero librerías externas adicionales, compatibilidad garantizada en `wasmJs` + Android, y verificabilidad directa en `Compose UI Test`.

### D2. Separación entre Filtros en Edición (`draftFilter`) y Filtros Aplicados (`appliedFilter`)
- **Contexto:** Si el coordinador escribe un rango de fechas o cambia varios selectores antes de pulsar `"Aplicar filtros"`, disparar peticiones en cada pulsación causaría condiciones de carrera, errores prematuros de fecha incompleta o discrepancia al cambiar de página.
- **Decisión:** Mantener en `CoordinationReportsUiState` dos representaciones explícitas:
  - `draftFilter`: estado editable de los inputs (`fromDateInput`, `toDateInput`, `selectedStatus`, `selectedCategory`, `selectedPriority`, `selectedAssigneeId`) + errores de validación inline.
  - `appliedFilter`: último criterio validado y confirmado con `"Aplicar filtros"`.
- **Beneficio:** Las acciones de paginación (`Anterior` / `Siguiente`) siempre consultan `appliedFilter` de forma determinística, evitando inconsistencias entre el tablero visible y la página solicitada.

### D3. Ejecución en Paralelo de Resumen + Listado e Independencia al Paginar (`RNF-07`)
- **Contexto:** El tablero requiere tanto `/api/v1/reports/summary` como `/api/v1/reports/requests` con los mismos filtros aplicados.
- **Decisión:**
  - En carga inicial, `"Aplicar filtros"` y `"Limpiar filtros"`, `CoordinationReportsViewModel` ejecuta en paralelo (`async` + `await` con `coroutineScope`) `getSummary(appliedFilter)` y `getRequests(appliedFilter, page = 1)`.
  - Al navegar entre páginas (`page >= 1`), consulta **únicamente** `getRequests(appliedFilter, page = targetPage)`, conservando en memoria el `summary` ya calculado para ese `appliedFilter`.
- **Beneficio:** Reduce a la mitad la latencia inicial (`< 2s`), evita tráfico duplicado de agregaciones SQL al paginar y elimina parpadeos visuales en las tarjetas KPI.

### D4. Validación y Conversión de Fechas `dd/mm/aaaa` (UI) $\leftrightarrow$ `YYYY-MM-DD` (API)
- **Contexto:** La Constitución Frontend exige formato colombiano en pantalla (`dd/mm/aaaa`), mientras el contrato REST del Backend recibe `YYYY-MM-DD` en zona `America/Bogota`.
- **Decisión:** Implementar `ReportFilterValidator` en `domain/validation/` usando **`kotlinx-datetime`** (`LocalDate`) para validar calendario gregoriano real (rechazando fechas inexistentes como `31/02/2026` o cadenas mal formadas), validar `fromDate <= toDate` y transformar limpiamente a `YYYY-MM-DD` para `ReportFilterQuery`.

### D5. Reutilización de `StaffRepository`, `OverdueBadge` y `AttentionDetailRoute`
- **Contexto:** Evitar duplicación de pantallas de detalle, selectores de agentes y componentes visuales ya especificados en `002` y `004`.
- **Decisión:**
  - **Responsables del filtro:** Reutilizar `StaffRepository` (`role = PERSONAL_ATENCION`), acumulando todas las páginas si `totalPages > 1`, ejecutado únicamente bajo sesión `COORDINADOR` (garantizado por `RequireRole`).
  - **Vencimiento:** Reutilizar `OverdueBadge` (`004`) para tarjetas del listado general.
  - **Detalle completo e historial:** Reutilizar `AttentionDetailRoute(code)` (`004-attention-lifecycle`) al hacer clic en cualquier fila del listado general.

---

## 2. Riesgos Identificados y Mitigaciones

| Riesgo | Impacto | Mitigación Planificada |
| :--- | :--- | :--- |
| Falla parcial en carga paralela (`summary` ok, `requests` falla o viceversa) | Estado inconsistente o carga colgada | Capturar ambos resultados de forma controlada en `CoordinationReportsViewModel`, liberando `isLoading` y mostrando mensaje mapeado con botón `"Reintentar"`. |
| Selector de responsables incompleto si hay más de 20 agentes | No permite filtrar por algunos agentes | Bucle de acumulación hasta `totalPages` en `StaffRepository` (solo para `COORDINADOR`, patrón probado en `004`). |
| Pérdida de posición o filtros al volver desde `AttentionDetailRoute(code)` | Fricción UX al auditar varios casos del reporte | Conservar la instancia del `CoordinationReportsViewModel` en el backstack mientras `CoordinationReportsRoute` permanezca en la pila de navegación. |
| Acceso indebido de otros roles por ruta directa | Exposición de UI gerencial | `RequireRole(UserRole.COORDINADOR)` envolviendo `CoordinationReportsRoute` en `AppNavHost` antes de instanciar pantallas/ViewModels que disparen llamadas HTTP. |
