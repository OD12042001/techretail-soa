# TechRetail SOA

Plataforma de comercio electrónico de **TechRetail S.A.** construida con una **Arquitectura Orientada a Servicios (SOA)**: diez microservicios Spring Boot independientes, un BUS de servicios (gateway) como único punto de entrada, trazabilidad distribuida con OpenTelemetry y Jaeger, mensajería asíncrona con RabbitMQ y una base de datos PostgreSQL por servicio (*Database per Service*).

Proyecto académico del curso de Arquitectura Orientada al Servicio.

> [!TIP]
> **¿Nuevo en el proyecto?** Empieza por la [**Guía de Proyecto (`docs/Guia_Proyecto.md`)**](docs/Guia_Proyecto.md): explica qué hace cada servicio, cómo encender todo paso a paso y en qué orden leer el código. Para el mapa formal de arquitectura, puertos, esquemas y contratos, consulta el [**Documento Técnico (`docs/Documento_Tecnico.md`)**](docs/Documento_Tecnico.md).

## Estado del proyecto

La **Fase P0** está completada con **4 microservicios funcionales** y **127 pruebas automatizadas en verde** (115 en los servicios funcionales + 12 de contexto en los módulos en andamiaje):

| Fase / Bloque | Contenido | Estado |
|---|---|---|
| Base e infraestructura | Los 10 módulos arrancan con configuración válida; PostgreSQL (`:5433`), RabbitMQ y Jaeger en Docker Compose | Completa |
| Autenticación y BUS | Registro, login, emisión y validación de JWT con roles (`ADMIN`, `EMPLEADO`, `CLIENTE`) y enrutamiento en `techretail-gateway` | Completa |
| Auditoría y monitoreo | Auditoría asíncrona en `db_esb`, sondas periódicas de salud en `techretail-monitoreo` y trazas OpenTelemetry hacia Jaeger | Completa |
| Servicios de negocio | `techretail-producto` implementado (catálogo y categorías con seed inicial); los 6 servicios restantes en andamiaje | En curso |

## Módulos

Cada carpeta es un proyecto Maven **independiente** con su propio `mvnw`. No hay POM raíz ni reactor.

| Módulo | Puerto | Base de datos | Estado | Notas |
|---|---|---|---|---|
| `techretail-cliente` | 8081 | `db_cliente` | Andamiaje | |
| `techretail-producto` | 8082 | `db_producto` | **Implementado** | Catálogo y categorías: lectura pública, escritura solo `ADMIN` |
| `techretail-pedido` | 8083 | `db_pedido` | Andamiaje | AMQP + OpenFeign |
| `techretail-pago` | 8084 | `db_pago` | Andamiaje | |
| `techretail-inventario` | 8085 | `db_inventario` | Andamiaje | AMQP |
| `techretail-notificacion` | 8086 | `db_notificacion` | Andamiaje | AMQP |
| `techretail-facturacion-sunat` | 8087 | `db_facturacion` | Andamiaje | SOAP contract-first (WSDL/XSD) |
| `techretail-autenticacion` | 8088 | `db_autenticacion` | **Implementado** | Registro, login, emisión de JWT y usuarios de demostración |
| `techretail-monitoreo` | 8089 | `db_monitoreo` | **Implementado** | Sondeos periódicos de salud, JWT propio |
| `techretail-gateway` | 8090 | `db_esb` | **Implementado** | BUS: enrutamiento, JWT y auditoría asíncrona |

Todo el tráfico externo entra por el gateway (`http://localhost:8090`).

## Tecnologías

- **Java 21** y **Spring Boot 4.1.1** (starters modulares, p. ej. `spring-boot-starter-webmvc`)
- **Spring Cloud Gateway Server WebMVC** (el gateway es servlet, no reactivo)
- **Spring Security** con **JWT** (HS256, `jjwt` 0.11.5), sin sesión y sin HTTP Basic
- **Spring Data JPA** / **JdbcTemplate**, **Bean Validation**
- **PostgreSQL 16** en ejecución normal (puerto **5433** en el host); **H2** en memoria (modo PostgreSQL) en las pruebas
- **Flyway** para el esquema (Hibernate solo valida: `ddl-auto=validate`)
- **OpenTelemetry** (`spring-boot-starter-opentelemetry`, Micrometer Tracing + OTLP) y **Jaeger** 1.62.0 para trazas distribuidas
- **RabbitMQ** (mensajería asíncrona) y **OpenFeign** (llamadas síncronas)
- **Spring-WS** + JAXB (facturación SOAP), **springdoc-openapi** 3.1.0
- **Maven** (wrapper `mvnw` en cada módulo), **Docker Compose** para la infraestructura
- Pruebas con **JUnit 5**, **MockMvc** y **Mockito**, desarrolladas con TDD

## Prerrequisitos

