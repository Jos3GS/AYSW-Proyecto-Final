---
proyecto: ITM Service Desk
subproyecto: Frontend
feature: 004-attention-lifecycle
categoria: Spec_Kit / Guia_IA
fecha: 2026-11-14
version: 1.0.0
estado: Aprobado
tags:
  - spec-kit
  - frontend
  - guia-ia
  - prompt-maestro
  - attention-lifecycle
  - sla
---

# Guía de Implementación Asistida por IA — 004-attention-lifecycle (Frontend)

> **Estado:** Aprobado

---

## Prompt Maestro para la Sesión de Implementación

```text
Actúa como un Ingeniero Senior especialista en Kotlin Multiplatform (KMP), Compose Multiplatform (Android + WasmJs) y Arquitectura MVVM + UDF.
Tu misión es implementar EXCLUSIVAMENTE la épica `004-attention-lifecycle` del Frontend de **ITM Service Desk** (bandejas e historial interno para `COORDINADOR` y `PERSONAL_ATENCION`), sobre la base verificada de `003-service-requests`.

VERIFICACIÓN DE CONTINUIDAD ANTES DE INICIAR:
Confirma que `003-service-requests` compila y pasa `./gradlew ktlintCheck commonTest koverVerify`, y que el manejo de roles (`RequireRole`), `ConfirmationDialog` y `DateFormatter` están listos.

DOCUMENTOS DE LECTURA OBLIGATORIA:
1. `frontend/.specify/memory/constitution.md`
2. `frontend/specs/004-attention-lifecycle/spec.md`
3. `frontend/specs/004-attention-lifecycle/research.md`
4. `frontend/specs/004-attention-lifecycle/data-model.md`
5. `frontend/specs/004-attention-lifecycle/contracts.md`
6. `frontend/specs/004-attention-lifecycle/plan.md`
7. `frontend/specs/004-attention-lifecycle/checklist.md`
8. `frontend/specs/004-attention-lifecycle/tasks.md`

REGLAS INNEGOCIABLES DE IMPLEMENTACIÓN:
- Carga condicional multipágina del staff (`StaffRepository`):
  - Cargar `GET /api/v1/users/staff?page={n}&size=20&role=PERSONAL_ATENCION` (iterando `1..totalPages`) ÚNICAMENTE cuando el rol autenticado sea `COORDINADOR` y la solicitud esté en `ABIERTA` o `ESCALADA`.
  - PROHIBIDO invocar `/api/v1/users/staff` cuando entra un usuario con rol `PERSONAL_ATENCION` (causaría `403 Forbidden`).
- Validaciones estrictas en `AttentionFormValidator` (aplicando `.trim()` en texto libre):
  - `validateAssign`: `assigneeId` no vacío y `priority` obligatoria (`ALTA`, `MEDIA` o `BAJA`).
  - `validateReassign`: `assigneeId` no vacío, `reason` `10..500` caracteres, `priority` opcional.
  - `validateIntervention`: `comment` `10..1000` caracteres.
  - `validateEscalate`: `reason` `10..500` caracteres.
  - `validateRespond`: `responseText` `20..2000` caracteres.
- Reglas de UI y SLA (`AttentionUiMapper` y `OverdueBadge`):
  - Etiquetas de prioridad obligatorias: `"Alta (24 h)"`, `"Media (72 h)"`, `"Baja (120 h)"`.
  - Badge `"Vencida"` (`isOverdue == true`) estrictamente informativo: NUNCA bloquear botones de asignar, reasignar, intervenir, escalar o responder.
  - Mostrar cumplimiento SLA tras respuesta: `"Respondida dentro del plazo"` o `"Respondida fuera del plazo"`.
  - Actor automático en historial: mostrar `"Sistema"` cuando `actorRole == SISTEMA` o `actorName == null`.
- Aislamiento de borradores y confirmaciones (`AttentionDetailViewModel`):
  - Conservar borradores independientes por pestaña en `AgentActionTab` (`interventionCommentDraft`, `escalationReasonDraft`, `responseTextDraft`).
  - Exigir `ConfirmationDialog` (`PendingConfirmationAction`) para Asignar, Reasignar, Escalar y Responder; permitir envío directo para Registrar Intervención.
  - Ante HTTP `409 Conflict` en cualquier mutación: cerrar diálogo, mostrar error del backend y recargar automáticamente `getInternalDetail(code)`.
- Rutas tipadas protegidas con `RequireRole`: `CoordinatorHomeRoute` (`COORDINADOR`), `AgentHomeRoute` (`PERSONAL_ATENCION`), `AttentionDetailRoute(code)` (`COORDINADOR` o `PERSONAL_ATENCION`).
- Alcance estricto: implementa únicamente las tareas de `004-attention-lifecycle` definidas en `tasks.md`. No implementes aún reportes ni analítica de `005-coordination-reports`.

METODOLOGÍA DE TRABAJO:
- Trabaja de forma secuencial siguiendo todas las tareas definidas en `frontend/specs/004-attention-lifecycle/tasks.md`.
- Al terminar, ejecuta obligatoriamente:
  `./gradlew ktlintCheck commonTest koverVerify compileKotlinWasmJs assembleDebug`
```

---

## Registro de Correcciones Críticas Frente a Desviaciones Comunes de la IA

| Desviación habitual de la IA | Por qué está mal según la Constitución / Spec | Corrección exigida a la IA |
|---|---|---|
| Llamar a `StaffRepository` para todos los roles al abrir `AttentionDetailScreen` | Un usuario `PERSONAL_ATENCION` recibe `403 Forbidden` en `/api/v1/users/staff`, rompiendo o ensuciando la pantalla de detalle | Condicionar la carga multipágina de staff estrictamente a `role == COORDINADOR && status in {ABIERTA, ESCALADA}`. |
| Cargar solo `page = 1` del directorio de personal de atención | Si hay más de 20 agentes registrados, el coordinador no puede asignar a los de páginas siguientes | Iterar `page = 1..totalPages` con `size = 20` acumulando todos los agentes activos disponibles. |
| Bloquear los botones de acción cuando `isOverdue == true` | Contradice HU-03/HU-05: una solicitud vencida sigue siendo operable hasta su respuesta final | Mantener `OverdueBadge` (`"Vencida"`) y textos SLA como meros indicadores visuales no bloqueantes. |
| Compartir un único campo `draftText` entre las pestañas `INTERVENTION`, `ESCALATE` y `RESPOND` | Al cambiar de pestaña en `AttentionActionPanel`, el agente pierde o mezcla borradores distintos | Mantener propiedades separadas (`interventionCommentDraft`, `escalationReasonDraft`, `responseTextDraft`) en `AttentionDetailUiState`. |
| Ejecutar Asignar, Reasignar, Escalar o Responder sin `ConfirmationDialog` | Permite cambios irreversibles de estado o respuestas enviadas por un toque accidental | Modelar `PendingConfirmationAction` (`Assign`, `Reassign`, `Escalate`, `Respond`) y abrir `ConfirmationDialog` antes del `POST`. |
| Dejar diálogo abierto o pantalla congelada ante `409 Conflict` | Un cambio concurrente previo del solicitante o coordinador deja la UI desincronizada | Cerrar `pendingConfirmation`, mostrar el error devuelto y ejecutar automáticamente `getInternalDetail(code)`. |
