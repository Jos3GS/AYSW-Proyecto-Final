# Especificación de Requisitos — ITM Service Desk

**Sistema:** Sistema de Gestión de Solicitudes de Servicio (`ITM Service Desk`)  
**Asignatura:** Aplicaciones y Servicios Web — Proyecto Final (Guía PF-01)  
**Versión consolidada:** `1.0.0` (épicas `001` a `005`)

---

## 1. Problema y Propósito

En la institución, la recepción y atención de solicitudes de servicio se realizaba por canales dispersos (correo electrónico, llamadas telefónicas y mensajería instantánea), sin un punto único de radicación, sin control de tiempos de respuesta (SLA) ni historial auditable de las acciones realizadas.

**ITM Service Desk** centraliza el ciclo completo de una solicitud de servicio mediante una aplicación web/móvil conectada a una API RESTful y una base de datos relacional PostgreSQL, garantizando trazabilidad integral desde la radicación hasta la respuesta final y el cierre.

---

## 2. Actores del Sistema

| Actor | Rol en el sistema | Responsabilidades principales |
| :--- | :--- | :--- |
| **Usuario Solicitante** | `SOLICITANTE` | Auto-registrarse, iniciar/cerrar sesión, radicar solicitudes (con vínculo opcional a una solicitud previa `RESUELTA` o `CERRADA`), consultar sus propias solicitudes, ver su historial filtrado y cancelar solicitudes en estado `ABIERTA`. |
| **Personal de Atención** | `PERSONAL_ATENCION` | Consultar la bandeja de solicitudes asignadas a su usuario (con marca informativa de vencimiento SLA), registrar intervenciones internas, escalar casos con motivo obligatorio y emitir la respuesta final inmutable. |
| **Coordinación** | `COORDINADOR` | Consultar pendientes (`ABIERTA` y `ESCALADA`), asignar/reasignar solicitudes con prioridad y SLA, registrar y listar personal interno (`PERSONAL_ATENCION` y `COORDINADOR`), y consultar el resumen consolidado y listado general filtrado. |
| **Sistema (Proceso automático)** | `SISTEMA` | Ejecutar periódicamente el cierre automático (`RESUELTA` → `CERRADA`) cuando transcurren 24 horas desde la emisión de la respuesta (`responded_at`), registrando auditoría sin actor humano. |

---

## 3. Alcance Aprobado y Restricciones (`RNF-10`)

### Dentro del alcance
1. Registro público de solicitantes y autenticación JWT (`HMAC256`, vigencia de 1 hora) con revocación en cierre de sesión.
2. Gestión de personal interno (`PERSONAL_ATENCION` y `COORDINADOR`) exclusivamente por la Coordinación.
3. Radicación de solicitudes con consecutivo anual automático `SOL-AAAA-NNNNN`, consulta paginada, detalle, trazabilidad y cancelación en estado `ABIERTA`.
4. Ciclo completo de atención: asignación, prioridad y SLA (`ALTA = 24h`, `MEDIA = 72h`, `BAJA = 120h`), intervenciones internas ocultas al solicitante, escalamiento, reasignación sin reinicio de reloj SLA, respuesta final inmutable y cierre automático a las 24 horas.
5. Reportes y resumen consolidado para Coordinación con métricas agregadas y filtros combinables por fecha (`America/Bogota`), estado, categoría, prioridad y responsable.

### Fuera del alcance (`RNF-10`)
- Carga o almacenamiento de archivos adjuntos.
- Envío de notificaciones por correo electrónico o mensajería WhatsApp.
- Pasarelas de pago, facturación o integraciones con plataformas BI/externas.
- Exportación binaria a PDF, Excel o CSV.
- Eliminación física de solicitudes o edición/eliminación de cuentas de personal o entradas de historial.

---

## 4. Requisitos Funcionales y Reglas de Negocio

