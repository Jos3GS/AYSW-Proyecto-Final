# Contracts: Ciclo de Vida y Atención de Solicitudes (Frontend)

> **Estado:** Aprobado

## 1. Endpoints API Consumidos (`data/remote/AttentionRemoteDataSource.kt`)

Todos los endpoints requieren cabecera `Authorization: Bearer <accessToken>`.

| Operación | Método y Ruta | Query / Body | Códigos esperados |
|---|---|---|---|
| Pendientes coordinación | `GET /api/v1/attention/coordination/pending` | `page` (default `1`), `size=20`, `status` (`ABIERTA` \| `ESCALADA`, opcional) | `200`, `401`, `403` |
| Bandeja agente | `GET /api/v1/attention/my-assigned` | `page` (default `1`), `size=20`, `status` (`EN_REVISION` \| `ESCALADA` \| `RESUELTA` \| `CERRADA`, opcional) | `200`, `401`, `403` |
| Detalle interno | `GET /api/v1/attention/requests/{code}` | Path `code` | `200`, `401`, `403`, `404` |
| Asignar (Coordinador) | `POST /api/v1/attention/requests/{code}/assign` | `AssignRequestDto` | `200`, `400`, `401`, `403`, `404`, `409` |
| Reasignar (Coordinador) | `POST /api/v1/attention/requests/{code}/reassign` | `ReassignRequestDto` | `200`, `400`, `401`, `403`, `404`, `409` |
| Intervención (Agente) | `POST /api/v1/attention/requests/{code}/interventions` | `AddInterventionRequestDto` | `200`, `400`, `401`, `403`, `404`, `409` |
| Escalar (Agente) | `POST /api/v1/attention/requests/{code}/escalate` | `EscalateRequestDto` | `200`, `400`, `401`, `403`, `404`, `409` |
| Responder (Agente) | `POST /api/v1/attention/requests/{code}/respond` | `RespondRequestDto` | `200`, `400`, `401`, `403`, `404`, `409` |
| Staff asignable (solo Coordinador) | `GET /api/v1/users/staff` | `page` (`1..totalPages`), `size=20`, `role=PERSONAL_ATENCION` | `200`, `401`, `403` |

## 2. DTOs `@Serializable` Estrictos (`data/dto/attention/AttentionDtos.kt`)

Configuración `Json { ignoreUnknownKeys = false; explicitNulls = false }`:

### 2.1 Request DTOs
- `AssignRequestDto(assigneeId: String, priority: String)`
- `ReassignRequestDto(assigneeId: String, reason: String, priority: String? = null)`
- `AddInterventionRequestDto(comment: String)`
- `EscalateRequestDto(reason: String)`
- `RespondRequestDto(responseText: String)`

### 2.2 Response DTOs
- `AttentionRequestSummaryDto`: `code`, `subject`, `category`, `status`, `priority` (nullable), `requesterName`, `assignedAgentId` (nullable), `assignedAgentName` (nullable), `createdAt`, `dueAt` (nullable), `isOverdue`.
- `PagedAttentionRequestsResponseDto`: `items: List<AttentionRequestSummaryDto>`, `page: Int`, `size: Int`, `totalItems: Long`, `totalPages: Int`.
- `InternalRequestHistoryItemDto`: `id`, `eventType`, `actorName` (nullable), `actorRole`, `previousStatus` (nullable), `newStatus` (nullable), `detail` (nullable), `createdAt`.
- `AttentionRequestDetailDto`: `code`, `subject`, `description`, `category`, `status`, `priority` (nullable), `requesterId`, `requesterName`, `assignedAgentId` (nullable), `assignedAgentName` (nullable), `previousRequestCode` (nullable), `responseText` (nullable), `createdAt`, `assignedAt` (nullable), `dueAt` (nullable), `respondedAt` (nullable), `closedAt` (nullable), `isOverdue`, `slaCompliant` (nullable), `history: List<InternalRequestHistoryItemDto>`.

## 3. Contratos de Repositorio (`domain/repository/AttentionRepository.kt`)

```kotlin
interface AttentionRepository {
    suspend fun getCoordinationPending(page: Int, size: Int = 20, status: RequestStatus? = null): AppResult<AttentionRequestsPage>
    suspend fun getMyAssigned(page: Int, size: Int = 20, status: RequestStatus? = null): AppResult<AttentionRequestsPage>
    suspend fun getInternalDetail(code: String): AppResult<AttentionRequestDetail>
    suspend fun assignRequest(code: String, command: AssignRequestCommand): AppResult<AttentionRequestDetail>
    suspend fun reassignRequest(code: String, command: ReassignRequestCommand): AppResult<AttentionRequestDetail>
    suspend fun addIntervention(code: String, command: AddInterventionCommand): AppResult<AttentionRequestDetail>
    suspend fun escalateRequest(code: String, command: EscalateRequestCommand): AppResult<AttentionRequestDetail>
    suspend fun respondRequest(code: String, command: RespondRequestCommand): AppResult<AttentionRequestDetail>
}
```

## 4. Rutas Tipadas y Guardias de Rol (`ui/navigation/Routes.kt`, `AppNavHost.kt`)

| Ruta `@Serializable` | Roles autorizados (`RequireRole`) | Pantalla destino |
|---|---|---|
| `CoordinatorHomeRoute` | `UserRole.COORDINADOR` | `CoordinationPendingScreen` |
| `AgentHomeRoute` | `UserRole.PERSONAL_ATENCION` | `AgentInboxScreen` |
| `AttentionDetailRoute(val code: String)` | `UserRole.COORDINADOR`, `UserRole.PERSONAL_ATENCION` | `AttentionDetailScreen` |

## 5. Manejo de Errores HTTP en Pantalla

- **`401 Unauthorized`**: Propaga `SessionExpired` (cierra sesión local y navega a `LoginRoute`).
- **`403 Forbidden`**: Muestra pantalla/mensaje legible de acceso denegado sin limpiar la sesión.
- **`404 Not Found`**: Muestra estado de solicitud no encontrada con acción de retorno a la bandeja de origen.
- **`409 Conflict`**: Cierra el `ConfirmationDialog` activo, muestra `actionErrorMessage` con el detalle del backend y recarga automáticamente `getInternalDetail(code)`.
