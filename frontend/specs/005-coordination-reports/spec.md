# Especificación Funcional: Consolidado y Reportes de Coordinación (Frontend)

**Feature:** `005-coordination-reports`
**Subproyecto:** Frontend (Kotlin Multiplatform + Compose Multiplatform)
**Hito del Roadmap:** `v0.3.0 (Consolidado de Coordinación)`
**Trazabilidad:** `HU-06`, `RF-06`, `RNF-07`, `RNF-09`, `RNF-10`
**Versión:** 1.0.0
**Fecha:** 2026-11-14
**Estado:** Aprobado

---

## 1. Propósito y Alcance

Esta especificación define la interfaz gráfica multiplataforma (`wasmJs` Web y Android) del **Tablero Consolidado y Listado General de Solicitudes** para el rol **`COORDINADOR`**, consumiendo los endpoints `GET /api/v1/reports/summary` y `GET /api/v1/reports/requests` del Backend e integrándose de forma fluida con la navegación existente de `002-auth-users` y `004-attention-lifecycle`.

### Dentro del Alcance
1. Acceso directo desde la vista principal de Coordinación (`CoordinationPendingScreen` / `CoordinatorHomeRoute`) hacia la pantalla **`CoordinationReportsScreen`** (`CoordinationReportsRoute`).
2. **Resumen consolidado en español:** tarjetas KPI (`Total de solicitudes`, `Vencidas activas`, `Resueltas dentro del plazo`, `Resueltas fuera del plazo`) y desgloses completos por **Estado**, **Categoría**, **Prioridad** (incluyendo `"Sin prioridad"`) y **Responsable** (incluyendo `"Sin responsable"` y conteo de vencidas por agente).
3. **Panel de filtros dinámicos combinables:** rango de fechas de creación (`dd/mm/aaaa` en interfaz, convertido a `YYYY-MM-DD` `America/Bogota`), **Estado**, **Categoría**, **Prioridad** y **Responsable**, con acciones `"Aplicar filtros"` y `"Limpiar filtros"`.
4. **Listado general paginado (`20` elementos por página):** sincronizado con los mismos filtros aplicados al resumen consolidado, mostrando estado, prioridad, responsable, vencimiento informativo (`"Vencida"`), cumplimiento de plazo SLA y fechas en formato colombiano `dd/mm/aaaa hh:mm` (`America/Bogota`).
5. **Navegación a inspección completa:** al seleccionar cualquier solicitud del listado general, navegar a `AttentionDetailRoute(code)` (`004-attention-lifecycle`) para ver descripción, respuesta oficial e historial interno inmutable completo.

### Fuera del Alcance (`RNF-10`)
- Descarga o exportación local de archivos (PDF, Excel, CSV).
- Mutaciones directas desde la pantalla de reportes (asignar/reasignar se realiza dentro de `AttentionDetailScreen`).
- Gráficos con librerías externas propietarias o de terceros fuera de Compose Material 3.

---

## 2. Actores y Control de Acceso (RBAC)

| Actor | Acceso UI | Comportamiento Esperado |
| :--- | :--- | :--- |
| **`COORDINADOR` autenticado** | Autorizado | Ve botón/pestaña de acceso desde `CoordinationPendingScreen` y puede abrir `CoordinationReportsRoute`. |
| **`PERSONAL_ATENCION` autenticado** | Denegado | Sin enlaces visibles hacia reportes; si accede por ruta, `RequireRole(UserRole.COORDINADOR)` bloquea la vista en español y permite volver a su bandeja. |
| **`SOLICITANTE` autenticado** | Denegado | Sin enlaces visibles; protegido por `RequireRole(UserRole.COORDINADOR)`. |

---

## 3. Requisitos Funcionales y Experiencia de Usuario

### RF-FE-06.1 — Tablero de Resumen Consolidado
1. Muestra 4 indicadores principales en la cabecera del reporte:
   - **Total de solicitudes** (`totalRequests`)
   - **Vencidas activas** (`overdueCount`)
   - **Resueltas dentro del plazo** (`resolvedWithinSlaCount`)
   - **Resueltas fuera del plazo** (`resolvedOutsideSlaCount`)
2. Presenta secciones desglosadas (tarjetas con barras de proporción Material 3 sobre el `totalRequests`):
   - **Por estado:** los 6 estados en español (`Abierta`, `En revisión`, `Escalada`, `Resuelta`, `Cerrada`, `Cancelada`), mostrando `0` cuando no haya casos.
   - **Por categoría:** las 4 categorías (`Trámite`, `Orientación`, `Atención administrativa`, `Requerimiento de servicio`), mostrando `0` cuando corresponda.
   - **Por prioridad:** `Alta (24 h)`, `Media (72 h)`, `Baja (120 h)` y `Sin prioridad` (`unassignedPriorityCount`).
   - **Carga por responsable:** nombre del agente (`assigneeName`), total asignado (`totalAssigned`) y cantidad vencida (`overdueAssigned`), más fila explícita `Sin responsable` (`unassignedCount`).
3. En estado vacío filtrado (`totalRequests == 0`), mantiene visible la estructura del resumen en `0` y muestra en el listado el mensaje claro: *"No se encontraron solicitudes con los filtros aplicados."*

