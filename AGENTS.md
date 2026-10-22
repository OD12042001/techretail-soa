# AGENTS.md

Academic SOA project (TechRetail S.A.): 10 **independent** Spring Boot 4.1.1 / Java 21 Maven projects. Not a multi-module reactor: the root `pom.xml` is empty and there is no root wrapper. All 10 services are still scaffolding — one `*Application` class, one Flyway `V1` migration, no entities/controllers.

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
| techretail-monitoreo | 8089 | db_monitoreo | Actuator |
| techretail-gateway | 8090 | db_esb | WebFlux + R2DBC + Spring Cloud Gateway (ESB) |

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
- Repository is not a Git repo; don't rely on `git diff`/history.

## Before running anything

1. Start infra from the repo root: `docker compose up -d postgres rabbitmq` (pgAdmin on :5050, RabbitMQ UI on :15672).
2. `docker/postgres-init.sql` creates all 10 databases **only on first volume creation**. If you add/rename a DB, use `docker compose down -v` then `up` again, or create it manually.
3. Tests are plain `@SpringBootTest` with **no test profile and no H2**; Flyway is enabled, so a module's test needs its real Postgres DB reachable. There are no unit-test-only suites.
4. Committed credentials: `techretail` / `techretail123`, localhost:5432. Local dev only.

## Critical gotchas

- **Every `src/main/resources/application.properties` actually contains YAML.** Spring parses them as `.properties`, so `server.port`, `spring.datasource.*` and `spring.flyway.*` are all ignored. Verified: `mvnw test` fails with `Failed to determine a suitable driver class`. Fix by converting the file to real properties or renaming it to `application.yml` (snakeyaml is on the classpath). Never append YAML to a `.properties` file.
- **Flyway owns the schema.** `ddl-auto: validate` is set in every service, so JPA entities must match the migrations or startup fails. Add `V2__*.sql`; never edit an already-applied `V1`.
- **Packages are `com.techretail.techretail_<service>` with an underscore** (Spring Initializr artifact; see each `HELP.md`). Keep that convention and nest new packages under it; don't "fix" the name.
- **These are Spring Boot 4 modular starters**: `spring-boot-starter-webmvc`, `spring-boot-starter-*-test`. Don't replace them with `spring-boot-starter-web` / `spring-boot-starter-test` out of habit.
- **Facturación is contract-first**: edit `src/main/resources/wsdl/facturacion.xsd`; `jaxb2-maven-plugin` regenerates classes into `target/generated-sources/jaxb`. Never hand-edit generated code.
- **Gateway uses R2DBC only**, but Flyway needs JDBC. Configure `spring.flyway.url` (or a JDBC datasource) before enabling migrations there.
- **Autenticación has Spring Security on the classpath with no `SecurityFilterChain`**, so every endpoint is protected by HTTP Basic with a generated password until security config is added.
- The gateway POM mixes `spring-cloud-starter-gateway-server-webmvc` with WebFlux/R2DBC; keep gateway code non-blocking and don't add JPA there.

## Docs

`docs/*.md` are Spanish course deliverables. `docs/Avance2_TechRetail_SOA.md` is the best source for endpoint/DB/API contracts and `docs/PLAN_PROYECTO_SOA.md` for architectural intent, but their stack claims are stale: they say Spring Boot 3.x / Java 17 and 9 services, while the code is Boot 4.1.1 / Java 21 with 10 modules (gateway included). Trust the POMs and `docker-compose.yml` over the docs.
