# Plan y Registro de Pruebas Funcionales — ITM Service Desk

**Sistema:** Sistema de Gestión de Solicitudes de Servicio (`ITM Service Desk`)  
**Colección HTTP para ejecución en vivo:** [pruebas_api.http](pruebas_api.http)  
**Carpeta de evidencias:** [../evidencias/](../evidencias/)

---

## 1. Estrategia de Pruebas

El proyecto cuenta con dos niveles complementarios de verificación:
1. **Pruebas funcionales manuales / HTTP en vivo (`PF-01` a `PF-12`):** Documentan las 12 pruebas mínimas exigidas por la guía PF-01 para su repetición durante la sustentación usando la interfaz Web (`wasmJs`) y la colección [pruebas_api.http](pruebas_api.http).
2. **Suites automatizadas existentes en el repositorio:**
   - **Backend (`backend/src/test/`):** Pruebas unitarias, de rutas Ktor (`testApplication`) y de integración contra PostgreSQL 18 real mediante **Testcontainers** (`ExposedRepositoriesTest.kt`, `AuthRoutingTest.kt`, `StaffRoutingTest.kt`, `ServiceRequestRoutingTest.kt`, `AttentionRoutingTest.kt`, `ReportRoutingTest.kt`, `AutoCloseServiceTest.kt`, entre otras).
   - **Frontend (`frontend/composeApp/src/commonTest/`):** Pruebas de validadores, mappers, repositorios con Ktor `MockEngine`, ViewModels y `Compose UI Test` (`CreateRequestScreenTest.kt`, `RequestDetailScreenTest.kt`, `AgentInboxScreenTest.kt`, `AttentionDetailScreenTest.kt`, `CoordinationPendingScreenTest.kt`, `CoordinationReportsScreenTest.kt`, `RoleGuardAndLogoutTest.kt`).

> **Nota para la sustentación:** Las pruebas `PF-01` a `PF-12` requieren completar la columna **Resultado obtenido / Estado** y adjuntar las capturas en `evidencias/` al ejecutar el ensayo final en vivo antes de sustentar.

---

## 2. Las 12 Pruebas Funcionales Mínimas (Guía PF-01)

### `PF-01` — Registro válido de solicitud
- **Requisito / CA asociado:** `RF-02`, `HU-01`, `CA-01`
- **Método y endpoint:** `POST /api/v1/requests`
- **Condiciones iniciales:** Servidor backend y PostgreSQL 18 activos; usuario `SOLICITANTE` autenticado con JWT válido (`Bearer <token_solicitante>`).
- **Pasos:**
  1. Enviar petición `POST /api/v1/requests` con cabecera `Authorization: Bearer <token_solicitante>` y JSON válido (desde la pantalla `CreateRequestScreen` o [pruebas_api.http](pruebas_api.http)).
  2. Copiar el campo `code` (`SOL-AAAA-NNNNN`) retornado en la respuesta.
  3. Consultar `GET /api/v1/requests/{code}` o revisar la pantalla *"Mis solicitudes"*.
- **Datos de entrada:**
  ```json
  {
    "category": "REQUERIMIENTO_SERVICIO",
    "subject": "Solicitud de acceso al laboratorio de redes",
    "description": "Requiero activación de usuario institucional para prácticas de la asignatura en el bloque C."
  }
  ```
- **Resultado esperado:** Código HTTP `201 Created`; la solicitud se persiste con `status = "ABIERTA"`, código autogenerado `SOL-AAAA-NNNNN`, `createdAt` en `America/Bogota`, y entrada inicial `CREACION` en `request_history`. Al consultarla nuevamente se recupera intacta.
- **Resultado obtenido:** *Pendiente de registro tras ejecución en vivo.*
- **Estado:** `Pendiente de captura en vivo` *(cubierta a nivel automatizado en `ServiceRequestRoutingTest.kt` y `CreateRequestScreenTest.kt`)*.
- **Evidencia:** `evidencias/PF-01_registro_valido.png`

---

### `PF-02` — Registro con datos inválidos (validación en servidor)
- **Requisito / CA asociado:** `RNF-06`, `RF-02` (Tabla 2)
- **Método y endpoint:** `POST /api/v1/requests`
- **Condiciones iniciales:** Usuario `SOLICITANTE` autenticado con JWT válido. Se envía la petición directamente al endpoint HTTP (sin pasar por validaciones de la UI) para comprobar validación del servidor.
- **Pasos:**
  1. Enviar `POST /api/v1/requests` con `category` inexistente, `subject` menor a 5 caracteres y `description` menor a 20 caracteres.
  2. Consultar `GET /api/v1/requests/my` para verificar que no se almacenó ningún registro.
