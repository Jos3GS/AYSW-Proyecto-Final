# Feature Spec: 004-attention-lifecycle (Bandejas de Coordinación y Atención, Asignación SLA, Intervenciones, Escalamiento y Respuesta)
**Ubicación en Repositorio:** `frontend/specs/004-attention-lifecycle/spec.md`  
**Versión de Spec:** 1.0.0  
**Versión Destino:** `v0.2.0`  
**Estado:** Aprobada  

---

## 1. Resumen y Justificación
* **Problema:** En `002-auth-users` se definieron los espacios base `CoordinatorHomeRoute` y `AgentHomeRoute`, pero el *Coordinador* aún necesita las pantallas operativas para revisar solicitudes pendientes (`Abierta` y `Escalada`, de la más antigua a la más reciente), asignarlas o reasignarlas a *Personal de atención* con prioridad SLA (`24h`/`72h`/`120h`), y el *Personal de atención* necesita su bandeja de asignaciones (con marca visual `Vencida`), detalle interno completo, registro de intervenciones (`10..1000`), escalamiento (`10..500`) y respuesta oficial inmutable (`20..2000`) con confirmación previa.
* **Solución propuesta:** Evolucionar los módulos de **`COORDINADOR`** y **`PERSONAL_ATENCION`** sobre **MVVM + UDF + Repository Pattern + Mapper Pattern** en Compose Multiplatform 1.12.2 (`wasmJs` + Android), reemplazando los placeholders de `CoordinatorHomeScreen` y `AgentHomeScreen` e integrando los 8 endpoints de `/api/v1/attention/*` junto con la consulta de personal `PERSONAL_ATENCION` (`/api/v1/users/staff?role=PERSONAL_ATENCION`):
  1. **`CoordinationPendingScreen` (`CoordinatorHomeRoute`):** bandeja cronológica (`ABIERTA` + `ESCALADA`, más antiguas primero), paginación `20/pág`, insignia informativa `Vencida` y navegación directa a **Personal interno (`StaffListRoute`)** o al detalle de gestión.
  2. **`AgentInboxScreen` (`AgentHomeRoute`):** bandeja de solicitudes asignadas al agente autenticado con filtros por estado, prioridad, vencimiento `dd/mm/aaaa hh:mm` (`America/Bogota`) e insignia informativa **`Vencida`** (sin bloquear acciones).
  3. **`AttentionDetailScreen` (`AttentionDetailRoute(code)`):** detalle interno compartido por `COORDINADOR` y `PERSONAL_ATENCION` asignado, con historial completo (incluyendo intervenciones internas y eventos del actor `Sistema` para cierre automático), paneles condicionales por rol y estado vigente:
     - **Para `COORDINADOR`:** formulario/diálogo de **Asignar** (`ABIERTA`) o **Reasignar** (`ESCALADA`, con motivo `10..500` y prioridad ajustable sin reinicio SLA), con confirmación `RNF-09`.
     - **Para `PERSONAL_ATENCION` asignado en `EN_REVISION`:** acciones de **Registrar intervención** (`10..1000`), **Escalar solicitud** (`10..500`, con confirmación `RNF-09`) y **Responder solicitud** (`20..2000`, con confirmación modal `RNF-09` de respuesta definitiva e inmutable).
* **Fuera de alcance (Out of Scope):**
  - Tablero consolidado de métricas, filtros gerenciales por fecha/categoría/responsable y listado general de `005-coordination-reports`.
  - Adjuntos, envío de correos, WhatsApp, encuestas, pagos o integraciones externas (prohibido por **RNF-10**).

---

## 2. Historias de Usuario
* **HU-03 (Bandeja de Coordinación, Asignación y Reasignación — RF-04 / RNF-09):** Como *Coordinador*, quiero visualizar primero las solicitudes `Abierta` y `Escalada` de la más antigua a la más reciente, seleccionar un responsable de *Personal de atención* y una prioridad (`Alta`, `Media`, `Baja`) para asignarlas, y reasignar solicitudes escaladas indicando motivo y prioridad ajustable.
* **HU-04 (Bandeja de Atención, Vencimiento, Intervención y Escalamiento — RF-04 / RF-05):** Como *Personal de atención*, quiero consultar mis casos asignados distinguiendo visualmente los vencidos (`Vencida`), registrar notas de intervención interna sin alterar el estado, o escalar con motivo obligatorio para devolver el caso al Coordinador.
* **HU-05 (Respuesta Final Inmutable con Confirmación — RF-05 / RNF-09):** Como *Personal de atención*, quiero registrar y confirmar la respuesta definitiva al solicitante viendo si fue atendida dentro o fuera de plazo, además de visualizar en el historial el eventual cierre automático ejecutado por `Sistema`.

---

## 3. Criterios de Aceptación (Escenarios BDD / Gherkin)

