# Matriz de Trazabilidad y Contribuciones — ITM Service Desk

**Sistema:** Sistema de Gestión de Solicitudes de Servicio (`ITM Service Desk`)  
**Repositorio:** `https://github.com/Jos3GS/AYSW-Proyecto-Final`

---

## 1. Matriz de Trazabilidad (CA ↔ Requisitos ↔ Implementación ↔ PR ↔ Pruebas)

| CA | Requisito / HU | Implementación Backend | Implementación Frontend | PRs asociados | Pruebas (`PF-*` y automatizadas) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **CA-01** *(Presentar solicitud, guardar en BD y volver a consultar)* | `HU-01`, `RF-01`, `RF-02`, `RF-03`, `RNF-02`, `RNF-05`, `RNF-06` | `AuthRouting.kt`, `AuthService.kt`, `ServiceRequestRouting.kt`, `ServiceRequestService.kt`, `ServiceRequestBuilder.kt`, `RequestValidator.kt`, `ExposedServiceRequestRepository.kt`, `V1`..`V3` | `LoginScreen.kt`, `RegisterScreen.kt`, `ApplicantHomeScreen.kt`, `MyRequestsScreen.kt`, `CreateRequestScreen.kt`, `RequestDetailScreen.kt`, `CreateRequestViewModel.kt`, `MyRequestsViewModel.kt`, `RequestDetailViewModel.kt` | `#1`, `#2`, `#3`, `#4`, `#5`, `#6` | `PF-01`, `PF-02`, `PF-03`, `PF-04`, `PF-11`, `AuthRoutingTest.kt`, `ServiceRequestRoutingTest.kt`, `CreateRequestScreenTest.kt`, `MyRequestsScreenTest.kt` |
| **CA-02** *(Personal autorizado evalúa y gestiona; acción queda registrada)* | `HU-03`, `HU-04`, `HU-07`, `RF-04`, `RF-07`, `RNF-03`, `RNF-05` | `StaffRouting.kt`, `StaffService.kt`, `AttentionRouting.kt`, `AttentionService.kt`, `ServiceRequestState.kt`, `AttentionValidator.kt`, `ExposedAttentionRepository.kt` | `StaffListScreen.kt`, `CreateStaffScreen.kt`, `CoordinatorHomeScreen.kt`, `CoordinationPendingScreen.kt`, `AgentHomeScreen.kt`, `AgentInboxScreen.kt`, `AttentionDetailScreen.kt`, `AttentionDetailViewModel.kt` | `#3`, `#4`, `#7`, `#8`, `#9` | `PF-05`, `PF-06`, `StaffRoutingTest.kt`, `AttentionRoutingTest.kt`, `AttentionValidatorTest.kt`, `CoordinationPendingScreenTest.kt`, `AgentInboxScreenTest.kt`, `AttentionDetailScreenTest.kt` |
| **CA-03** *(Consultar estado durante atención y que coincida con las acciones)* | `HU-02`, `HU-03`, `HU-04`, `RF-03`, `RF-04`, `RNF-05` | `ServiceRequestState.kt`, `ServiceRequestRouting.kt` (`GET /my`, `GET /{code}`, `POST /{code}/cancel`), `AttentionRouting.kt` (`assign`, `escalate`, `reassign`, `respond`) | `RequestStatusChip.kt`, `OverdueBadge.kt`, `RequestDetailScreen.kt`, `MyRequestsScreen.kt`, `AttentionDetailScreen.kt`, `AgentInboxScreen.kt` | `#5`, `#6`, `#7`, `#8`, `#9` | `PF-07`, `ServiceRequestStateLifecycleTest.kt`, `ServiceRequestStateTest.kt`, `AttentionRoutingTest.kt` |
| **CA-04** *(Consultar historial de intervenciones / trazabilidad)* | `HU-02`, `HU-04`, `RF-03`, `RF-04`, `RNF-05` | `RequestHistoryTable.kt`, `ApplicantVisibleHistoryEntry.kt`, `ExposedServiceRequestRepository.kt` (filtro privacidad `RF-03`), `ExposedAttentionRepository.kt` (historial completo) | `RequestDetailScreen.kt` (historial filtrado del solicitante), `AttentionDetailScreen.kt` (historial completo interno incluyendo `INTERVENCION`, `ESCALAMIENTO`, `REASIGNACION`, `SISTEMA`) | `#5`, `#6`, `#7`, `#8`, `#9` | `PF-08`, `ExposedRepositoriesTest.kt`, `ServiceRequestRoutingTest.kt`, `AttentionRoutingTest.kt`, `RequestDetailScreenTest.kt`, `AttentionDetailScreenTest.kt` |
| **CA-05** *(Solicitante consulta la respuesta y ve el caso finalizado)* | `HU-05`, `RF-02`, `RF-03`, `RF-05`, `RNF-05`, `RNF-09` | `AttentionRouting.kt` (`POST .../respond`), `AutoCloseService.kt`, `AutoCloseScheduler.kt`, `V4__add_autoclose_partial_index.sql`, `ServiceRequestRouting.kt` (`GET /{code}`, `previousRequestCode`) | `AttentionDetailScreen.kt` (emisión respuesta final), `RequestDetailScreen.kt` (bloque respuesta + cierre + botón crear solicitud relacionada), `CreateRequestScreen.kt` (`previousRequestCode`) | `#5`, `#6`, `#7`, `#8`, `#9` | `PF-09`, `PF-11`, `PF-12`, `AutoCloseServiceTest.kt`, `AutoCloseSchedulerTest.kt`, `AttentionRoutingTest.kt`, `RequestDetailScreenTest.kt` |
| **CA-06** *(Coordinación consulta resumen o reporte integrando varias solicitudes)* | `HU-06`, `RF-06`, `RNF-03`, `RNF-06`, `RNF-07`, `RNF-10` | `ReportRouting.kt`, `ReportService.kt`, `ReportFilterBuilder.kt`, `ExposedReportRepository.kt`, `V5__add_coordination_reports_indexes.sql` | `CoordinationReportsScreen.kt`, `CoordinationReportsViewModel.kt`, `ReportFilterPanel.kt`, `ReportSummarySection.kt`, `ReportFilterValidator.kt`, `ReportRepositoryImpl.kt` | `#10`, `#11` | `PF-10`, `PF-12`, `ReportRoutingTest.kt`, `ReportServiceTest.kt`, `ReportFilterBuilderTest.kt`, `CoordinationReportsScreenTest.kt` |