- **Datos de entrada:**
  ```json
  {
    "category": "INVALIDA",
    "subject": "Hola",
    "description": "Muy corta"
  }
  ```
- **Resultado esperado:** Código HTTP `400 Bad Request` con `error = "VALIDATION_ERROR"` y `details[]` indicando errores en `category`, `subject` y `description`. No se guarda nada en PostgreSQL.
- **Resultado obtenido:** *Pendiente de registro tras ejecución en vivo.*
- **Estado:** `Pendiente de captura en vivo` *(cubierta en `RequestValidatorTest.kt` y `ServiceRequestRoutingTest.kt`)*.
- **Evidencia:** `evidencias/PF-02_registro_invalido_400.png`

---

### `PF-03` — Consulta de solicitud existente
- **Requisito / CA asociado:** `RF-03`, `HU-02`, `CA-01`, `CA-03`
- **Método y endpoint:** `GET /api/v1/requests/{code}` (y `GET /api/v1/attention/requests/{code}` para actores internos)
- **Condiciones iniciales:** Existe la solicitud creada en `PF-01` perteneciente al `SOLICITANTE` autenticado.
- **Pasos:**
  1. Enviar `GET /api/v1/requests/{code}` con el JWT del propietario.
- **Datos de entrada:** Parámetro de ruta `code = SOL-2026-00001` (código generado en `PF-01`).
- **Resultado esperado:** Código HTTP `200 OK` devolviendo los datos completos del caso (`id`, `code`, `category`, `subject`, `description`, `status`, `createdAt`, `history`).
- **Resultado obtenido:** *Pendiente de registro tras ejecución en vivo.*
- **Estado:** `Pendiente de captura en vivo` *(cubierta en `ServiceRequestRoutingTest.kt` y `RequestDetailScreenTest.kt`)*.
- **Evidencia:** `evidencias/PF-03_consulta_existente_200.png`

---

### `PF-04` — Consulta de solicitud inexistente
- **Requisito / CA asociado:** `RF-03`, `RNF-01`
- **Método y endpoint:** `GET /api/v1/requests/{code}`
- **Condiciones iniciales:** Usuario `SOLICITANTE` autenticado con JWT válido; el código consultado no existe en la base de datos.
- **Pasos:**
  1. Enviar `GET /api/v1/requests/SOL-2026-99999` con JWT válido.
- **Datos de entrada:** Parámetro de ruta `code = SOL-2026-99999`.
- **Resultado esperado:** Código HTTP `404 Not Found` con estructura `ApiErrorDto` controlada (`error = "NOT_FOUND"`, mensaje `"Solicitud no encontrada."`), sin stacktrace.
- **Resultado obtenido:** *Pendiente de registro tras ejecución en vivo.*
- **Estado:** `Pendiente de captura en vivo` *(cubierta en `ServiceRequestRoutingTest.kt`)*.
- **Evidencia:** `evidencias/PF-04_consulta_inexistente_404.png`

---

### `PF-05` — Gestión por actor autorizado
- **Requisito / CA asociado:** `RF-04`, `HU-03`, `HU-04`, `CA-02`
- **Método y endpoint:**
  - `POST /api/v1/attention/requests/{code}/assign` (`COORDINADOR`)
  - `POST /api/v1/attention/requests/{code}/interventions` (`PERSONAL_ATENCION` asignado)
- **Condiciones iniciales:** Solicitud en estado `ABIERTA`; existen usuarios activos `COORDINADOR` y `PERSONAL_ATENCION`.
- **Pasos:**
  1. Como `COORDINADOR`, enviar `POST /api/v1/attention/requests/{code}/assign` con `assigneeId` del agente y `priority = "ALTA"`.
  2. Como el `PERSONAL_ATENCION` asignado, enviar `POST /api/v1/attention/requests/{code}/interventions` con un comentario válido (`10..1000` chars).
- **Datos de entrada:**
  - Asignación: `{ "assigneeId": "<uuid_agente>", "priority": "ALTA" }`
  - Intervención: `{ "comment": "Se verifica conectividad en el bloque C y se programa activación." }`