| Código | Historia | Descripción | Reglas clave |
| :--- | :--- | :--- | :--- |
| **RF-01** | **HU-01 / HU-07** | Registro público, autenticación y semilla inicial | El registro público asigna siempre `SOLICITANTE`. Al arrancar, `CoordinatorSeedService` crea de forma idempotente un único `COORDINADOR` semilla desde `.env`. |
| **RF-02** | **HU-01 / HU-05** | Radicación de solicitud y vínculo a caso previo | Genera código `SOL-AAAA-NNNNN` en `America/Bogota` y estado `ABIERTA`. Si envía `previousRequestCode`, debe existir, pertenecer al mismo solicitante y estar en `RESUELTA` o `CERRADA` (sin alterar la solicitud previa). |
| **RF-03** | **HU-02** | Consulta propia, detalle, historial y privacidad | El solicitante solo consulta sus solicitudes. El historial visible para `SOLICITANTE` excluye acciones `INTERVENCION` y oculta (`comment = null`) los motivos de `ESCALAMIENTO` y `REASIGNACION`. |
| **RF-04** | **HU-03 / HU-04** | Bandejas, asignación, intervenciones, escalamiento y reasignación | Coordinación consulta `ABIERTA`/`ESCALADA` ordenadas de más antigua a más reciente (`created_at ASC`). En `ESCALADA` → `EN_REVISION`, `first_assigned_at` nunca se reinicia. Solo el `PERSONAL_ATENCION` asignado puede intervenir o escalar. |
| **RF-05** | **HU-02 / HU-05** | Cancelación, respuesta final inmutable y cierre automático | `SOLICITANTE` propietario cancela solo si está `ABIERTA`. `PERSONAL_ATENCION` asignado responde en `EN_REVISION` (`RESUELTA`, calculando `responded_within_sla`). `SISTEMA` cierra a las `24h` (`RESUELTA` → `CERRADA`). |
| **RF-06** | **HU-06** | Resumen consolidado y listado general para Coordinación | Totales por estado, categoría, prioridad, responsable, vencidas activas y dentro/fuera de SLA, con filtros combinables (`createdFrom`, `createdTo`, `status`, `category`, `priority`, `assignedToId`) y listado paginado consistente. |
| **RF-07** | **HU-07** | Gestión de personal interno | Solo `COORDINADOR` puede crear (`PERSONAL_ATENCION` o `COORDINADOR`) y listar personal interno paginado (`GET /api/v1/users/staff`). |

### Reglas de Validación en Servidor

#### Tabla 1 — Usuarios y Personal (`UserValidator`)
- `fullName`: entre 3 y 100 caracteres; solo letras Unicode y espacios.
- `documentNumber`: entre 5 y 15 dígitos numéricos (único).
- `email`: formato válido, máximo 100 caracteres, sin `..`, normalizado a minúsculas (único).
- `phone`: entre 7 y 15 dígitos numéricos.
- `password`: mínimo 8 caracteres, al menos 1 letra y 1 dígito, y máximo 72 bytes UTF-8 (límite de BCrypt).
- `role` (solo en `/api/v1/users/staff`): `PERSONAL_ATENCION` o `COORDINADOR`.

#### Tabla 2 — Solicitudes y Atención (`RequestValidator` / `AttentionValidator`)
- `category`: `TRAMITE`, `ORIENTACION`, `ATENCION_ADMINISTRATIVA` o `REQUERIMIENTO_SERVICIO`.
- `subject`: entre 5 y 100 caracteres.
- `description`: entre 20 y 2000 caracteres.
- `previousRequestCode` (opcional): patrón `^SOL-\d{4}-\d{5,}$`.
- `reason` en cancelación (opcional): máximo 500 caracteres.
- `priority` (asignación/reasignación): `ALTA` (SLA `24h`), `MEDIA` (SLA `72h`) o `BAJA` (SLA `120h`).
- `comment` en intervención interna: entre 10 y 1000 caracteres.
- `reason` en escalamiento / reasignación: entre 10 y 500 caracteres.
- `responseText` en respuesta final: entre 20 y 2000 caracteres.

---

## 5. Requisitos No Funcionales (`RNF-01` a `RNF-10`)