---

## 2. Trazabilidad Transversal (Infraestructura y Calidad)

| Área | Requisitos | Implementación | PRs | Pruebas / Verificación |
| :--- | :--- | :--- | :--- | :--- |
| Arranque, BD, Migraciones, Semilla y Salud | `RF-01`, `RNF-08`, `RNF-09` | `Application.kt`, `EnvConfig.kt`, `AppConfigBuilder.kt`, `DatabaseFactory.kt`, `CoordinatorSeedService.kt`, `HealthRouting.kt` | `#1`, `#2` | `AppConfigBuilderTest.kt`, `EnvConfigTest.kt`, `CoordinatorSeedServiceTest.kt`, `HealthRoutingTest.kt` |
| Seguridad Perimetral, CORS, Caddy TLS y Errores | `RNF-01`, `RNF-03`, `RNF-04` | `SecurityPlugins.kt`, `IpAllowListPlugin.kt`, `StatusPagesConfig.kt`, `JwtAuthConfig.kt`, `RoleAuthorizationPlugin.kt`, `Caddyfile`, `CaddyBootstrapper.kt` | `#1`, `#3`, `#5` (+ commit pendiente `CaddyBootstrapper`) | `IpAllowListTest.kt`, `JwtTokenServiceTest.kt`, `BcryptPasswordHasherTest.kt`, `CaddyBootstrapperTest.kt`, `HttpClientAuthTest.kt` |

---

## 3. Historial de Pull Requests y Contribuciones (GitHub)