- **Resultado esperado:** Ambos endpoints responden `200 OK`. La solicitud pasa a `EN_REVISION` con `firstAssignedAt`, `dueAt` (`+24h`) y quedan registradas las acciones `ASIGNACION` e `INTERVENCION` en `request_history`.
- **Resultado obtenido:** *Pendiente de registro tras ejecución en vivo.*
- **Estado:** `Pendiente de captura en vivo` *(cubierta en `AttentionRoutingTest.kt` y `AttentionDetailScreenTest.kt`)*.
- **Evidencia:** `evidencias/PF-05_gestion_autorizada_200.png`

---

### `PF-06` — Gestión por actor no autorizado
- **Requisito / CA asociado:** `RNF-03`, `RF-04`, `RF-06`, `RF-07`
- **Método y endpoint:**
  - `POST /api/v1/attention/requests/{code}/assign` (intentado sin token y con token `SOLICITANTE` / `PERSONAL_ATENCION`)
  - `POST /api/v1/attention/requests/{code}/interventions` (intentado por otro `PERSONAL_ATENCION` no asignado)
  - `GET /api/v1/reports/summary` (intentado con token `SOLICITANTE`)
- **Condiciones iniciales:** Existen tokens JWT válidos para `SOLICITANTE` y `PERSONAL_ATENCION`.
- **Pasos:**
  1. Enviar `POST /api/v1/attention/requests/{code}/assign` **sin** cabecera `Authorization`.
  2. Enviar la misma petición con el JWT de un `SOLICITANTE`.
  3. Enviar `POST /api/v1/attention/requests/{code}/interventions` con el JWT de un agente distinto al asignado (o con JWT de `SOLICITANTE`).
  4. Enviar `GET /api/v1/reports/summary` con JWT de `SOLICITANTE`.
- **Datos de entrada:** Cabecera omitida (caso 1) o JWT de rol no permitido (`SOLICITANTE` / agente no asignado).
- **Resultado esperado:**
  - Paso 1 (sin JWT): `401 Unauthorized`.
  - Pasos 2, 3 y 4 (rol o propiedad inválida): `403 Forbidden` sin modificar el estado ni el historial en la BD.
- **Resultado obtenido:** *Pendiente de registro tras ejecución en vivo.*
- **Estado:** `Pendiente de captura en vivo` *(cubierta en `AttentionRoutingTest.kt`, `ReportRoutingTest.kt` y `RoleGuardAndLogoutTest.kt`)*.
- **Evidencia:** `evidencias/PF-06_gestion_no_autorizada_401_403.png`

---

### `PF-07` — Consulta de estado durante la atención
- **Requisito / CA asociado:** `RF-03`, `RF-04`, `CA-03`
- **Método y endpoint:** `GET /api/v1/requests/{code}` (`SOLICITANTE`) y `GET /api/v1/attention/requests/{code}` (`COORDINADOR` / `PERSONAL_ATENCION`)
- **Condiciones iniciales:** Solicitud gestionada en `PF-05` (y opcionalmente escalada/reasignada).
- **Pasos:**
  1. Tras asignar en `PF-05`, consultar la solicitud desde el perfil `SOLICITANTE` y desde el perfil `PERSONAL_ATENCION`.
  2. Si se ejecuta escalamiento (`POST .../escalate`), volver a consultar el estado (`ESCALADA`); tras reasignar (`POST .../reassign`), volver a consultar (`EN_REVISION`).
- **Datos de entrada:** `code` de la solicitud gestionada.
- **Resultado esperado:** Código HTTP `200 OK`; el campo `status` y el badge visual de estado en la UI coinciden exactamente con la última transición aprobada (`ABIERTA` → `EN_REVISION` → `ESCALADA` → `EN_REVISION`).
- **Resultado obtenido:** *Pendiente de registro tras ejecución en vivo.*
- **Estado:** `Pendiente de captura en vivo` *(cubierta en `ServiceRequestStateLifecycleTest.kt` y `AttentionRoutingTest.kt`)*.
- **Evidencia:** `evidencias/PF-07_consulta_estado.png`

---

