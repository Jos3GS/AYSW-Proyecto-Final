# Data Model: Ciclo de Vida y Atención de Solicitudes (Frontend)

> **Estado:** Aprobado

## 1. Modelos de Dominio (`domain/model/AttentionModels.kt`)

### 1.1 Enumeraciones
- `RequestPriority`:
  - `ALTA` — Prioridad alta, SLA de 24 horas.
  - `MEDIA` — Prioridad media, SLA de 72 horas.
  - `BAJA` — Prioridad baja, SLA de 120 horas.
- `HistoryActorRole`:
  - `SOLICITANTE`
  - `PERSONAL_ATENCION`
  - `COORDINADOR`
  - `SISTEMA`

### 1.2 Entidades de lectura y paginación
- `AttentionRequestSummary`:
  - `code: String`
  - `subject: String`
  - `category: RequestCategory`
  - `status: RequestStatus`
  - `priority: RequestPriority?`
  - `requesterName: String`
  - `assignedAgentId: String?`
  - `assignedAgentName: String?`
  - `createdAt: Instant`
  - `dueAt: Instant?`
  - `isOverdue: Boolean`
- `AttentionRequestsPage`:
  - `items: List<AttentionRequestSummary>`
  - `page: Int`
  - `size: Int`
  - `totalItems: Long`
  - `totalPages: Int`
- `InternalHistoryEntry`:
  - `id: String`
  - `eventType: String`
  - `actorName: String?`
  - `actorRole: HistoryActorRole`
  - `previousStatus: RequestStatus?`
  - `newStatus: RequestStatus?`
  - `detail: String?`
  - `createdAt: Instant`
- `AttentionRequestDetail`:
  - `code: String`
  - `subject: String`
  - `description: String`
  - `category: RequestCategory`
  - `status: RequestStatus`
  - `priority: RequestPriority?`
  - `requesterId: String`
  - `requesterName: String`
  - `assignedAgentId: String?`
  - `assignedAgentName: String?`
  - `previousRequestCode: String?`
  - `responseText: String?`
  - `createdAt: Instant`
  - `assignedAt: Instant?`
  - `dueAt: Instant?`
  - `respondedAt: Instant?`
  - `closedAt: Instant?`
  - `isOverdue: Boolean`
  - `slaCompliant: Boolean?`
  - `history: List<InternalHistoryEntry>`

### 1.3 Comandos de entrada (`domain/model/AttentionModels.kt`)
- `AssignRequestCommand(assigneeId: String, priority: RequestPriority)`
- `ReassignRequestCommand(assigneeId: String, reason: String, priority: RequestPriority? = null)`
- `AddInterventionCommand(comment: String)`
- `EscalateRequestCommand(reason: String)`
- `RespondRequestCommand(responseText: String)`

## 2. Reglas de Validación (`domain/validation/AttentionFormValidator.kt`)

Todas las cadenas de texto libre deben aplicarse con `.trim()` antes de evaluar longitudes:

| Operación | Campo | Regla obligatoria |
|---|---|---|
| Asignar (`validateAssign`) | `assigneeId` | No vacío (`isNotBlank()`) |
| Asignar (`validateAssign`) | `priority` | No nula (`ALTA`, `MEDIA` o `BAJA`) |
| Reasignar (`validateReassign`) | `assigneeId` | No vacío (`isNotBlank()`) |
| Reasignar (`validateReassign`) | `reason` | Longitud entre `10` y `500` caracteres (inclusive) |
| Reasignar (`validateReassign`) | `priority` | Opcional (`RequestPriority?`); si se envía, sustituye la prioridad |
| Intervención (`validateIntervention`) | `comment` | Longitud entre `10` y `1000` caracteres (inclusive) |
| Escalamiento (`validateEscalate`) | `reason` | Longitud entre `10` y `500` caracteres (inclusive) |
| Respuesta (`validateRespond`) | `responseText` | Longitud entre `20` y `2000` caracteres (inclusive) |

## 3. Reglas de Presentación (`data/mapper/AttentionUiMapper.kt`)

| Concepto | Valor / Condición | Etiqueta visible al usuario |
|---|---|---|
| Prioridad `ALTA` | `RequestPriority.ALTA` | `"Alta (24 h)"` |
| Prioridad `MEDIA` | `RequestPriority.MEDIA` | `"Media (72 h)"` |
| Prioridad `BAJA` | `RequestPriority.BAJA` | `"Baja (120 h)"` |
| Vencimiento activo | `isOverdue == true` | `"Vencida"` |
| Cumplimiento SLA | `slaCompliant == true` | `"Respondida dentro del plazo"` |
| Incumplimiento SLA | `slaCompliant == false` | `"Respondida fuera del plazo"` |
| Actor automático | `actorRole == HistoryActorRole.SISTEMA` o `actorName == null` | `"Sistema"` |
| Fechas y horas | Cualquier `Instant` | Formato `dd/mm/aaaa hh:mm` en zona `America/Bogota` vía `DateFormatter` |

## 4. Estados de UI y Pestañas (`viewmodel/attention/`)

### 4.1 `CoordinationPendingUiState`
- `items: List<AttentionRequestSummary>`
- `selectedStatusFilter: RequestStatus?` (`null` = todas pendientes, `ABIERTA`, `ESCALADA`)
- `currentPage: Int = 1`
- `totalPages: Int = 1`
- `totalItems: Long = 0`
- `isLoading: Boolean = false`
- `errorMessage: String? = null`

### 4.2 `AgentInboxUiState`
- `items: List<AttentionRequestSummary>`
- `selectedStatusFilter: RequestStatus?` (`null` = todas mis asignadas, `EN_REVISION`, `ESCALADA`, `RESUELTA`, `CERRADA`)
- `currentPage: Int = 1`
- `totalPages: Int = 1`
- `totalItems: Long = 0`
- `isLoading: Boolean = false`
- `errorMessage: String? = null`

### 4.3 `AgentActionTab` y `PendingConfirmationAction`
- `enum class AgentActionTab { INTERVENTION, ESCALATE, RESPOND }`
- `sealed interface PendingConfirmationAction`:
  - `data class Assign(val command: AssignRequestCommand, val agentName: String) : PendingConfirmationAction`
  - `data class Reassign(val command: ReassignRequestCommand, val agentName: String) : PendingConfirmationAction`
  - `data class Escalate(val command: EscalateRequestCommand) : PendingConfirmationAction`
  - `data class Respond(val command: RespondRequestCommand) : PendingConfirmationAction`

### 4.4 `AttentionDetailUiState`
- `detail: AttentionRequestDetail? = null`
- `isLoading: Boolean = false`
- `isSubmitting: Boolean = false`
- `loadErrorMessage: String? = null`
- `actionErrorMessage: String? = null`
- `actionSuccessMessage: String? = null`
- `availableAgents: List<StaffUser> = emptyList()`
- `isLoadingAgents: Boolean = false`
- `agentsLoadError: String? = null`
- `selectedAgentId: String = ""`
- `selectedPriority: RequestPriority? = null`
- `reassignmentReasonDraft: String = ""`
- `activeAgentTab: AgentActionTab = AgentActionTab.INTERVENTION`
- `interventionCommentDraft: String = ""`
- `escalationReasonDraft: String = ""`
- `responseTextDraft: String = ""`
- `validationErrors: Map<String, String> = emptyMap()`
- `pendingConfirmation: PendingConfirmationAction? = null`
