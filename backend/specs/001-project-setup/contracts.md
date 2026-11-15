# Contratos de APIs e Interfaces: 001-project-setup (Backend)
**Ubicación:** `specs/001-project-setup/contracts.md`  
**Spec Asociada:** `specs/001-project-setup/spec.md`  
**Protocolo:** REST / JSON (`application/json`, UTF-8, zona `America/Bogota` `-05:00`)  
**Estado:** Aprobado  

---

## 1. Endpoints de Infraestructura Base

### `GET /api/v1/health`
* **Descripción:** Verifica la disponibilidad del servidor Ktor y la conectividad real contra PostgreSQL 18 (`SELECT 1` sobre el pool HikariCP).
* **Autenticación:** Pública (exento de JWT para monitoreo y verificación inicial del Frontend).
* **Seguridad Perimetral:** Sujeto a `ALLOWED_CLIENT_IPS` (IPs exactas o CIDR tras `XForwardedHeaders` confiable de Caddy) y cabeceras `CORS` (`CORS_ALLOWED_ORIGINS`, sin `*`).
* **Headers de Respuesta:**
  * `Content-Type`: `application/json; charset=UTF-8`
  * `Access-Control-Allow-Origin`: `<origen autorizado si coincide con CORS_ALLOWED_ORIGINS>`

#### Response `200 OK`
```json
{
  "status": "UP",
  "service": "itm-service-desk-backend",
  "database": "UP",
  "timestamp": "2026-11-14T11:24:00-05:00"
}
```

#### Response `503 Service Unavailable` (BD inaccesible / timeout HikariCP)
```json
{
  "timestamp": "2026-11-14T11:24:00-05:00",
  "status": 503,
  "error": "SERVICE_UNAVAILABLE",
  "message": "El servicio de base de datos no se encuentra disponible temporalmente.",
  "path": "/api/v1/health",
  "details": []
}
```

---

## 2. Contrato Transversal de Respuestas de Error (`StatusPages`)
Todas las excepciones controladas y no controladas en la API devolverán el esquema unificado `ApiErrorDto` (RNF-01, RNF-04, RNF-08), sin exponer stacktraces, consultas SQL ni datos sensibles:

```json
{
  "timestamp": "2026-11-14T11:24:00-05:00",
  "status": 400,
  "error": "VALIDATION_ERROR",
  "message": "Resumen legible en español del error.",
  "path": "/api/v1/recurso",
  "details": [
    {
      "field": "nombreCampo",
      "message": "Descripción específica para mostrar junto al campo."
    }
  ]
}
```

### Matriz de Códigos HTTP Soportados desde `001-project-setup`

| Código HTTP | `error` | Cuándo aplica |
| :---: | :--- | :--- |
| **`400 Bad Request`** | `VALIDATION_ERROR` / `MALFORMED_REQUEST` | JSON inválido, tipos incompatibles o errores de validación de campos (`details` poblado). |
| **`401 Unauthorized`** | `UNAUTHORIZED` | Token JWT ausente, inválido o expirado (> 8h) en rutas protegidas. |
| **`403 Forbidden`** | `FORBIDDEN` | IP bloqueada por `ALLOWED_CLIENT_IPS`, rol sin permiso o acceso a recurso ajeno. |
| **`404 Not Found`** | `NOT_FOUND` | Endpoint inexistente o recurso solicitado no encontrado. |
| **`409 Conflict`** | `CONFLICT` | Violación de unicidad o transición inválida de máquina de estados. |
| **`500 Internal Server Error`** | `INTERNAL_ERROR` | Error inesperado del servidor (mensaje genérico; detalle solo en log interno sin secretos). |
| **`503 Service Unavailable`** | `SERVICE_UNAVAILABLE` | Falla o timeout de conectividad con PostgreSQL 18 / pool HikariCP. |

---

## 3. Política Perimetral CORS e IPs
* **Orígenes CORS:** Exclusivamente los configurados en `CORS_ALLOWED_ORIGINS` (coma-separados, prohibido `*`).
* **Métodos CORS permitidos:** `GET`, `POST`, `PATCH`, `PUT`, `DELETE` (y preflight `OPTIONS`).
* **Cabeceras CORS permitidas:** `Authorization`, `Content-Type`.
* **Control de IPs (`ALLOWED_CLIENT_IPS`):** Evaluado sobre la IP cliente resuelta vía `XForwardedHeaders` (confiando solo en el proxy Caddy local) contra IPs exactas o subredes CIDR (por defecto `127.0.0.1,::1`). Cualquier IP fuera de la lista recibe `403 Forbidden` con esquema `ApiErrorDto`.

---

## 4. Eventos Publicados / Consumidos
* No aplica en `001-project-setup` (la comunicación entre Frontend y Backend es síncrona REST/JSON sobre HTTP/HTTPS; no se utilizan colas ni brokers de mensajería).
