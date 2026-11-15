# Feature Spec: 003-service-requests (Portal del Solicitante: Radicación, Mis Solicitudes, Detalle y Cancelación)
**Ubicación:** `specs/003-service-requests/spec.md`  
**Versión de Spec:** 1.0.0  
**Versión Destino:** `v0.2.0`  
**Estado:** Aprobada  

---

## 1. Resumen y Justificación
* **Problema:** Una vez autenticado en el portal, el *Usuario solicitante* requiere pantallas reales en Compose Multiplatform (Web `wasmJs` y Android) para radicar nuevas solicitudes de servicio con validación previa (Tabla 2), asociar opcionalmente una solicitud anterior ya resuelta o cerrada cuando requiera seguimiento (HU-05), consultar su bandeja personal paginada y filtrable por estado (HU-02), inspeccionar el detalle completo con su respuesta final e historial cronológico legible en español (`dd/mm/aaaa hh:mm`), y cancelar con confirmación explícita las solicitudes que sigan en estado `Abierta`.
* **Solución propuesta:** Evolucionar el módulo del rol `SOLICITANTE` sobre **MVVM + UDF + Repository Pattern + Mapper Pattern** consumiendo los endpoints de `003-service-requests` del Backend (`POST /api/v1/requests`, `GET /api/v1/requests/my`, `GET /api/v1/requests/{code}`, `POST /api/v1/requests/{code}/cancel`):
  1. **`MyRequestsScreen`** (reemplaza el placeholder de `ApplicantHome`): listado paginado (`20/pág`) ordenado del más reciente al más antiguo, filtro por los 6 estados en español, acceso rápido a detalle y botón **"Nueva solicitud"**.
  2. **`CreateRequestScreen`**: formulario Material 3 con selector de las 4 categorías, asunto (`5..100`), descripción (`20..2000` con contador), campo opcional `previousRequestCode` (`SOL-AAAA-NNNNN`, pre-cargable desde una solicitud `Resuelta` o `Cerrada`), validación inmediata en cliente y mapeo de errores `400` del servidor (`details[]`).
  3. **`RequestDetailScreen`**: visualización de radicado, estado, categoría, fechas formateadas en `America/Bogota`, solicitud vinculada previa, bloque destacado de respuesta final/fecha de cierre cuando apliquen, línea de tiempo del historial visible (RF-03), acción **"Nueva solicitud relacionada"** en estados `Resuelta`/`Cerrada` y botón **"Cancelar solicitud"** exclusivo para estado `Abierta` con diálogo de confirmación (RNF-09) y motivo opcional (`<= 500`).
* **Fuera de alcance (Out of Scope):**
  - Bandejas de asignación, priorización, intervenciones internas, escalamiento, reasignación y respuesta para *Personal de atención* y *Coordinador* (pertenecen a `004-attention-lifecycle`).
  - Tablero consolidado y métricas de Coordinación (pertenece a `005-coordination-reports`).
  - Adjuntos, notificaciones por correo/WhatsApp, pagos o integraciones externas (prohibido por RNF-10).

---

## 2. Historias de Usuario
* **HU-01 (Radicación de Solicitud en UI — RF-02):** Como *Usuario solicitante*, quiero diligenciar un formulario guiado con categoría, asunto, descripción y código de solicitud anterior opcional, ver validaciones claras antes y después de enviar, y obtener el radicado único `SOL-AAAA-NNNNN` junto con el estado inicial `Abierta`.
* **HU-02 (Mis Solicitudes, Detalle y Cancelación — RF-03 / RF-05 / RNF-09):** Como *Usuario solicitante*, quiero consultar mis solicitudes de 20 en 20 filtrando por estado, abrir cualquier detalle para revisar su avance y cancelar con confirmación aquellas que aún estén en estado `Abierta`.
* **HU-05 (Consulta de Respuesta y Reapertura vía Nueva Solicitud — RF-02 / RF-03):** Como *Usuario solicitante*, quiero ver en el detalle la respuesta final y fecha de cierre de mis solicitudes atendidas, e iniciar desde ahí una nueva solicitud vinculada (`previousRequestCode`) si sigo inconforme, sin modificar la solicitud original.

---

## 3. Criterios de Aceptación (Escenarios BDD / Gherkin)