| Código | Categoría | Requisito |
| :--- | :--- | :--- |
| **RNF-01** | Calidad y contratos | Contratos JSON tipados (`ignoreUnknownKeys = false`), estilo `ktlint 1.8.0` sin errores y cobertura de pruebas $\ge 90\%$ con Kover. |
| **RNF-02** | Autenticación | Contraseñas derivadas con BCrypt (`costo 12`, salt aleatorio en servidor); JWT `HMAC256` con expiración de `1h` (`3600s`) y `jti` único; blacklist en `revoked_tokens`. |
| **RNF-03** | Autorización (RBAC) | Validación estricta de rol y propiedad del recurso en el servidor (`401` sin sesión/token revocado, `403` rol o propietario inválido). |
| **RNF-04** | Seguridad perimetral | Terminación TLS con Caddy, `CORS_ALLOWED_ORIGINS` explícito (prohibido `*`), `IpAllowListPlugin`, secretos en `.env` fuera de Git y cero stacktraces o credenciales en logs/respuestas. |
| **RNF-05** | Atomicidad e inmutabilidad | Toda creación o transición ejecuta `UPDATE` condicional y `INSERT` append-only en `request_history` dentro de la misma transacción SQL; `409 Conflict` ante concurrencia. |
| **RNF-06** | Validación integral | Todas las entradas se validan en el servidor (aunque la petición no venga de la UI), devolviendo `400 Bad Request` con `details[]` por campo. |
| **RNF-07** | Rendimiento y paginación | Listados acotados (`size` entre `1` y `20`, por defecto `20`) e índices PostgreSQL para responder en `< 2s` con `1.000+` solicitudes. |
| **RNF-08** | Disponibilidad y BD | Pool HikariCP + migraciones versionadas Flyway (`V1` a `V5`); ante indisponibilidad de BD responde `503 Service Unavailable`. |
| **RNF-09** | Zona horaria y UI | Zona horaria institucional `America/Bogota` (`-05:00`), formato visual `dd/mm/aaaa hh:mm`, textos 100 % en español y diálogos de confirmación en acciones críticas. |
| **RNF-10** | Restricción de alcance | Sin adjuntos, correo, WhatsApp, pagos, facturación ni integraciones BI externas. |

---

## 6. Condiciones de Aceptación Obligatorias (`CA-01` a `CA-06`)

| ID | Condición de aceptación | Cómo se verifica en la aplicación |
| :--- | :--- | :--- |
| **CA-01** | Un usuario presenta una solicitud y esta queda guardada en la base de datos. Se puede volver a consultar. | Un `SOLICITANTE` se registra/inicia sesión, crea la solicitud (`POST /api/v1/requests`), obtiene código `SOL-AAAA-NNNNN` y la consulta en *"Mis solicitudes"* y su detalle (`GET /api/v1/requests/my`, `GET /api/v1/requests/{code}`). |
| **CA-02** | Un miembro del personal autorizado evalúa y gestiona la solicitud. La acción queda registrada. | El `COORDINADOR` asigna la solicitud con prioridad (`POST .../assign`), y el `PERSONAL_ATENCION` asignado registra intervenciones (`POST .../interventions`), opcionalmente escala (`POST .../escalate`) o responde (`POST .../respond`). |
| **CA-03** | Se consulta el estado de la solicitud durante su atención y el estado coincide con las acciones realizadas. | Tanto el solicitante como el personal/coordinación ven el estado actualizado en tiempo real (`ABIERTA` → `EN_REVISION` → `ESCALADA` → `EN_REVISION` → `RESUELTA` → `CERRADA`). |
| **CA-04** | Se consulta el historial de intervenciones asociadas a esa solicitud (trazabilidad). | En `AttentionDetailScreen` (`COORDINADOR` y `PERSONAL_ATENCION` asignado) se visualiza el historial cronológico completo con actor, rol, fecha/hora, estado anterior/nuevo e intervenciones; en `RequestDetailScreen` (`SOLICITANTE`) se visualiza la trazabilidad de estados filtrando notas internas (`RF-03`). |
| **CA-05** | El solicitante consulta la respuesta y ve que su caso está finalizado. | El `SOLICITANTE` abre su solicitud en estado `RESUELTA` o `CERRADA`, visualiza el bloque de respuesta final (`responseText`, `respondedAt`, y `closedAt` si ya cerró) y confirma que el caso finalizó. |
| **CA-06** | La coordinación consulta un resumen o reporte que integra varias solicitudes. | El `COORDINADOR` abre *"Reportes y consolidado"* (`CoordinationReportsScreen` → `GET /api/v1/reports/summary` y `GET /api/v1/reports/requests`) y observa totales agregados, cumplimiento SLA, desglose por estado/categoría/prioridad/responsable y filtros combinables sobre múltiples solicitudes. |
