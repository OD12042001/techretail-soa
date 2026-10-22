# AGENTS.md

Academic SOA project (TechRetail S.A.): 10 **independent** Spring Boot 4.1.1 / Java 21 Maven projects. Not a multi-module reactor: the root `pom.xml` is empty and there is no root wrapper. Phase 1 is done: every module boots with valid config. Phase 2.2 is done: the gateway has real behaviour (routing, JWT enforcement, asynchronous auditing to `db_esb`, `/api/auditoria` endpoints) and `techretail-monitoreo` too (periodic health checks into `monitoreo_logs`, own JWT security). The other eight domain services are still scaffolding (one `*Application` class, one Flyway `V1`, no entities/controllers).

## Modules

| Module | Port | DB | Notable stack |
|---|---|---|---|
| techretail-cliente | 8081 | db_cliente | springdoc-openapi 3.1.0 |
| techretail-producto | 8082 | db_producto | springdoc-openapi 3.1.0 |
| techretail-pedido | 8083 | db_pedido | AMQP + OpenFeign |
| techretail-pago | 8084 | db_pago | |
| techretail-inventario | 8085 | db_inventario | AMQP |
| techretail-notificacion | 8086 | db_notificacion | AMQP |
| techretail-facturacion-sunat | 8087 | db_facturacion | SOAP contract-first (Spring-WS + jaxb2) |
| techretail-autenticacion | 8088 | db_autenticacion | Spring Security + jjwt 0.11.5 |
| techretail-monitoreo | 8089 | db_monitoreo | Actuator + `@Scheduled` health probes + Spring Security/jjwt 0.11.5 |
| techretail-gateway | 8090 | db_esb | Gateway WebMVC (servlet) + JdbcTemplate + Spring Security |

## Commands

Run from inside a module directory (each has its own `mvnw`):

```powershell
.\mvnw.cmd spring-boot:run          # run one service
.\mvnw.cmd test                     # all tests of that module
.\mvnw.cmd test -Dtest=TechretailPedidoApplicationTests
.\mvnw.cmd package
```

- `mvn`/`mvnw` at the repo root does nothing (empty POM, no wrapper). Always `cd` into the module first.
- No lint, formatter, typecheck, CI, or pre-commit hook exists in this repo. `test` is the only verification gate.
- The repo **is** a Git repo (`main` + feature branches). Each module has its own `.gitignore` that excludes `target/`.

## Before running anything

1. Start infra from the repo root: `docker compose up -d postgres rabbitmq` (pgAdmin on :5050, RabbitMQ UI on :15672).
2. `docker/postgres-init.sql` creates all 10 databases **only on first volume creation**. If you add/rename a DB, use `docker compose down -v` then `up` again, or create it manually.
3. Tests run with the `test` profile (`src/test/resources/application-test.properties`): **H2 in memory with `MODE=PostgreSQL`**, Flyway still applies the real migrations. So `.\mvnw.cmd test` needs no Docker and no database. `spring-boot:run` still uses the real PostgreSQL from `application.properties`.
4. Committed credentials: `techretail` / `techretail123`, localhost:5432. Local dev only. Every datasource value is a placeholder (`${DB_HOST:localhost}`, `${DB_PORT:5432}`, `${DB_USER:...}`, `${DB_PASSWORD:...}`), so an external DB overrides them by environment variable.

## Critical gotchas

- **Configs are real `.properties` again.** They used to contain YAML (Spring ignored `server.port`, `spring.datasource.*` and `spring.flyway.*`, and `mvnw test` failed with `Failed to determine a suitable driver class`). Keep new keys in properties syntax; never append YAML to a `.properties` file.
- **Flyway owns the schema.** `spring.jpa.hibernate.ddl-auto=validate` is set in every service, so JPA entities must match the migrations or startup fails. Add `V2__*.sql`; never edit an already-applied `V1`.
- **Packages are `com.techretail.techretail_<service>` with an underscore** (Spring Initializr artifact; see each `HELP.md`). Keep that convention and nest new packages under it; don't "fix" the name.
- **These are Spring Boot 4 modular starters**: `spring-boot-starter-webmvc`, `spring-boot-starter-*-test`. Don't replace them with `spring-boot-starter-web` / `spring-boot-starter-test` out of habit. Boot 4 also moved test annotations: `@AutoConfigureMockMvc` lives in `org.springframework.boot.webmvc.test.autoconfigure`.
- **Facturación is contract-first**: edit `src/main/resources/wsdl/facturacion.xsd`; `jaxb2-maven-plugin` **3.1.0** regenerates classes into `target/generated-sources/jaxb`. Version 2.5.0 emits `javax.xml.bind` and no longer compiles against Boot 4 (Jakarta). Never hand-edit generated code.
- **Gateway is a servlet (WebMVC) service, not reactive.** `spring-boot-starter-webflux`, `data-r2dbc` and `r2dbc-postgresql` are gone on purpose; it talks to `db_esb` with `JdbcTemplate`. Don't reintroduce reactive deps or JPA there.
- **Gateway auditing is asynchronous.** `AuditFilter` hands entries to `AuditPublisher` (bounded `auditExecutor`), so tests that read `auditoria_logs` must wait for the row. The local `/api/auditoria/**` endpoints (ADMIN/EMPLEADO) live outside `/api/monitoreo/**` because the gateway routes that prefix to the monitoreo service before any local controller.
- **Monitoreo probes** the 9 other services (`techretail.monitoreo.servicios[*]`, URLs overridable via `SERVICIO_<NAME>_URL`) every `MONITOREO_INTERVALO_MS` (30 s). The scheduler is disabled in the `test` profile (`techretail.monitoreo.scheduler.enabled=false`).
- **Security is explicit now.** The gateway enforces stateless JWT (`techretail.security.jwt.*`, HS256, shared `JWT_SECRET` with autenticación) and writes every request to `auditoria_logs`; autenticación and monitoreo each have their own `SecurityFilterChain` (monitoreo re-validates the JWT and allows only `ADMIN`/`EMPLEADO` on `/api/monitoreo/**`; `/actuator/health` stays public). `/api/auth/**` is public on purpose; everything else answers 401/403 in JSON. There are no default HTTP Basic credentials to fall back on.

## Docs

`docs/*.md` are Spanish course deliverables. `docs/Avance2_TechRetail_SOA.md` is the best source for endpoint/DB/API contracts and `docs/PLAN_PROYECTO_SOA.md` for architectural intent, but their stack claims are stale: they say Spring Boot 3.x / Java 17 and 9 services, while the code is Boot 4.1.1 / Java 21 with 10 modules (gateway included). Trust the POMs and `docker-compose.yml` over the docs.