### Escenario 1: Bandeja de pendientes del Coordinador (`ABIERTA` + `ESCALADA` de más antigua a más reciente — HU-03.1)
* **Given (Dado que):** Un usuario autenticado con rol `COORDINADOR` ingresa a su pantalla principal (`CoordinatorHomeRoute`).
* **When (Cuando):** Carga `CoordinationPendingScreen` o filtra por `Todas pendientes` | `Abierta` | `Escalada`.
* **Then (Entonces):**
  1. Invoca `GET /api/v1/attention/coordination/pending?page=1&size=20` manteniendo el orden cronológico ascendente entregado por el Backend (más antiguas primero).
  2. Cada elemento muestra `code`, `subject`, categoría en español, chip de estado (`Abierta` / `Escalada`), solicitante, insignia **`Vencida`** si `isOverdue == true`, y fecha de creación `dd/mm/aaaa hh:mm` (`America/Bogota`).
  3. Mantiene visible el acceso rápido **"Personal interno"** (`StaffListRoute`) ya habilitado en `002`.

### Escenario 2: Asignación inicial (`ABIERTA -> EN_REVISION`) y Reasignación (`ESCALADA -> EN_REVISION`) por Coordinador (HU-03 / RNF-09)
* **Given (Dado que):** El `COORDINADOR` abre `AttentionDetailScreen` de una solicitud `ABIERTA` o `ESCALADA`.
* **When (Cuando):**
  - Si está `ABIERTA`, selecciona un agente de la lista filtrada `role = PERSONAL_ATENCION`, elige prioridad (`Alta (24 h)` | `Media (72 h)` | `Baja (120 h)`) y confirma el diálogo *"¿Confirmas asignar la solicitud {code} a {agentName} con prioridad {priorityLabel}?"*; o
  - Si está `ESCALADA`, selecciona nuevo agente `PERSONAL_ATENCION`, opcionalmente ajusta prioridad, redacta un motivo (`10..500` chars) y confirma el diálogo de reasignación.
* **Then (Entonces):**
  1. Solo se listan usuarios activos con rol `PERSONAL_ATENCION` en el selector.
  2. La UI muestra aviso informativo en reasignación indicando que el vencimiento SLA se recalcula desde la primera asignación.
  3. Tras `200 OK`, se actualiza el detalle reflejando estado `En revisión`, responsable asignado, nueva fecha límite `dueAt` en `dd/mm/aaaa hh:mm` y entrada en el historial completo.

### Escenario 3: Bandeja del Personal de atención y marca informativa `Vencida` no bloqueante (HU-04.1 / HU-04.5)
* **Given (Dado que):** Un usuario con rol `PERSONAL_ATENCION` inicia sesión (`AgentHomeRoute`).
* **When (Cuando):** Visualiza `AgentInboxScreen` (`20/pág`, con filtro por estado `Todas` | `En revisión` | `Escalada` | `Resuelta` | `Cerrada`).
* **Then (Entonces):**
  1. Consume `GET /api/v1/attention/my-assigned?page={n}&size=20`, mostrando prioridad, `dueAt` (`dd/mm/aaaa hh:mm`) e insignia destacada **`Vencida`** cuando `isOverdue == true`.
  2. Al abrir una solicitud vencida en `EN_REVISION`, todas las acciones operativas (**Registrar intervención**, **Escalar**, **Responder**) permanecen habilitadas (el vencimiento es meramente informativo).

### Escenario 4: Registro de intervención interna (`10..1000`) y escalamiento (`10..500`) (HU-04.2 / HU-04.3 / HU-04.4)
* **Given (Dado que):** El `PERSONAL_ATENCION` asignado visualiza una solicitud en estado `EN_REVISION` dentro de `AttentionDetailScreen`.
* **When (Cuando):**
  - Ingresa una nota en **Intervención interna** (`10..1000` caracteres) y pulsa **"Registrar intervención"**; o
  - Ingresa un motivo en **Escalar solicitud** (`10..500` caracteres) y confirma en el diálogo *"¿Deseas escalar la solicitud {code}? Volverá al Coordinador para reasignación."* (`RNF-09`).
* **Then (Entonces):**
  1. `AttentionFormValidator` valida longitudes localmente en `commonMain` y muestra errores por campo si no cumplen, sin hacer llamada HTTP.
  2. Con `200 OK`, la intervención agrega la nota al historial manteniendo estado `En revisión`; el escalamiento actualiza el estado a `Escalada` deshabilitando las acciones de agente.

