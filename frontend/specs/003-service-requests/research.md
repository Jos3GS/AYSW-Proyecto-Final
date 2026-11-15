# Investigación Técnica y Spikes: 003-service-requests (Frontend)
**Ubicación:** `specs/003-service-requests/research.md`  
**Spec Asociada:** `specs/003-service-requests/spec.md`  
**Estado:** Concluido (Aprobado)  

---

## 1. Preguntas de Investigación y Objetivos del Spike
1. **Validación portable de Tabla 2 y `previousRequestCode` en `commonMain` (`wasmJs` + Android):** ¿Cómo validar `category`, longitudes `5..100` y `20..2000`, formato `SOL-AAAA-NNNNN` y límite `<= 500` de `reason` de forma idéntica al Backend sin discrepancias entre JVM y `wasmJs`?
2. **Sincronización entre `MyRequestsScreen`, `CreateRequestScreen` y `RequestDetailScreen`:** ¿Cómo reflejar de inmediato una nueva solicitud radicada o una solicitud cancelada al regresar a `MyRequestsScreen` sin mantener cachés globales propensos a desincronización?
3. **Integración de `previousRequestCode` desde un caso `Resuelta`/`Cerrada` y errores `400` del servidor:** ¿Cómo pre-poblar `CreateRequest(previousRequestCode)` de manera tipada en Navigation Compose y combinar validaciones locales con errores de negocio (`400` por inexistencia, propiedad o estado inválido)?
4. **Confirmación de cancelación (`RNF-09`) y resolución de conflicto concurrente (`409 Conflict`):** ¿Cómo diseñar el diálogo de cancelación con motivo opcional (`<= 500`) y actualizar la UI si el equipo de atención asigna la solicitud mientras el diálogo está abierto?
5. **Formateo `dd/mm/aaaa hh:mm` (`America/Bogota`), privacidad del historial (`RF-03`) y responsividad M3:** ¿Cómo presentar las tarjetas paginadas (`20/pág`), los chips de estado/categoría en español, la respuesta oficial y la línea de tiempo tanto en Android (`Compact`) como en Web (`Medium`/`Expanded`)?

---

## 2. Alternativas Evaluadas y Trade-offs

| Decisión / Problema | Alternativas Evaluadas | Ventajas | Desventajas / Riesgos | Alineación con Constitución | Decisión |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Refresco de `MyRequestsScreen` tras crear o cancelar** | **Opción A:** Caché en memoria compartida entre pantallas | Menos peticiones HTTP | Se desincroniza con filtros por estado, paginación y cambios hechos por atención (`409`) | Riesgo de datos obsoletos | Descartada |
| | **Opción B:** Navegar tras `201` al `RequestDetail` recién creado (y refrescar `MyRequestsScreen` con recarga al entrar/volver vía evento o `LaunchedEffect`) | Mantiene la fuente de verdad en el Backend, respeta paginación `20/pág` y filtros activos sin código frágil | Realiza un `GET` al volver a la bandeja | Cumple MVVM / UDF 100% | **Seleccionada** |
| **Navegación hacia `CreateRequest` vinculando solicitud previa** | **Opción A:** Argumento complejo o objeto serializado en la ruta | Pasa todo el detalle | Contamina rutas de navegación y rompe deep-links limpios | Innecesario | Descartada |
| | **Opción B:** Ruta tipada `@Serializable CreateRequestRoute(previousRequestCode: String? = null)` | Navegación limpia, legible en URL/backstack y pre-puebla únicamente el campo `previousRequestCode` editable por el usuario | Ninguno significativo | Cumple Navigation Compose | **Seleccionada** |
| **Presentación de categorías, estados y acciones del historial en español** | **Opción A:** Renderizar strings crudos del Backend (`ATENCION_ADMINISTRATIVA`, `EN_REVISION`) | Cero código de mapeo | Incumple `RNF-09` (UI amigable en español) | Viola RNF-09 | Descartada |
| | **Opción B:** Enums tipados en dominio + **Mapper Pattern** hacia etiquetas en español y tokens visuales Material 3 (colores de estado) | 100% español, tipado seguro y centralizado en `RequestMapper` para reutilizar en `004` y `005` | Requiere fallback seguro si apareciera un valor inesperado | Cumple Mapper + RNF-09 | **Seleccionada** |
| **Confirmación de cancelación y manejo de `409 Conflict`** | **Opción A:** Cancelar directo al pulsar el botón | Un clic menos | Viola explícitamente `RNF-09` (confirmación previa antes de cancelar) | Viola RNF-09 | Descartada |
| | **Opción B:** Mostrar botón solo en estado `ABIERTA`; abrir `CancelRequestDialog` con confirmación + `reason` opcional (`<= 500`); ante `409` mostrar banner de conflicto y recargar `getDetail(code)` | Cumple `RNF-09` y sincroniza la UI con el estado real del servidor si hubo asignación simultánea | Un modal adicional controlado | Cumple RNF-09 / UDF | **Seleccionada** |

---

## 3. Análisis Técnico y Hallazgos