### Escenario 1: Radicación exitosa de solicitud desde `CreateRequestScreen` (HU-01 / Tabla 2)
* **Given (Dado que):** Un usuario autenticado con rol `SOLICITANTE` abre **Nueva solicitud** (`CreateRequestScreen`).
* **When (Cuando):** Selecciona una categoría válida (`Trámite`, `Orientación`, `Atención administrativa` o `Requerimiento de servicio`), ingresa un asunto (`5..100` caracteres), una descripción (`20..2000` caracteres), opcionalmente deja vacío `previousRequestCode` y pulsa **"Enviar solicitud"**.
* **Then (Entonces):**
  1. `CreateRequestViewModel` valida Tabla 2 localmente mediante `RequestFormValidator` sin enviar campos bloqueados (`code`, `status`, `createdAt`, `requester`), deshabilita temporalmente el botón para evitar doble envío (`isSubmitting = true`) y llama a `ServiceRequestRepository.create(...)`.
  2. Al recibir `HTTP 201 Created`, muestra el código generado `SOL-AAAA-NNNNN` y navega al detalle (`RequestDetail`) o actualiza la bandeja `MyRequests`.

### Escenario 2: Vinculación con solicitud previa válida e inválida (`previousRequestCode`) (HU-01.6 / HU-05)
* **Given (Dado que):** El solicitante diligencia el campo opcional **Código de solicitud anterior** manualmente o llega desde **"Nueva solicitud relacionada"** en una solicitud `Resuelta` o `Cerrada`.
* **When (Cuando):** Ingresa un código con formato incorrecto o envía un código rechazado por el Backend (`400 Bad Request` sobre `previousRequestCode`).
* **Then (Entonces):**
  1. Si no coincide con `^SOL-\d{4}-\d{5,}$`, `RequestFormValidator` muestra error inmediato bajo el campo sin invocar red.
  2. Si el formato es válido pero el Backend responde `400 Bad Request` (no existe, pertenece a otro solicitante o no está `RESUELTA`/`CERRADA`), `ErrorMapper` asigna el mensaje específico al campo `previousRequestCode` conservando los datos escritos.
  3. Si es válido y el Backend responde `201 Created`, muestra la nueva solicitud con `previousRequestCode` enlazado mientras la previa mantiene su estado original.

### Escenario 3: Listado propio paginado (`20/pág`), filtro por estado y manejo de vacío/error (HU-02 / RNF-07 / RNF-09)
* **Given (Dado que):** El `SOLICITANTE` se encuentra en **Mis solicitudes** (`MyRequestsScreen`).
* **When (Cuando):** Visualiza la lista inicial, selecciona un filtro de estado (ej. `Abierta`, `Resuelta`) o pulsa **Siguiente** / **Anterior** en la paginación.
* **Then (Entonces):**
  1. Se ejecuta `GET /api/v1/requests/my?page={n}&size=20` (con `&status=...` cuando haya filtro activo).
  2. Cada tarjeta muestra `code`, `subject`, etiqueta legible de categoría, chip de estado en español, código previo si existe y fecha formateada `dd/mm/aaaa hh:mm` (`America/Bogota`).
  3. Si no hay resultados se muestra estado vacío amigable; si falla la red o hay error transitorio se muestra acción **"Reintentar"**.

### Escenario 4: Detalle de solicitud, respuesta final e historial visible (HU-02 / HU-05 / RF-03)
* **Given (Dado que):** El `SOLICITANTE` selecciona una solicitud en `MyRequestsScreen` navegando a `RequestDetail(code)`.
* **When (Cuando):** Se carga `GET /api/v1/requests/{code}`.
* **Then (Entonces):**
  1. Se muestra la información completa junto con la línea de tiempo cronológica en español (`dd/mm/aaaa hh:mm`), únicamente con eventos visibles devueltos por el servidor (sin notas internas ni motivos de escalamiento).
  2. Si el estado es `RESUELTA` o `CERRADA`, se presenta la sección destacada **"Respuesta oficial"** (`responseText`, `respondedAt`, y `closedAt` si aplica) junto al botón **"Nueva solicitud relacionada"** que abre `CreateRequest` pre-poblando `previousRequestCode`.
  3. Si el Backend responde `404 Not Found` (`"Solicitud no encontrada."`) o `403 Forbidden`, la pantalla muestra el aviso correspondiente en español y un botón para volver a **Mis solicitudes**.

