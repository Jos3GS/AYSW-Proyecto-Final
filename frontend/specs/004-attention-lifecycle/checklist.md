# Checklist de Verificación: Ciclo de Atención y Escalamiento (Frontend)

**Feature:** `004-attention-lifecycle`  
**Subproyecto:** Frontend (Kotlin Multiplatform + Compose Multiplatform)  
**Versión:** 1.0.0  
**Fecha:** 2026-11-14  
**Estado:** Aprobado  

---

## 1. Cumplimiento Constitucional y Arquitectónico

- [ ] **C1.1** La arquitectura respeta estrictamente MVVM + UDF + Repository + Mapper sin llamadas Ktor ni lógica de serialización dentro de composables.
- [ ] **C1.2** El grafo de Koin (`AppModules.kt`) registra `AttentionRemoteDataSource`, `AttentionRepository`, `AttentionFormValidator`, `CoordinationPendingViewModel`, `AgentInboxViewModel` y `AttentionDetailViewModel`.
- [ ] **C1.3** Todos los textos visibles, etiquetas, mensajes de error y botones de diálogo están 100% en español.
- [ ] **C1.4** Todas las fechas (`createdAt`, `dueAt`, `respondedAt`, `closedAt`, entradas de historial) se muestran en formato `dd/mm/aaaa hh:mm` en zona horaria `America/Bogota` mediante `DateFormatter` y `AttentionUiMapper`.
- [ ] **C1.5** El proyecto compila sin errores en ambos targets configurados: `wasmJs` (`compileKotlinWasmJs`) y Android (`assembleDebug`).

---

## 2. Contratos API y Serialización Estricta

- [ ] **C2.1** Todos los DTOs en `data/dto/attention/AttentionDtos.kt` usan `@Serializable` compatible con `ignoreUnknownKeys = false`, coincidiendo campo por campo con los contratos de `004-attention-lifecycle` (Backend).
- [ ] **C2.2** `GET /api/v1/attention/coordination/pending` envía `page`, `size=20` y filtro opcional `status` (`ABIERTA` | `ESCALADA`), respetando el orden `created_at ASC` devuelto por el servidor.
- [ ] **C2.3** `GET /api/v1/attention/my-assigned` envía `page`, `size=20` y filtro opcional `status` (`EN_REVISION` | `ESCALADA` | `RESUELTA` | `CERRADA`).
- [ ] **C2.4** `GET /api/v1/attention/requests/{code}` mapea correctamente los campos internos opcionales (`priority`, `assigneeName`, `dueAt`, `isOverdue`, `responseText`, `respondedAt`, `respondedWithinSla`, `closedAt`) e historial con `actorRole` (`SOLICITANTE`, `PERSONAL_ATENCION`, `COORDINADOR`, `SISTEMA`).
- [ ] **C2.5** Los payloads de mutación cumplen exactamente los contratos:
  - `POST /api/v1/attention/requests/{code}/assign` -> `{ assigneeId, priority }`
  - `POST /api/v1/attention/requests/{code}/reassign` -> `{ assigneeId, reason, priority? }`
  - `POST /api/v1/attention/requests/{code}/interventions` -> `{ comment }`
  - `POST /api/v1/attention/requests/{code}/escalate` -> `{ reason }`
  - `POST /api/v1/attention/requests/{code}/respond` -> `{ responseText }`

---

## 3. Navegación, RBAC y Reglas Operativas

- [ ] **C3.1** `CoordinatorHomeRoute` renderiza `CoordinationPendingScreen` bajo `RequireRole(UserRole.COORDINADOR)` y conserva el acceso navegable hacia `StaffListRoute`.
- [ ] **C3.2** `AgentHomeRoute` renderiza `AgentInboxScreen` bajo `RequireRole(UserRole.PERSONAL_ATENCION)`.
- [ ] **C3.3** `AttentionDetailRoute(val code: String)` está protegida por `RequireRole(setOf(UserRole.COORDINADOR, UserRole.PERSONAL_ATENCION))` y bloquea acceso a `SOLICITANTE`.
- [ ] **C3.4** `AttentionDetailViewModel` consulta `StaffRepository` (`GET /api/v1/users/staff?page={n}&size=20&role=PERSONAL_ATENCION`) **únicamente** cuando el usuario es `COORDINADOR` y `status IN {ABIERTA, ESCALADA}`, acumulando todas las páginas hasta `totalPages` si hay más de 20 agentes. Un usuario `PERSONAL_ATENCION` nunca dispara esa petición (`403`).
- [ ] **C3.5** El panel `AttentionActionPanel` muestra exclusivamente acciones válidas según rol y estado:
  - `COORDINADOR` + `ABIERTA`: Asignar (`assigneeId`, `priority`).
  - `COORDINADOR` + `ESCALADA`: Reasignar (`assigneeId`, `reason`, `priority` opcional).
  - `PERSONAL_ATENCION` + `EN_REVISION`: 3 pestañas (`Intervención`, `Escalar`, `Responder`).
  - Estados `RESUELTA` y `CERRADA`: solo lectura para ambos roles (sin formularios de mutación).
- [ ] **C3.6** El indicador `OverdueBadge` (`"Vencida"`) aparece cuando `isOverdue == true` de forma estrictamente informativa y **nunca** deshabilita ni oculta acciones operativas.

---

## 4. Validaciones, Confirmaciones y Concurrencia

- [ ] **C4.1** `AttentionFormValidator` valida exactamente antes de confirmar o enviar:
  - `assigneeId` no vacío y `priority` obligatoria al asignar.
  - `reason` (reasignación / escalamiento): `10..500` caracteres (trim aplicado).
  - `comment` (intervención): `10..1000` caracteres (trim aplicado).
  - `responseText` (respuesta oficial): `20..2000` caracteres (trim aplicado).
- [ ] **C4.2** `Assign`, `Reassign`, `Escalate` y `Respond` muestran `ConfirmationDialog` (RNF-09) vía `PendingConfirmationAction` antes de ejecutar la petición HTTP; `AddIntervention` se envía directamente tras validar.
- [ ] **C4.3** En `EN_REVISION`, cambiar entre pestañas (`AgentActionTab`) conserva intactos los borradores y mensajes de error de cada formulario.
- [ ] **C4.4** Ante cualquier respuesta `409 Conflict` en las 5 mutaciones, el ViewModel cierra `ConfirmationDialog`, muestra el mensaje de error del backend mediante `ErrorMapper` y recarga automáticamente `getInternalDetail(code)`.
- [ ] **C4.5** Cuando `actorRole == SISTEMA`, el historial muestra `"Sistema"` y cuando `respondedWithinSla != null`, muestra `"Respondida dentro del plazo"` o `"Respondida fuera del plazo"`.

---

## 5. Pruebas y Puertas de Calidad

- [ ] **C5.1** Pruebas unitarias en `commonTest` verdes para `AttentionFormValidator`, `AttentionMapper`, `AttentionUiMapper`, `AttentionRepositoryImpl` (con `Ktor MockEngine`), `CoordinationPendingViewModel`, `AgentInboxViewModel` y `AttentionDetailViewModel`.
- [ ] **C5.2** Pruebas de interfaz (`Compose UI Test`) verdes para `CoordinationPendingScreen`, `AgentInboxScreen` y `AttentionDetailScreen` (roles, `OverdueBadge`, pestañas de agente, `ConfirmationDialog`, recarga en `409`).
- [ ] **C5.3** `./gradlew ktlintCheck` pasa con 0 errores de estilo.
- [ ] **C5.4** `./gradlew koverVerify` verifica cobertura $\ge 90\%$ en los módulos cubiertos.
