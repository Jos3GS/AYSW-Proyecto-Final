# Research: Ciclo de Vida y Atención de Solicitudes (Frontend)

> **Estado:** Aprobado

## 1. Decisiones Técnicas y Arquitectónicas

### D1. Carga condicional multipágina del personal de atención (`StaffRepository`)
- **Contexto:** Al asignar o reasignar una solicitud desde `AttentionDetailScreen`, el Coordinador necesita elegir un usuario activo con rol `PERSONAL_ATENCION`. Sin embargo, `AttentionDetailScreen` también es usada por el propio `PERSONAL_ATENCION`, quien no tiene autorización (`403 Forbidden`) para consultar `GET /api/v1/users/staff`.
- **Decisión:** Cargar el listado de personal de atención desde `AttentionDetailViewModel` únicamente cuando el rol autenticado sea `COORDINADOR` y el estado actual de la solicitud sea `ABIERTA` o `ESCALADA`. La carga itera `page = 1..totalPages` con `size = 20` para ofrecer la lista completa sin truncation artificial y evita por completo llamadas `403` cuando entra un agente.
- **Alternativa descartada:** Precargar el directorio de staff al iniciar sesión para todos los roles (provocaría `403` en `PERSONAL_ATENCION` y `SOLICITANTE` y mantendría estado obsoleto en memoria).

### D2. Indicador SLA no bloqueante (`OverdueBadge`)
- **Contexto:** HU-03 y HU-05 indican que una solicitud puede superar su plazo SLA (`dueAt`) y quedar marcada con `isOverdue = true`, pero debe seguir siendo operable para asignación, reasignación, escalamiento y respuesta final.
- **Decisión:** Renderizar `OverdueBadge` con etiqueta visible `"Vencida"` y el cálculo textual del cumplimiento SLA (`"Respondida dentro del plazo"` / `"Respondida fuera del plazo"` cuando ya fue respondida), sin deshabilitar ningún botón de acción cuando `isOverdue == true`.
- **Alternativa descartada:** Bloquear botones de atención o exigir un flujo separado de prórroga cuando la solicitud esté vencida.

### D3. Manejo de concurrencia optimista (`409 Conflict`) en mutaciones del ciclo de vida
- **Contexto:** Dos actores pueden operar simultáneamente sobre una solicitud (p. ej., el solicitante cancela una solicitud `ABIERTA` justo antes de que el coordinador la asigne, o un coordinador reasigna mientras un agente intenta responder).
- **Decisión:** Cuando cualquiera de las 5 mutaciones (`assign`, `reassign`, `interventions`, `escalate`, `respond`) devuelva HTTP `409 Conflict`, el ViewModel cierra cualquier diálogo abierto (`pendingConfirmation = null`), conserva el mensaje legible del servidor para feedback en pantalla (`actionErrorMessage`) y dispara inmediatamente una recarga automática con `getInternalDetail(code)` para sincronizar el estado e historial real.
- **Alternativa descartada:** Dejar el diálogo abierto o mostrar únicamente un toast genérico sin recargar la solicitud.

### D4. Aislamiento de borradores por pestaña en `AttentionActionPanel` (`AgentActionTab`)
- **Contexto:** Un miembro de `PERSONAL_ATENCION` en una solicitud `EN_REVISION` puede cambiar entre las pestañas `INTERVENTION`, `ESCALATE` y `RESPOND` mientras redacta una nota o consulta el historial.
- **Decisión:** Mantener tres propiedades separadas en `AttentionDetailUiState` (`interventionCommentDraft`, `escalationReasonDraft`, `responseTextDraft`), de modo que cambiar de pestaña conserve el borrador sin borrar lo escrito y sin contaminar otro formulario. Limpiar únicamente el borrador de la acción completada con éxito.
- **Alternativa descartada:** Usar un único `currentDraft: String` compartido entre pestañas.

### D5. Confirmación explícita (`ConfirmationDialog`) en transiciones irreversibles o de impacto
- **Contexto:** Asignar, reasignar, escalar o enviar la respuesta definitiva cambian estado, SLA/responsable o cierran la atención frente al solicitante; en contraste, registrar una intervención interna (`addIntervention`) no cambia el estado ni cierra el caso.
- **Decisión:** Modelar `PendingConfirmationAction` (`Assign`, `Reassign`, `Escalate`, `Respond`) en `AttentionDetailViewModel` para exigir confirmación modal mediante el componente reutilizable `ConfirmationDialog` antes de ejecutar la llamada HTTP en esas 4 operaciones, permitiendo envío directo en `addIntervention`.
- **Alternativa descartada:** Pedir confirmación también en cada nota interna (fricción innecesaria) o responder/escalar sin confirmación (riesgo de cierre prematuro accidental).

## 2. Riesgos y Mitigaciones

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Selección de prioridad omitida al asignar o desalineada de horas SLA | Confusión visual para el coordinador | Etiquetas explícitas en `AttentionUiMapper` (`"Alta (24 h)"`, `"Media (72 h)"`, `"Baja (120 h)"`) y validación obligatoria en `AttentionFormValidator.validateAssign`. |
| Intentar operar en `ESCALADA` como `PERSONAL_ATENCION` | Error `403`/`409` en runtime | Ocultar o deshabilitar el panel de acciones de agente cuando `status != EN_REVISION` y permitir el panel de asignación/reasignación al `COORDINADOR` en `ABIERTA` y `ESCALADA`. |
| Diferencias de zona horaria al renderizar `createdAt`, `dueAt`, `respondedAt` o entradas del historial | Fechas inconsistentes entre navegador `wasmJs` y Android | Centralizar todo formateo en `DateFormatter` (`America/Bogota`, `dd/mm/aaaa hh:mm`) ya implementado en `001-project-setup`. |