### `PF-08` — Historial de atención y aislamiento entre solicitudes
- **Requisito / CA asociado:** `RF-03`, `RF-04`, `RNF-05`, `CA-04`
- **Método y endpoint:** `GET /api/v1/attention/requests/{code}` (interno) y `GET /api/v1/requests/{code}` (solicitante)
- **Condiciones iniciales:** Existen al menos dos solicitudes (`SOL-A` y `SOL-B`) con acciones registradas en `request_history`.
- **Pasos:**
  1. Consultar `GET /api/v1/attention/requests/{SOL-A}` como `COORDINADOR` o `PERSONAL_ATENCION` asignado.
  2. Consultar `GET /api/v1/requests/{SOL-A}` como `SOLICITANTE` propietario.
- **Datos de entrada:** Códigos `SOL-A` y `SOL-B` distintos.
- **Resultado esperado:** Código HTTP `200 OK`. En la vista interna aparecen todas las acciones cronológicas exclusivas de `SOL-A` (incluyendo `INTERVENCION` y motivos de `ESCALAMIENTO`/`REASIGNACION`) sin mezclarse con `SOL-B`. En la vista del solicitante aparecen los cambios de estado de `SOL-A`, ocultando notas internas (`RF-03`).
- **Resultado obtenido:** *Pendiente de registro tras ejecución en vivo.*
- **Estado:** `Pendiente de captura en vivo` *(cubierta en `ExposedRepositoriesTest.kt` y `AttentionRoutingTest.kt`)*.
- **Evidencia:** `evidencias/PF-08_historial_atencion.png`

---

### `PF-09` — Finalización del caso y consulta de respuesta por solicitante
- **Requisito / CA asociado:** `RF-05`, `HU-05`, `CA-05`
- **Método y endpoint:**
  - `POST /api/v1/attention/requests/{code}/respond` (`PERSONAL_ATENCION` asignado)
  - `GET /api/v1/requests/{code}` (`SOLICITANTE` propietario)
- **Condiciones iniciales:** Solicitud en estado `EN_REVISION` asignada al agente autenticado.
- **Pasos:**
  1. Como `PERSONAL_ATENCION` asignado, enviar `POST /api/v1/attention/requests/{code}/respond` con `responseText` de al menos 20 caracteres.
  2. Iniciar sesión como el `SOLICITANTE` propietario y abrir el detalle de la solicitud (`GET /api/v1/requests/{code}` / `RequestDetailScreen`).
- **Datos de entrada:**
  ```json
  {
    "responseText": "Su cuenta institucional fue habilitada en el laboratorio de redes del bloque C. Puede ingresar con su documento."
  }
  ```
- **Resultado esperado:**
  - `POST .../respond` retorna `200 OK`, cambia estado a `RESUELTA`, fija `respondedAt` y `respondedWithinSla`.
  - El solicitante visualiza el estado `RESUELTA` (o `CERRADA` tras 24h por `AutoCloseService`), la respuesta oficial `responseText` y la fecha de respuesta en la interfaz.
- **Resultado obtenido:** *Pendiente de registro tras ejecución en vivo.*
- **Estado:** `Pendiente de captura en vivo` *(cubierta en `AttentionRoutingTest.kt`, `AutoCloseServiceTest.kt` y `RequestDetailScreenTest.kt`)*.
- **Evidencia:** `evidencias/PF-09_finalizacion_y_respuesta.png`

---

### `PF-10` — Consulta consolidada (Resumen y listado general)
- **Requisito / CA asociado:** `RF-06`, `HU-06`, `RNF-07`, `CA-06`
- **Método y endpoint:** `GET /api/v1/reports/summary` y `GET /api/v1/reports/requests?page=1&size=20`
- **Condiciones iniciales:** Existen múltiples solicitudes en PostgreSQL distribuidas en distintos estados, categorías, prioridades y responsables. Usuario `COORDINADOR` autenticado.
- **Pasos:**
  1. Consultar `GET /api/v1/reports/summary` y `GET /api/v1/reports/requests` sin filtros (desde `CoordinationReportsScreen` o cliente HTTP).
  2. Aplicar filtros combinados (ej. `createdFrom`, `createdTo`, `status`, `category`) y verificar consistencia entre `totalRequests` del resumen y `totalItems` del listado.
