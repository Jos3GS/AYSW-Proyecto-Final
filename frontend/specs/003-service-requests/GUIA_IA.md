---
proyecto: ITM Service Desk
subproyecto: Frontend
feature: 003-service-requests
categoria: Spec_Kit / Guia_IA
fecha: 2026-11-14
version: 1.0.0
estado: Aprobado
tags:
  - spec-kit
  - frontend
  - guia-ia
  - prompt-maestro
  - service-requests
---

# Guía de Implementación Asistida por IA — 003-service-requests (Frontend)

> **Estado:** Aprobado

---

## Prompt Maestro para la Sesión de Implementación

```text
Actúa como un Ingeniero Senior especialista en Kotlin Multiplatform (KMP), Compose Multiplatform (Android + WasmJs) y Arquitectura MVVM + UDF.
Tu misión es implementar EXCLUSIVAMENTE la épica `003-service-requests` del Frontend de **ITM Service Desk** (flujo completo del `SOLICITANTE`), sobre la base verificada de `002-auth-users`.

VERIFICACIÓN DE CONTINUIDAD ANTES DE INICIAR:
Confirma que `002-auth-users` compila y pasa `./gradlew ktlintCheck commonTest koverVerify`, y que `RequireRole`, `SessionViewModel`, `DateFormatter` y `ConfirmationDialog` ya están operativos.

DOCUMENTOS DE LECTURA OBLIGATORIA:
1. `frontend/.specify/memory/constitution.md`
2. `frontend/specs/003-service-requests/spec.md`
3. `frontend/specs/003-service-requests/research.md`
4. `frontend/specs/003-service-requests/data-model.md`
5. `frontend/specs/003-service-requests/contracts.md`
6. `frontend/specs/003-service-requests/plan.md`
7. `frontend/specs/003-service-requests/checklist.md`
8. `frontend/specs/003-service-requests/tasks.md`

REGLAS INNEGOCIABLES DE IMPLEMENTACIÓN:
- Modelos y catálogos cerrados:
  - `RequestCategory`: `TRAMITE`, `ORIENTACION`, `ATENCION_ADMINISTRATIVA`, `REQUERIMIENTO_SERVICIO`.
  - `RequestStatus`: mapeo de estados del ciclo de vida con etiquetas legibles en español en `ServiceRequestUiMapper`.
- Validación en `RequestFormValidator`:
  - `subject` (aplicado `.trim()`): longitud `5..100` caracteres.
  - `description` (aplicado `.trim()`): longitud `20..2000` caracteres.
  - `previousRequestCode`: opcional, pero si no está vacío debe cumplir estrictamente el regex `^SOL-\d{4}-\d{5,}$`.
  - `cancelReason` (motivo de cancelación): opcional en UI, longitud máxima `500` caracteres tras `.trim()`.
- Contrato DTO limpio: `CreateServiceRequestDto` solo envía `subject`, `description`, `category` y `previousRequestCode?`. Jamás enviar `code`, `status`, `requesterId` ni `createdAt` desde el cliente.
- Reglas de `MyRequestsScreen`:
  - Paginación fija `size = 20`.
  - Al cambiar el filtro de estado o limpiar filtros, reiniciar SIEMPRE `page = 1`.
- Reglas de `RequestDetailScreen`:
  - Mostrar botón `"Nueva solicitud relacionada"` ÚNICAMENTE cuando el estado sea `RESUELTA` o `CERRADA`, prellenando `previousRequestCode` al navegar hacia `CreateRequestRoute`.
  - Mostrar acción de cancelar (`CancelRequestDialog`, modal conforme a `RNF-09`) ÚNICAMENTE cuando `status == ABIERTA`.
  - Ante HTTP `409 Conflict` al cancelar, cerrar el diálogo modal, mostrar el error del servidor y recargar automáticamente el detalle de la solicitud (`getMyRequestDetail(code)`).
  - La vista pública del solicitante NO debe mostrar ni consumir trazas/notas internas de agentes ni datos privados de otros usuarios.
- Seguridad de rutas: todas las pantallas de esta épica deben quedar protegidas con `RequireRole(UserRole.SOLICITANTE)`.
- Alcance estricto: implementa únicamente `T01..T07` de `003-service-requests`. No implementes aún bandejas de Coordinador/Agente ni reportes.

METODOLOGÍA DE TRABAJO:
- Trabaja de forma secuencial siguiendo `T01` a `T07` de `tasks.md`.
- Al terminar, ejecuta obligatoriamente:
  `./gradlew ktlintCheck commonTest koverVerify compileKotlinWasmJs assembleDebug`
```

---

## Registro de Correcciones Críticas Frente a Desviaciones Comunes de la IA

| Desviación habitual de la IA | Por qué está mal según la Constitución / Spec | Corrección exigida a la IA |
|---|---|---|
| Mantener el `currentPage` anterior al cambiar el filtro de estado en `MyRequestsScreen` | Si el usuario estaba en la página 3 y filtra un estado con 1 sola página, ve una lista vacía errónea o petición inválida | Reiniciar explícitamente `page = 1` cada vez que cambie el filtro de estado. |
| Permitir cancelar una solicitud en estado `EN_REVISION` o sin `CancelRequestDialog` | Viola las reglas de negocio (solo `ABIERTA` es cancelable por el solicitante) y el requisito modal `RNF-09` | Renderizar el botón de cancelar solo cuando `status == ABIERTA` y exigir confirmación en `CancelRequestDialog`. |
| Mostrar el botón `"Nueva solicitud relacionada"` en solicitudes `ABIERTA`, `EN_REVISION` o `CANCELADA` | Solo las solicitudes finalizadas con respuesta (`RESUELTA` o `CERRADA`) pueden ser referenciadas como antecedente | Restringir la visibilidad de `"Nueva solicitud relacionada"` exclusivamente a `status == RESUELTA || status == CERRADA`. |
| Validar `previousRequestCode` solo con `isNotBlank()` o regex laxo | Permite enviar códigos inválidos al backend provocando errores `400` evitables en cliente | Validar con el regex estricto `^SOL-\d{4}-\d{5,}$` en `RequestFormValidator`. |
| Ante un `409 Conflict` al cancelar, dejar el `CancelRequestDialog` abierto sin recargar | El usuario queda frente a estado obsoleto si el coordinador asignó la solicitud segundos antes | Cerrar el diálogo, mostrar el mensaje del backend y recargar automáticamente el detalle (`getMyRequestDetail(code)`). |
| Incluir campos de respuesta o notas internas en el detalle del solicitante | Rompe la separación entre vista pública del solicitante y vista interna (`004-attention-lifecycle`) | Limitar `RequestDetailScreen` a los campos públicos devueltos por `GET /api/v1/requests/my/{code}`. |