* **Hallazgo 1 — Paridad Multiplataforma de `RequestFormValidator` (`Tabla 2`):**
  * En `commonMain`, aplicar `.trim()` al validar `subject` (`length in 5..100`) y `description` (`length in 20..2000`) replica exactamente el comportamiento del Backend y evita falsos errores por espacios al inicio o al final.
  * Para `previousRequestCode`, cuando el texto normalizado (`trim().uppercase()`) no está vacío, validar con el patrón ASCII `Regex("^SOL-\\d{4}-\\d{5,}$")` (idéntico en JVM y `wasmJs`) previene peticiones innecesarias. Los errores semánticos que solo conoce el servidor (código inexistente, de otro solicitante o en estado distinto de `RESUELTA`/`CERRADA`) llegan como `HTTP 400` (`details[]` con `field = "previousRequestCode"`) y se inyectan directamente en `CreateRequestUiState.previousRequestCodeError`.
  * En creación nunca existen campos ni controles editables para `code`, `status`, `createdAt` o `requester`, cumpliendo `RF-02`.

* **Hallazgo 2 — Flujo de `MyRequestsScreen` (`GET /api/v1/requests/my`) y Reemplazo del Placeholder `002`:**
  * `ApplicantHome` (establecido en `002-auth-users` dentro de `AppNavHost`) pasa a renderizar `MyRequestsScreen`, protegido por `RequireRole(UserRole.SOLICITANTE)`.
  * El selector de filtro ofrece `Todas` + los 6 estados oficiales (`Abierta`, `En revisión`, `Escalada`, `Resuelta`, `Cerrada`, `Cancelada`). Cambiar de filtro reinicia `page = 1` automáticamente y muestra los botones **Anterior** / **Siguiente** junto con `Página X de Y` (`size = 20`).

* **Hallazgo 3 — Visualización de Detalle, Respuesta Oficial e Historial (`RequestDetailScreen`):**
  * La pantalla separa visualmente cuatro bloques responsivos:
    1. **Encabezado:** código `SOL-AAAA-NNNNN`, chip de estado, categoría y fechas de creación/actualización en `dd/mm/aaaa hh:mm` (`America/Bogota`). Si tiene `previousRequestCode`, muestra un enlace interactivo **"Solicitud vinculada: SOL-..."** para consultarla con un toque.
    2. **Descripción del caso:** asunto y texto completo conservando saltos de línea.
    3. **Respuesta oficial y cierre (`RESUELTA`/`CERRADA`):** tarjeta destacada con `responseText`, `respondedAt` y `closedAt` (cuando exista), más botón **"Nueva solicitud relacionada"** que abre `CreateRequestRoute(previousRequestCode = code)`.
    4. **Historial de estados (`RF-03`):** línea de tiempo cronológica con acción legible en español, transición `estado anterior → nuevo estado`, fecha `dd/mm/aaaa hh:mm` y comentario visible cuando no sea nulo (el Backend ya omitió `INTERVENCION` y comentarios internos).

* **Hallazgo 4 — Cancelación Segura con Prevención de Doble Clic y Concurrencia (`RNF-09`):**
  * El botón **"Cancelar solicitud"** solo se renderiza cuando `detail.status == RequestStatus.ABIERTA`.
  * Dentro de `CancelRequestDialog`, el usuario puede introducir opcionalmente un motivo (con contador `0 / 500`) y confirmar; mientras `isCancelling == true`, los botones del diálogo permanecen deshabilitados para impedir doble petición.
  * Ante `409 Conflict`, el `ViewModel` cierra el diálogo, muestra el mensaje de conflicto en un banner visible y vuelve a ejecutar `loadDetail(code)` para actualizar la vista al estado vigente.

* **Hallazgo 5 — Responsividad Web (`wasmJs`) y Android (`Material 3`):**
  * En `MyRequestsScreen`, las pantallas anchas (`width >= 600.dp`) muestran una tabla/lista espaciosa de ancho máximo legible (`widthIn(max = 1040.dp)`) con columnas de Código, Asunto, Categoría, Estado y Fecha; en pantallas compactas Android (`< 600.dp`) muestran tarjetas apiladas Material 3.
  * `CreateRequestScreen` limita el formulario centrado a `widthIn(max = 720.dp)` con contador de caracteres en `subject` y `description`.

---

## 4. Decisión Final Justificada
> Se adopta un módulo `requests` desacoplado bajo **MVVM + UDF + Repository + Mapper**, manteniendo la fuente de verdad en el Backend (refresco explícito sin cachés ocultas), validación portable de Tabla 2 en `commonMain` (`RequestFormValidator`), rutas tipadas `CreateRequestRoute(previousRequestCode?)` y `RequestDetailRoute(code)`, traducción integral al español y zona `America/Bogota` mediante `RequestMapper` + `DateFormatter`, confirmación modal con motivo opcional (`<= 500`) y recarga automática ante `409 Conflict` para cancelaciones, cubriendo todo con `Ktor MockEngine` y `Compose UI Test` ($\ge 90\%$ Kover).