### Escenario 5: Cancelación con diálogo de confirmación y manejo de conflicto `409` (HU-02 / RNF-09)
* **Given (Dado que):** El `SOLICITANTE` visualiza `RequestDetailScreen` de una solicitud suya.
* **When (Cuando):** La solicitud está en estado `ABIERTA` y pulsa **"Cancelar solicitud"**, opcionalmente escribe un motivo (`<= 500` caracteres) y confirma en el diálogo modal (*"¿Estás seguro de cancelar la solicitud {code}?"*).
* **Then (Entonces):**
  1. El botón **"Cancelar solicitud"** solo se habilita/muestra cuando el estado es `ABIERTA`.
  2. Al confirmar, invoca `POST /api/v1/requests/{code}/cancel` y ante `200 OK` actualiza el detalle al estado `Cancelada` mostrando la nueva entrada del historial.
  3. Si el Backend responde `409 Conflict` (p. ej., un coordinador acaba de asignarla en paralelo), se muestra el mensaje devuelto por el servidor y se recarga el detalle real de la solicitud.

### Escenario 6: Guardia RBAC por rol y expiración de sesión JWT (`401` / `403`)
* **Given (Dado que):** Un usuario con rol `PERSONAL_ATENCION` o `COORDINADOR` está autenticado, o la sesión JWT de 1 hora de un `SOLICITANTE` expira/es revocada.
* **When (Cuando):** Se intenta acceder a rutas del módulo de solicitante o el Backend responde `401`/`403`.
* **Then (Entonces):**
  1. `RequireRole(UserRole.SOLICITANTE)` bloquea el acceso de roles internos a `MyRequests`, `CreateRequest` y `RequestDetail`.
  2. Cualquier respuesta `401 Unauthorized` dispara la limpieza automática de `SessionStorage` y redirige a `Login` con aviso de sesión expirada.

---

## 4. Contratos de Pantallas, Rutas y Endpoints Consumidos

### Flujo de Navegación Tipada (`AppNavHost` — Módulo Solicitante)
* `MyRequests` (`ApplicantHome`, protegido con `RequireRole(SOLICITANTE)`)
  * → `CreateRequest(previousRequestCode: String? = null)`
  * → `RequestDetail(code: String)`
    * → `CreateRequest(previousRequestCode = code)` *(cuando estado sea `RESUELTA` o `CERRADA`)*
    * → `RequestDetail(code = previousRequestCode)` *(navegación opcional al enlace previo)*

### Endpoints Backend Consumidos (`003-service-requests`)
| Acción Frontend | Método y Ruta | Uso de JWT | Respuesta Esperada |
| :--- | :--- | :---: | :--- |
| Radicar solicitud | `POST /api/v1/requests` | Sí (`SOLICITANTE`) | `201 Created` (`ServiceRequestDetailDto`) |
| Listar mis solicitudes | `GET /api/v1/requests/my?page&size&status` | Sí (`SOLICITANTE`) | `200 OK` (`PagedServiceRequestsResponseDto`) |
| Consultar detalle e historial | `GET /api/v1/requests/{code}` | Sí (`SOLICITANTE`) | `200 OK` (`ServiceRequestDetailDto`) |
| Cancelar solicitud abierta | `POST /api/v1/requests/{code}/cancel` | Sí (`SOLICITANTE`) | `200 OK` (`ServiceRequestDetailDto`) |

---

## 5. Requisitos No Funcionales (NFRs)
* **Idioma, Fechas y Confirmaciones (RNF-09):** 100% de textos, etiquetas de categoría/estado/acción, contadores y errores en español; marcas temporales formateadas como `dd/mm/aaaa hh:mm` (`America/Bogota`) mediante `kotlinx-datetime`; diálogo de confirmación obligatorio antes de cancelar una solicitud e indicadores de carga anti-doble clic.
* **Responsividad Multiplataforma:** Diseño adaptable Material 3 (`Compact` en móviles Android y formularios/tablas centradas o de ancho máximo legible en Web `wasmJs` `Medium`/`Expanded`).
* **Rendimiento UI y Paginación (RNF-07):** Paginación de `20` ítems por página sin cargar listas masivas en memoria.
* **Calidad y Testing:** Cero errores en `ktlintCheck` (`1.8.x`), pruebas unitarias de validadores, mappers, repositorios con `Ktor MockEngine` y ViewModels con `kotlinx-coroutines-test`, además de pruebas de árbol semántico con `Compose UI Test` alcanzando cobertura global $\ge 90\%$ en **JetBrains Kover**.