Resumen extraído directamente del historial de merges en la rama `main`:

| PR | Fecha | Rama origen | Componente / Épica | Autor(es) de código en rama | Integrado por |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **#1** | 2026-11-14 | `BE/Feat/001-Project-Setup` | Backend `001-project-setup`: Ktor Netty, HikariCP, Flyway `V1`, `EnvConfig`, `CoordinatorSeedService`, `/health`, Testcontainers | `Jos3GS` | `José Gutiérrez` (`Jos3GS`) |
| **#2** | 2026-11-14 | `FE/Feat/001-Project-Setup` | Frontend `001-project-setup`: KMP + Compose Material 3, Koin, Ktor Client, `SessionStorage`, `StartupScreen` | `KevinStiven04` | `José Gutiérrez` (`Jos3GS`) |
| **#3** | 2026-11-15 | `BE/Feat/002-auth-users` | Backend `002-auth-users`: `register`, `login`, `me`, `logout` (blacklist `jti`), BCrypt 12, RBAC, `/users/staff`, `V2` | `KevinStiven04` (impl.) / `Jos3GS` (specs) | `José Gutiérrez` (`Jos3GS`) |
| **#4** | 2026-11-15 | `FE/Feat/002-auth-users` | Frontend `002-auth-users`: `LoginScreen`, `RegisterScreen`, `StaffListScreen`, `CreateStaffScreen`, `RequireRole`, Kover 96 % | `Jos3GS` | `José Gutiérrez` (`Jos3GS`) |
| **#5** | 2026-11-15 | `BE/Feat/003-service-requests` | Backend `003-service-requests`: `service_requests`, `request_history`, `SOL-AAAA-NNNNN`, State/Builder, Caddy HTTPS | `Jos3GS` | `José Gutiérrez` (`Jos3GS`) |
| **#6** | 2026-11-15 | `FE/Feat/003-service-requests` | Frontend `003-service-requests`: `CreateRequestScreen`, `MyRequestsScreen`, `RequestDetailScreen`, cancelación y `previousRequestCode` | `KevinStiven04` | `José Gutiérrez` (`Jos3GS`) |
| **#7** | 2026-11-15 | `FE/Feat/004-attention-lifecycle` | Frontend `004-attention-lifecycle`: `CoordinatorHomeScreen`, `AgentHomeScreen`, `AttentionDetailScreen`, SLA/vencidas | `Jos3GS` | `José Gutiérrez` (`Jos3GS`) |
| **#8** | 2026-11-15 | `BE/Feat/004-attention-lifecycle` | Backend `004-attention-lifecycle`: 8 endpoints `/attention/*`, SLA 24h/72h/120h, `AutoCloseScheduler` 24h, `V4` | `KevinStiven04` (impl.) / `Jos3GS` (specs) | `José Gutiérrez` (`Jos3GS`) |
| **#9** | 2026-11-15 | `BE/Feat/004-attention-lifecycle` | Backend `004-attention-lifecycle`: ajustes e integración final de ciclo de atención y cierre automático | `KevinStiven04` | `José Gutiérrez` (`Jos3GS`) |
| **#10** | 2026-11-15 | `BE/Feat/005-coordinator-reports` | Backend `005-coordination-reports`: `/reports/summary`, `/reports/requests`, `ReportFilterBuilder`, `V5` | `Jos3GS` | `José Gutiérrez` (`Jos3GS`) |
| **#11** | 2026-11-15 | `FE/Feat/005-coordination-reports` | Frontend `005-coordination-reports`: `CoordinationReportsScreen`, filtros dinámicos, tarjetas resumen y listado paginado | `KevinStiven04` | `José Gutiérrez` (`Jos3GS`) |

> **Nota para la sustentación:** Verificar en GitHub que cada PR cuente con su descripción (propósito, cambios, pruebas y requisitos) y registrar en un nuevo PR los cambios finales de `CaddyBootstrapper` y la documentación de sustentación (`README.md`, `docs/*`, `evidencias/*`).
