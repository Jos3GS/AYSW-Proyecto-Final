---
proyecto: ITM Service Desk
subproyecto: Frontend
feature: 005-coordination-reports
categoria: Spec_Kit / Guia_IA
fecha: 2026-11-14
version: 1.0.0
estado: Aprobado
tags:
  - spec-kit
  - frontend
  - guia-ia
  - prompt-maestro
  - reports
  - dashboard
---

# Guía de Implementación Asistida por IA — 005-coordination-reports (Frontend)

> **Estado:** Aprobado

---

## Prompt Maestro para la Sesión de Implementación

```text
Actúa como un Ingeniero Senior especialista en Kotlin Multiplatform (KMP), Compose Multiplatform (Android + WasmJs) y Arquitectura MVVM + UDF.
Tu misión es implementar EXCLUSIVAMENTE la épica `005-coordination-reports` del Frontend de **ITM Service Desk**, sobre la base verificada de `004-attention-lifecycle`.

VERIFICACIÓN DE CONTINUIDAD ANTES DE INICIAR:
Confirma que `004-attention-lifecycle` compila y pasa `./gradlew ktlintCheck commonTest koverVerify`, y que `AttentionDetailRoute(code)` y `StaffRepository` ya están operativos.

DOCUMENTOS DE LECTURA OBLIGATORIA:
1. `frontend/.specify/memory/constitution.md`
2. `frontend/specs/005-coordination-reports/spec.md`
3. `frontend/specs/005-coordination-reports/research.md`
4. `frontend/specs/005-coordination-reports/data-model.md`
5. `frontend/specs/005-coordination-reports/contracts.md`
6. `frontend/specs/005-coordination-reports/plan.md`
7. `frontend/specs/005-coordination-reports/checklist.md`
8. `frontend/specs/005-coordination-reports/tasks.md`

REGLAS INNEGOCIABLES DE IMPLEMENTACIÓN:
- Cero dependencias externas de gráficas: construir `ReportSummarySection` usando componentes nativos Compose Material 3 (tarjetas KPI y barras horizontales proporcionales). PROHIBIDO agregar librerías externas de charts en `libs.versions.toml` o Gradle.
- Validación estricta de fechas en `ReportFilterValidator`:
  - Validar entrada de usuario en formato estricto `dd/mm/aaaa` verificando calendario gregoriano real (ej. rechazar `31/02/2026` o bisiestos inválidos) y convertir a formato ISO `YYYY-MM-DD` para enviar `from` y `to` al API.
  - Exigir que `from <= to` antes de disparar consulta al backend.
- Separación entre `draftFilter` y `appliedFilter` en `CoordinationReportsViewModel`:
  - Editar campos en `ReportFilterPanel` actualiza solo `draftFilter` sin lanzar llamadas HTTP automáticas.
  - Al cargar la pantalla, aplicar filtros o limpiar filtros: ejecutar en paralelo `getSummary(appliedFilter)` y `getRequests(appliedFilter, page = 1, size = 20)` + carga multipágina de `StaffRepository` (`role=PERSONAL_ATENCION`) cuando aplique.
  - Al cambiar de página (`page > 1` o paginación): invocar ÚNICAMENTE `getRequests(appliedFilter, page, size = 20)` sin volver a pedir `getSummary`.
- Control de acceso e integración de navegación:
  - Proteger `CoordinationReportsRoute` estrictamente con `RequireRole(UserRole.COORDINADOR)`.
  - Hacer clic sobre una fila de solicitud del listado navega directamente a `AttentionDetailRoute(code)` de `004-attention-lifecycle`.
- Alcance estricto: implementa únicamente `T5-FE-01` hasta `T5-FE-11` de `tasks.md`. No agregues exportación CSV/PDF ni endpoints no aprobados.

METODOLOGÍA DE TRABAJO:
- Trabaja de forma secuencial siguiendo `T5-FE-01` hasta `T5-FE-11` de `tasks.md`.
- Al terminar, ejecuta obligatoriamente la batería completa de cierre funcional:
  `./gradlew ktlintCheck commonTest koverVerify compileKotlinWasmJs assembleDebug wasmJsBrowserDistribution`
```

---

## Registro de Correcciones Críticas Frente a Desviaciones Comunes de la IA

| Desviación habitual de la IA | Por qué está mal según la Constitución / Spec | Corrección exigida a la IA |
|---|---|---|
| Agregar dependencias de gráficos externos (Vico, MPAndroidChart, KoalaPlot) | Viola la Constitución (dependencias mínimas aprobadas) e introduce incompatibilidades con `wasmJs` | Construir tarjetas KPI y barras horizontales nativas en Compose Material 3 dentro de `ReportSummarySection`. |
| Usar librerías JVM de fechas (`LocalDate.parse` de `java.time`) o regex laxo `\d{2}/\d{2}/\d{4}` sin validar días reales | `java.time` no compila en `wasmJs` y un regex sin calendario deja pasar `"31/02/2026"` | Validar calendario gregoriano real con `kotlinx-datetime` en `ReportFilterValidator` y verificar `from <= to`. |
| Volver a llamar `GET /api/v1/reports/summary` al pasar de página en la tabla | Genera tráfico redundante: las métricas agregadas dependen del filtro aplicado, no de la página actual | Al cambiar de página invocar únicamente `getRequests(appliedFilter, page, size = 20)`. |
| Disparar peticiones HTTP en cada pulsación de tecla del filtro de fechas | Produce llamadas con fechas incompletas (`400 Bad Request`) y condiciones de carrera | Mantener `draftFilter` separado de `appliedFilter` y consultar solo al pulsar Aplicar / Limpiar. |
| Usar cálculo local en cliente para resumir páginas descargadas | La paginación entrega solo `size = 20` registros por vez y produciría KPIs falsos | Consumir las métricas totales directamente desde `GET /api/v1/reports/summary`. |
| Olvidar `RequireRole(UserRole.COORDINADOR)` en `CoordinationReportsRoute` | Expondría la ruta de reportes ejecutivos a otros roles si navegan directamente | Envolver la ruta con `RequireRole(UserRole.COORDINADOR)` según `contracts.md`. |