| Herramienta | Versión | Para qué |
|---|---|---|
| JDK | 21 | Compilar y ejecutar |
| Docker y Docker Compose | recientes | PostgreSQL, RabbitMQ, Jaeger y pgAdmin |
| Git | cualquiera | Clonar el repositorio |

No necesitas instalar Maven: cada módulo incluye `mvnw` / `mvnw.cmd`. Las pruebas **no** requieren Docker ni base de datos (usan H2).

## Clonar el repositorio

```powershell
git clone https://github.com/OD12042001/techretail-soa.git
cd techretail-soa
```

## Infraestructura local

Desde la raíz del repositorio:

```powershell
docker compose up -d postgres rabbitmq jaeger
```

| Servicio | URL / puerto en el host | Credenciales |
|---|---|---|
| PostgreSQL | `localhost:5433` (`5433 -> 5432`) | `techretail` / `techretail123` |
| RabbitMQ (AMQP) | `localhost:5672` | `guest` / `guest` |
| RabbitMQ (panel web) | http://localhost:15672 | `guest` / `guest` |
| Jaeger (UI de trazas) | http://localhost:16686 | — |
| Jaeger (colector OTLP) | `localhost:4318` (HTTP) / `localhost:4317` (gRPC) | — |
| pgAdmin (opcional: `docker compose up -d pgadmin`) | http://localhost:5050 | `admin@techretail.com` / `admin` |

> [!NOTE]
> - PostgreSQL se publica en el puerto **5433** del host porque el 5432 está ocupado por una instalación nativa de PostgreSQL 18. Todos los `application.properties` apuntan por defecto a `${DB_PORT:5433}`.
> - `docker/postgres-init.sql` crea las 10 bases de datos **solo la primera vez** que se crea el volumen. Si añades o renombras una base de datos, ejecuta `docker compose down -v` y vuelve a levantar, o créala manualmente.

## Configuración y variables de entorno

La configuración está en `src/main/resources/application.properties` de cada módulo (formato `.properties`, no YAML). Todos los valores tienen un valor por defecto para desarrollo y se pueden sobrescribir con variables de entorno, así que **no hace falta editar archivos** para levantar el proyecto en local.

| Variable | Por defecto | Usada por | Descripción |
|---|---|---|---|
| `DB_HOST` | `localhost` | todos | Host de PostgreSQL |
| `DB_PORT` | `5433` | todos | Puerto de PostgreSQL en el host |
| `DB_USER` | `techretail` | todos | Usuario de la base de datos |
| `DB_PASSWORD` | `techretail123` | todos | Contraseña de la base de datos |
| `JWT_SECRET` | secreto de desarrollo | autenticación, gateway, monitoreo, producto | Clave HS256, **mínimo 32 bytes**. **Debe ser idéntica en todos los módulos** |
| `JWT_ISSUER` | `techretail-auth` | los mismos | Emisor esperado del token |
| `JWT_EXPIRATION_MINUTES` | `60` | autenticación | Vigencia del token |
| `AUTH_DEMO_USERS` | `true` | autenticación | Crea usuarios de demostración al arrancar |
| `SERVICIO_<NOMBRE>_URL` | `http://localhost:<puerto>` | gateway, monitoreo | URL de cada servicio destino (p. ej. `SERVICIO_CLIENTES_URL`) |
| `MONITOREO_INTERVALO_MS` | `30000` | monitoreo | Intervalo entre ciclos de salud |
| `MONITOREO_TIMEOUT_MS` | `2000` | monitoreo | Timeout de cada sondeo |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://localhost:4318/v1/traces` | servicios funcionales | Endpoint OTLP/HTTP del colector de trazas (Jaeger) |
| `OTEL_SAMPLING_PROBABILITY` | `1.0` | servicios funcionales | Fracción de peticiones trazadas (`1.0` = 100 %) |

Ejemplo en PowerShell, para cambiar el secreto en **todas** las terminales antes de arrancar:

```powershell
$env:JWT_SECRET = "una-clave-larga-de-al-menos-32-bytes-solo-para-tu-entorno"
```

> [!WARNING]
> Las credenciales y el secreto JWT por defecto están en el repositorio y son **solo para desarrollo local**. Cámbialos en cualquier otro entorno y desactiva los usuarios de demostración con `AUTH_DEMO_USERS=false`.

### Usuarios de demostración

El registro público solo crea usuarios `CLIENTE`. Para probar los tres roles, `techretail-autenticacion` aprovisiona al arrancar:

| Usuario | Contraseña | Rol |
|---|---|---|
| `admin@techretail.pe` | `Admin123!` | `ADMIN` |
| `empleado@techretail.pe` | `Empleado123!` | `EMPLEADO` |
| `cliente@techretail.pe` | `Cliente123!` | `CLIENTE` |

## Compilar y ejecutar

Todos los comandos se ejecutan **dentro del directorio del módulo**; en la raíz no hay nada que compilar.

```powershell
cd techretail-producto
.\mvnw.cmd clean install        # compila y ejecuta los tests
.\mvnw.cmd spring-boot:run      # arranca el servicio (usa PostgreSQL real en :5433)
```