- **Datos de entrada:** Consulta global sin parámetros y consulta filtrada (ej. `?status=RESUELTA&page=1&size=20`).
- **Resultado esperado:** Código HTTP `200 OK` en `< 2s`. El resumen consolida `totalRequests`, `byStatus`, `byCategory`, `byPriority`, `unassignedPriorityCount`, `byAssignee`, `unassignedCount`, `overdueCount`, `resolvedWithinSlaCount` y `resolvedOutsideSlaCount`, coincidiendo exactamente con los registros paginados.
- **Resultado obtenido:** *Pendiente de registro tras ejecución en vivo.*
- **Estado:** `Pendiente de captura en vivo` *(cubierta en `ReportRoutingTest.kt`, `ReportServiceTest.kt` y `CoordinationReportsScreenTest.kt`)*.
- **Evidencia:** `evidencias/PF-10_consulta_consolidada.png`

---

### `PF-11` — Persistencia e integridad de relaciones
- **Requisito / CA asociado:** `RNF-05`, `RNF-08`, `RF-02`
- **Método y endpoint:** Flujo completo + reinicio de backend / consulta directa a PostgreSQL y `POST /api/v1/requests` con `previousRequestCode`
- **Condiciones iniciales:** Solicitud previa en estado `RESUELTA` o `CERRADA` del mismo solicitante; instancia de PostgreSQL 18 activa.
- **Pasos:**
  1. Radicar una nueva solicitud referenciando `previousRequestCode` de una solicitud `RESUELTA`/`CERRADA` del mismo solicitante (y probar también referenciar una solicitud de otro usuario o en estado `ABIERTA` esperando `400`).
  2. Reiniciar el servidor backend (`./gradlew run`) sin borrar la base de datos.
  3. Volver a consultar las solicitudes y su historial.
- **Datos de entrada:** `previousRequestCode` válido e inválido.
- **Resultado esperado:** La referencia válida queda persistida con FK `previous_request_id` sin alterar el estado de la solicitud anterior; la referencia ajena o en estado no permitido se rechaza con `400 Bad Request`. Tras reiniciar el backend, todas las solicitudes, usuarios, tokens revocados e historial permanecen intactos en PostgreSQL.
- **Resultado obtenido:** *Pendiente de registro tras ejecución en vivo.*
- **Estado:** `Pendiente de captura en vivo` *(cubierta en `ExposedRepositoriesTest.kt` y `ServiceRequestRoutingTest.kt`)*.
- **Evidencia:** `evidencias/PF-11_persistencia_integridad.png`

---

### `PF-12` — Integración interfaz–API–base de datos (extremo a extremo)
- **Requisito / CA asociado:** `CA-01` a `CA-06`, `RNF-09`
- **Método y endpoint:** Flujo completo en interfaz Web (`wasmJs`) conectada vía Caddy (`https://localhost:8443`) al backend Ktor (`:8080`) y PostgreSQL 18.
- **Condiciones iniciales:** PostgreSQL 18, Backend + Caddy y Frontend Web ejecutándose según el [README.md](../README.md); datos ficticios cargados.
- **Pasos:**
  1. Registrar o iniciar sesión como `SOLICITANTE` en `LoginScreen` y crear una solicitud en `CreateRequestScreen`.
  2. Cerrar sesión e ingresar como `COORDINADOR`: asignar la solicitud en `AttentionDetailScreen` con prioridad `ALTA`.
  3. Cerrar sesión e ingresar como el `PERSONAL_ATENCION` asignado: ver la bandeja (`AgentHomeScreen`), registrar una intervención y emitir la respuesta final.
  4. Regresar como `SOLICITANTE` y verificar estado `RESUELTA`, historial y respuesta final en `RequestDetailScreen`.
  5. Ingresar como `COORDINADOR` y consultar `CoordinationReportsScreen` verificando que el caso aparece en los totales consolidados.
- **Datos de entrada:** Credenciales de prueba ficticias de los 3 actores y datos de solicitud de ejemplo.
- **Resultado esperado:** Todo el flujo funciona sin datos quemados ni simulados desde la interfaz gráfica, persistiendo cada paso en PostgreSQL y reflejando fechas en `dd/mm/aaaa hh:mm` (`America/Bogota`).
- **Resultado obtenido:** *Pendiente de registro tras ejecución en vivo.*
- **Estado:** `Pendiente de captura en vivo` *(plan B: captura/video guardado en `evidencias/`)*.
- **Evidencia:** `evidencias/PF-12_flujo_e2e_completo.mp4` (o capturas por paso en `evidencias/`)