### RF-FE-06.2 — Panel de Filtros Dinámicos Combinables
1. **Rango de fechas (`Desde` / `Hasta`):**
   - Entrada amigable en español (`dd/mm/aaaa`, o selector/campo validado en `ReportFilterValidator`) convertida internamente a `YYYY-MM-DD` para el backend.
   - Validación inmediata en cliente antes de consultar: formato válido `dd/mm/aaaa` (día/mes/año calendario real) y regla `fechaDesde <= fechaHasta`; de lo contrario muestra error inline en español y no dispara llamada HTTP.
2. **Selectores opcionales (`Todos` por defecto):**
   - **Estado:** los 6 valores de `RequestStatus` en español.
   - **Categoría:** las 4 valores de `RequestCategory` en español.
   - **Prioridad:** `Alta (24 h)`, `Media (72 h)`, `Baja (120 h)`.
   - **Responsable:** lista completa de usuarios activos/registrados con rol `PERSONAL_ATENCION` obtenida vía `StaffRepository` (acumulando todas las páginas si supera 20 agentes).
3. **Sincronización Resumen ↔ Listado:**
   - Al pulsar **"Aplicar filtros"**, ambos endpoints (`/api/v1/reports/summary` y `/api/v1/reports/requests?page=1&size=20`) se consultan en paralelo para reflejar exactamente el mismo conjunto de datos.
   - Al pulsar **"Limpiar filtros"**, todos los criterios vuelven a nulo y se recargan métricas y página 1 globales.

### RF-FE-06.3 — Listado General Paginado e Inspección de Detalle
1. Muestra hasta **20 solicitudes por página** con controles `Anterior` / `Siguiente` e indicador `Página X de Y (Total: Z)`.
2. Cada tarjeta/fila muestra:
   - Código (`SOL-AAAA-NNNNN`), Asunto, Categoría y Fecha de creación (`dd/mm/aaaa hh:mm` `America/Bogota`).
   - Estado, Prioridad (`Alta (24 h)` / `Media (72 h)` / `Baja (120 h)` / `Sin asignar`), Solicitante y Responsable (`assignedToName` o `"Sin asignar"`).
   - Badge informativo `"Vencida"` (`OverdueBadge`) cuando `isOverdue == true`.
   - Badge informativo de cumplimiento SLA cuando `respondedWithinSla != null` (`"Dentro del plazo"` / `"Fuera del plazo"`).
3. Al hacer clic sobre cualquier fila, navega a **`AttentionDetailRoute(code)`** (`004-attention-lifecycle`) para visualizar el detalle e historial completo.

---

## 4. Criterios de Aceptación (Escenarios BDD)

### Escenario 1: Carga inicial del tablero y listado general (`HU-06`)
- **Dado** que un `COORDINADOR` inicia sesión y accede desde `CoordinationPendingScreen` a `"Reportes y consolidado"`,
- **Cuando** carga `CoordinationReportsScreen`,
- **Entonces** ve las 4 métricas KPI, los desgloses por estado (6), categoría (4), prioridad (3 + `Sin prioridad`) y responsable (`+ Sin responsable`), junto con la primera página (`<= 20` ítems) del listado general en español y fechas `dd/mm/aaaa hh:mm`.

### Escenario 2: Filtrado combinado sincronizado (`RF-06`)
- **Dado** el `COORDINADOR` ubicado en `CoordinationReportsScreen`,
- **Cuando** ingresa rango `01/11/2026` a `14/11/2026`, selecciona categoría `Trámite`, prioridad `Alta (24 h)` y un responsable, y pulsa `"Aplicar filtros"`,
- **Entonces** el sistema envía ambos requests con `createdFrom=2026-11-01&createdTo=2026-11-14&category=TRAMITE&priority=ALTA&assignedToId=...` y actualiza tablero + página 1 del listado con totales consistentes.

### Escenario 3: Validación de rango de fechas inválido en cliente
- **Dado** el `COORDINADOR` ingresando filtros de fecha,
- **Cuando** escribe `Desde = 20/11/2026` y `Hasta = 10/11/2026` y pulsa `"Aplicar filtros"`,
- **Entonces** aparece el mensaje inline `"La fecha final no puede ser anterior a la fecha inicial."` sin ejecutar peticiones HTTP al servidor.

### Escenario 4: Limpieza de filtros
- **Dado** filtros activos en `CoordinationReportsScreen`,
- **Cuando** pulsa `"Limpiar filtros"`,
- **Entonces** todos los campos/selectores regresan a su estado inicial y se recarga el consolidado y la página 1 globales.

### Escenario 5: Navegación al detalle completo desde el listado general
- **Dado** resultados visibles en el listado general de reportes,
- **Cuando** el `COORDINADOR` selecciona la solicitud `SOL-2026-00019`,
- **Entonces** la aplicación navega a `AttentionDetailRoute("SOL-2026-00019")` mostrando su descripción, respuesta oficial e historial completo.

### Escenario 6: Bloqueo RBAC en navegación directa
- **Dado** una sesión activa con rol `PERSONAL_ATENCION` o `SOLICITANTE`,
- **Cuando** intenta abrir `CoordinationReportsRoute`,
- **Entonces** `RequireRole(UserRole.COORDINADOR)` deniega el acceso en español sin disparar llamadas a `/api/v1/reports/*`.