Orden recomendado de arranque, cada uno en su propia terminal:

1. `techretail-autenticacion` (8088) — emite los tokens
2. `techretail-producto` (8082) — catálogo (carga 5 categorías base con Flyway `V2`)
3. `techretail-monitoreo` (8089) — sondeos periódicos de salud
4. `techretail-gateway` (8090) — último, para que las rutas ya tengan destino

Los servicios que todavía están en andamiaje arrancan, pero no exponen endpoints de negocio.

### Ejecutar las pruebas

```powershell
cd techretail-producto
.\mvnw.cmd test                                              # todos los tests del módulo
.\mvnw.cmd test -Dtest=ProductoControllerTests               # una clase
```

Las pruebas usan el perfil `test` (H2 en memoria con las migraciones Flyway reales), por lo que no necesitan Docker.

## Probar la API

Obtener un token y operar el catálogo a través del gateway:

```powershell
$login = Invoke-RestMethod -Method Post -Uri http://localhost:8090/api/auth/login `
  -ContentType 'application/json' `
  -Body '{"usuario":"admin@techretail.pe","password":"Admin123!"}'
```

La respuesta incluye `token`, `tipo`, `expiraEn`, `rol`, `idUsuario` y `nombre`. Usa `$login.token` como Bearer:

```powershell
# Lectura pública de categorías (5 categorías sembradas por V2__seed_categorias.sql) y productos
Invoke-RestMethod http://localhost:8090/api/productos/categorias
Invoke-RestMethod http://localhost:8090/api/productos

# Escritura: requiere rol ADMIN
Invoke-RestMethod -Method Post -Uri http://localhost:8090/api/productos `
  -Headers @{ Authorization = "Bearer $($login.token)" } -ContentType 'application/json' `
  -Body '{"nombre":"Mouse","descripcion":"Gamer","precio":89.90,"idCategoria":1,"stockReferencial":25}'
```

También puedes importar en Postman la colección lista para ejecutar en [`docs/postman_collection/techretail-avance3.json`](docs/postman_collection/techretail-avance3.json).

### Endpoints implementados

| Servicio | Endpoints | Acceso |
|---|---|---|
| Autenticación | `POST /api/auth/registro`, `POST /api/auth/login` | Público |
| Producto | `GET /api/productos`, `GET /api/productos/{id}`, `GET /api/productos/categorias` | Público |
| Producto | `POST /api/productos`, `PUT /api/productos/{id}`, `POST /api/productos/categorias` | `ADMIN` |
| Monitoreo | `GET /api/monitoreo/estado`, `GET /api/monitoreo/historial?limite=` | `ADMIN`, `EMPLEADO` |
| Auditoría (gateway) | `GET /api/auditoria?limite=`, `GET /api/auditoria/resumen` | `ADMIN`, `EMPLEADO` |
| Salud | `GET /actuator/health` | Público |

## Seguridad

- El gateway valida firma, emisor y expiración de cada JWT, aplica la política por rol y registra cada petición en `auditoria_logs` (escritura asíncrona, no bloquea la respuesta).
- **Zero Trust:** `techretail-monitoreo` y `techretail-producto` vuelven a validar el JWT por sí mismos, por lo que protegen sus recursos aunque se acceda sin pasar por el gateway.
- Los errores de seguridad responden en JSON: `401` sin token o token inválido, `403` con un rol sin permiso.

## Documentación

Toda la documentación detallada del proyecto vive en la carpeta [`docs/`](docs/):

- [**`docs/Guia_Proyecto.md`**](docs/Guia_Proyecto.md) — **Punto de partida recomendado para nuevos desarrolladores:** explicación sencilla de cada microservicio, guía paso a paso de encendido en terminal y ruta de lectura del código.
- [**`docs/Documento_Tecnico.md`**](docs/Documento_Tecnico.md) — **Mapa técnico formal:** arquitectura, puertos, stack con versiones, bases de datos, seguridad, observabilidad y contratos REST.
- [`docs/Avance3_TechRetail_SOA.md`](docs/Avance3_TechRetail_SOA.md), [`docs/Avance2_TechRetail_SOA.md`](docs/Avance2_TechRetail_SOA.md) y [`docs/PLAN_PROYECTO_SOA.md`](docs/PLAN_PROYECTO_SOA.md) — Entregables académicos del curso. Si algún documento histórico difiere del código, prevalecen los `pom.xml`, `docker-compose.yml` y `Documento_Tecnico.md`.

## Convenciones

- Los paquetes son `com.techretail.techretail_<servicio>` (con guion bajo, por el artefacto de Spring Initializr).
- El esquema lo gobierna Flyway: añade migraciones `V2__*.sql`, `V3__*.sql`, etc.; nunca edites una migración ya aplicada.
- Desarrollo con TDD: primero los tests (en rojo), después el código.