### Escenario 5: Respuesta definitiva inmutable con confirmación y visualización SLA / Cierre automático (HU-05 / RNF-09)
* **Given (Dado que):** El `PERSONAL_ATENCION` asignado visualiza una solicitud en estado `EN_REVISION`.
* **When (Cuando):** Redacta una respuesta final (`20..2000` caracteres) y confirma el diálogo *"¿Confirmas enviar esta respuesta definitiva para {code}? Una vez enviada no podrá modificarse."*
* **Then (Entonces):**
  1. Ejecuta `POST /api/v1/attention/requests/{code}/respond` evitando doble clic (`isSubmitting = true`).
  2. Con `200 OK`, muestra estado `Resuelta`, el bloque **"Respuesta oficial"** y la etiqueta SLA correspondiente (**"Respondida dentro del plazo"** o **"Respondida fuera del plazo"**).
  3. Cuando una solicitud ya haya sido cerrada automáticamente por el servidor (`CERRADA`), el historial muestra la entrada `Cierre automático` atribuida a `Sistema` junto con `closedAt` en `dd/mm/aaaa hh:mm`.

### Escenario 6: Control de acceso RBAC (`401`/`403`), errores `404` y conflicto concurrente `409`
* **Given (Dado que):** Un `SOLICITANTE` intenta acceder a rutas de atención, un `PERSONAL_ATENCION` consulta una solicitud ajena (`403`), o se produce un conflicto de estado concurrente (`409`).
* **When (Cuando):** Se navega hacia la ruta o se ejecuta la acción.
* **Then (Entonces):**
  1. `RequireRole` bloquea rutas a roles no autorizados; ante `403` o `404` en detalle se presenta vista controlada en español sin exponer datos.
  2. Ante `409 Conflict` (ej. solicitud ya asignada por otro coordinador o escalada/cerrada en paralelo), la UI cierra el modal activo, muestra el mensaje del servidor en un banner visible y recarga automáticamente `getInternalDetail(code)`.
  3. Ante `401 Unauthorized`, se limpia `SessionStorage` y redirige a `LoginRoute`.

---

## 4. Contratos de Pantallas, Rutas y Endpoints Consumidos

### Flujo de Navegación Tipada (`AppNavHost` — Módulo Coordinación y Atención)
* `CoordinatorHomeRoute` → `CoordinationPendingScreen` *(reemplaza placeholder `002`, rol `COORDINADOR`)*
  * → `StaffListRoute` *(existente `002`)*
  * → `AttentionDetailRoute(code: String)`
* `AgentHomeRoute` → `AgentInboxScreen` *(reemplaza placeholder `002`, rol `PERSONAL_ATENCION`)*
  * → `AttentionDetailRoute(code: String)` *(acceso compartido `COORDINADOR` | `PERSONAL_ATENCION`)*

### Endpoints Backend Consumidos (`004-attention-lifecycle` + `002` Staff)
| Acción Frontend | Método y Ruta | Rol Autorizado |
| :--- | :--- | :--- |
| Bandeja pendientes | `GET /api/v1/attention/coordination/pending` | `COORDINADOR` |
| Selector agentes (`role=PERSONAL_ATENCION`) | `GET /api/v1/users/staff?role=PERSONAL_ATENCION` | `COORDINADOR` |
| Asignar solicitud | `POST /api/v1/attention/requests/{code}/assign` | `COORDINADOR` |
| Reasignar solicitud escalada | `POST /api/v1/attention/requests/{code}/reassign` | `COORDINADOR` |
| Bandeja asignadas | `GET /api/v1/attention/my-assigned` | `PERSONAL_ATENCION` |
| Detalle interno completo | `GET /api/v1/attention/requests/{code}` | `COORDINADOR` / `PERSONAL_ATENCION` asignado |
| Registrar intervención | `POST /api/v1/attention/requests/{code}/interventions` | `PERSONAL_ATENCION` asignado |
| Escalar solicitud | `POST /api/v1/attention/requests/{code}/escalate` | `PERSONAL_ATENCION` asignado |
| Registrar respuesta oficial | `POST /api/v1/attention/requests/{code}/respond` | `PERSONAL_ATENCION` asignado |

---

## 5. Requisitos No Funcionales (NFRs)
* **Idioma, Formato Temporal y Confirmaciones (`RNF-09`):** Todos los labels (`Alta (24 h)`, `Media (72 h)`, `Baja (120 h)`, `Vencida`, `Cierre automático`, `Sistema`), mensajes de validación y fechas `dd/mm/aaaa hh:mm` (`America/Bogota`) en español; confirmación modal obligatoria en asignación, reasignación, escalamiento y respuesta definitiva.
* **Responsividad Multiplataforma:** Tablas en Web (`wasmJs` `Medium`/`Expanded`) y tarjetas M3 compactas en Android, además de sección de acciones claramente separada del historial cronológico.
* **Calidad y Testing:** Cero errores en `ktlintCheck` (`1.8.x`), cobertura $\ge 90\%$ en **JetBrains Kover** verificada con pruebas unitarias de validadores/mappers/ViewModels (`kotlinx-coroutines-test`), `Ktor MockEngine` y `Compose UI Test`.
