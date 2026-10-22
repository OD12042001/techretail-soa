# PROYECTO FINAL
## ARQUITECTURA ORIENTADA A SERVICIOS (SOA) (100000SI84)
### Avance 2 (Semana 10) — Diseño de Arquitectura y Servicios

**Nombre del proyecto:** Diseño de una Arquitectura SOA para TechRetail S.A.

**Curso:** Arquitectura Orientada al Servicio (100000SI84)
**Ciclo:** 2026 — Ciclo 2 Agosto
**Carrera:** Ingeniería de Sistemas e Informática
**Docente:** Calderón Chuquillanqui, Anthony Vidal
**Equipo:** Grupo 1
**Integrante:** Mori Gomez, Victor Manuel

---

# 1. Resumen Ejecutivo (ajustado con el feedback del Avance 1)

TechRetail S.A. es una cadena ficticia de tiendas de tecnología y electrónica con presencia en tres ciudades del Perú, que opera a través de tienda física y tienda en línea y que está obligada a emitir boletas electrónicas ante la SUNAT. En el Avance 1 se diagnosticó su situación actual —sistemas aislados (monolito PHP, hojas Excel por tienda y coordinación por WhatsApp), inventario sin sincronización, clientes duplicados y dependencia de un proveedor externo SOAP— y se definieron los procesos críticos de negocio y los nueve servicios candidatos de la arquitectura.

Con el feedback recibido sobre el Avance 1 se reforzaron las siguientes decisiones de diseño, que este documento formaliza con mayor nivel de detalle:

- **Base de datos por servicio (Database per Service):** cada uno de los nueve servicios posee su propia base de datos PostgreSQL (diez bases de datos en total, incluyendo la del BUS). Ningún servicio accede directamente a las tablas de otro; la integración se realiza exclusivamente mediante servicios expuestos o colas de mensajería.
- **BUS con direccionamiento (Enterprise Service Bus):** se define explícitamente **qué tipo de BUS se propone** y **por qué**, se detalla su tabla de direccionamiento, la transformación de mensajes JSON ↔ XML, la validación centralizada de JWT, la auditoría y el catálogo de servicios con las operaciones clásicas UDDI (publish / find / bind). Se aclara, además, que el BUS es una **propuesta de implementación**: en el marco de las cuatro entregas se materializará un subconjunto de sus capacidades como gateway propio.
- **Mayor nivel de detalle:** el inventario se complementa con fichas por servicio (módulo, endpoints, entradas/salidas, seguridad), el diseño incluye los **esquemas de datos (DDL) y de mensajes (JSON Schema / XSD)**, se detallan los **protocolos** (REST, SOAP y AMQP) y se refuerza la **especificación Swagger/OpenAPI**.

Este Avance 2 presenta: las herramientas y tecnologías utilizadas; el inventario detallado de los nueve servicios; el diseño de esquemas de datos y mensajes; la arquitectura SOA en cuatro capas con el BUS propuesto; la composición y orquestación del proceso de compra; la especificación técnica de dos servicios representativos; y el modelo de seguridad inicial. Los diagramas acompañan cada sección clave.

---

# 2. Herramientas y Tecnologías

| Herramienta / Tecnología | Versión | Capa / uso | Para qué se utiliza |
|---|---|---|---|
| Java | 17+ (LTS) | Todas | Lenguaje base de los servicios |
| Spring Boot | 3.x | Negocio | Framework de los servicios REST |
| Spring Web Services (Spring-WS) | 4.x | Negocio (SOAP) | Servicio SOAP Contract-First |
| Spring Cloud Gateway / Spring Boot | 3.x | Integración | Base del BUS/ESB propuesto (enrutamiento) |
| Spring Cloud OpenFeign | 4.x | Integración | Comunicación síncrona entre servicios |
| Spring Security + JWT | 6.x | Seguridad | Autenticación y autorización por roles |
| PostgreSQL | 16 | Datos | Una base de datos por servicio (10 en total) |
| Flyway | 10.x | Datos | Migraciones versionadas (V1, V2, …) por servicio |
| RabbitMQ | 3.13 | Integración | Mensajería asíncrona por eventos |
| SpringDoc OpenAPI (Swagger UI) | 2.x | Documentación | Documentación interactiva de los servicios REST |
| WSDL (auto-generado) | 1.1 | Documentación | Contrato del servicio SOAP |
| jaxb2-maven-plugin | 2.5.0 | Build | Genera clases Java a partir del XSD (Contract-First) |
| Lombok | 1.18 | Desarrollo | Reduce código repetitivo en Java |
| Docker + Docker Compose | 24+ | Infraestructura | Levantar PostgreSQL, RabbitMQ y pgAdmin en un comando |
| Postman | 11.x | Pruebas | Colecciones de request/response de los servicios |
| Git + GitHub | — | Gestión | Control de versiones y entrega del código |
| draw.io | — | Diagramación | Diagramas de arquitectura y BPMN |
| SLF4J + Logback | 1.5 / 1.4 | Observabilidad | Logs rotados y auditoría |
| Pillow (Python) | 12.x | Diagramación | Generación de los diagramas de este avance |

**Nota:** las versiones indicadas son las de referencia; se fijarán en cada `pom.xml` al iniciar la implementación (Avance 3).

---

# 3. Inventario de Servicios

## 3.1 Cuadro resumen

| # | Nombre del servicio | Descripción | Entrada | Salida | Protocolo | Formato |
|---|---|---|---|---|---|---|
| 1 | ClienteService | Gestión de clientes: registro, actualización y consulta de perfil y contacto. | Datos del cliente (documento, nombre, email, teléfono, dirección, ciudad) | Cliente con id, fecha de registro y estado ACTIVO/INACTIVO | REST | JSON |
| 2 | ProductoService | Administración del catálogo: precios, categorías y disponibilidad. | Filtros (categoría, precio) o datos del producto | Lista/detalle de productos con precio y disponibilidad | REST | JSON |
| 3 | PedidoService | Creación, consulta y cambio de estado de pedidos; orquestador del proceso de compra. | Carrito (idCliente, items, método de pago) | Pedido con id, estado, subtotal, IGV, total y fecha | REST | JSON |
| 4 | PagoService | Procesamiento de pagos asociados a un pedido. | idPedido, monto, método y datos del medio de pago | Pago con estado APROBADO/RECHAZADO y nro. de operación | REST | JSON |
| 5 | InventarioService | Control de stock por tienda, reservas y alertas de reposición. | idProducto, idTienda, cantidad, idPedido | Reserva con stock anterior/actual o error 409 con stock | REST | JSON |
| 6 | NotificacionService | Envío de confirmaciones y avisos a clientes y personal. | Destinatario, evento, canal y mensaje | Notificación con estado ENVIADO y fecha | REST | JSON |
| 7 | FacturacionSunatService | Emisión de boletas electrónicas (sistema heredado/SUNAT). | RUC, monto y descripción (FacturaRequest) | Nro. de boleta, estado y fecha (FacturaResponse) | SOAP | XML |
| 8 | AutenticacionService | Registro, login, emisión/validación de JWT y gestión de roles. | Usuario y contraseña | Token JWT (Bearer), expiración, rol e idUsuario | REST | JSON |
| 9 | MonitoreoService | Disponibilidad y tiempos de respuesta de los servicios. | Sin parámetros | Estado UP/DOWN y tiempo de respuesta por servicio | REST | JSON |

## 3.2 Detalle de cada servicio (módulos)

A continuación se documenta cada servicio como módulo independiente: responsabilidad, base de datos, endpoints principales, entradas/salidas y reglas de seguridad.

**1. ClienteService** — Tipo: entidad · Protocolo: REST/JSON · BD: `db_cliente` (tabla `clientes`)

- Responsabilidad: mantener el maestro de clientes de los canales web, móvil y POS.
- Endpoints: `POST /api/clientes`, `GET /api/clientes/{id}`, `GET /api/clientes`, `PUT /api/clientes/{id}`, `DELETE /api/clientes/{id}`.
- Entrada: `{nombres, tipoDocumento, numeroDocumento, email, telefono, direccion, ciudad}`.
- Salida: cliente con `idCliente`, `fechaRegistro`, `estado`.
- Seguridad: JWT; alta/edición reservada a ADMIN; el CLIENTE solo consulta su propio perfil.

**2. ProductoService** — Tipo: entidad · Protocolo: REST/JSON · BD: `db_producto` (tablas `productos`, `categorias`)

- Responsabilidad: catálogo de productos, precios y categorías; alimenta la tienda y el POS.
- Endpoints: `GET /api/productos`, `GET /api/productos/{id}`, `POST /api/productos`, `PUT /api/productos/{id}`.
- Entrada: `{nombre, descripcion, precio, idCategoria, stockReferencial}`.
- Salida: lista paginada de productos con precio y disponibilidad.
- Seguridad: `GET` público (navegación); escritura con rol ADMIN.

**3. PedidoService** — Tipo: proceso · Protocolo: REST/JSON · BD: `db_pedido` (tablas `pedidos`, `pedido_items`)

- Responsabilidad: registrar y dar seguimiento a los pedidos; **orquesta** la compra.
- Endpoints: `POST /api/pedidos`, `GET /api/pedidos/{id}`, `GET /api/pedidos?idCliente=`, `PUT /api/pedidos/{id}/estado`.
- Entrada: `{idCliente, idTienda, metodoPago, items[]}`.
- Salida: pedido con `subtotal`, `igv`, `total`, `estado` y fecha.
- Seguridad: JWT; CLIENTE solo sus pedidos; EMPLEADO/ADMIN cualquier pedido.

**4. PagoService** — Tipo: tarea · Protocolo: REST/JSON · BD: `db_pago` (tabla `pagos`)

- Responsabilidad: procesar el pago de un pedido y determinar su resultado.
- Endpoints: `POST /api/pagos`, `GET /api/pagos/{idPedido}`.
- Entrada: `{idPedido, monto, metodoPago, numeroTarjeta, titular}`.
- Salida: `{idPago, estado: APROBADO|RECHAZADO, operacion, fecha}`.
- Seguridad: JWT con rol CLIENTE o ADMIN.

**5. InventarioService** — Tipo: utilidad · Protocolo: REST/JSON · BD: `db_inventario` (tablas `inventarios`, `reservas`, `alertas`)

- Responsabilidad: stock por tienda, reservas durante la compra y alertas de reposición.
- Endpoints: `POST /api/inventario/reservar`, `GET /api/inventario/{idProducto}/{idTienda}`, `PUT /api/inventario/ajuste`.
- Entrada: `{idProducto, idTienda, cantidad, idPedido}`.
- Salida: reserva con `stockAnterior` y `stockActual`; si no hay stock, `409` con `STOCK_INSUFICIENTE`.
- Seguridad: JWT con rol EMPLEADO o ADMIN (reserva interna invocada por PedidoService).

**6. NotificacionService** — Tipo: utilidad · Protocolo: REST/JSON · BD: `db_notificacion` (tabla `notificaciones`)

- Responsabilidad: enviar confirmaciones de pedido/pago y avisos de stock (correo/SMS simulado).
- Endpoints: `POST /api/notificaciones`, `GET /api/notificaciones?idPedido=`.
- Entrada: `{destinatario, evento, canal, mensaje}`.
- Salida: `{idNotificacion, estado: ENVIADO, fecha}`.
- Seguridad: consumo interno vía RabbitMQ; endpoint REST protegido con JWT.

**7. FacturacionSunatService** — Tipo: tarea · Protocolo: SOAP/XML · BD: `db_facturacion` (tabla `facturas`)

- Responsabilidad: emitir boletas electrónicas; sistema heredado que justifica la coexistencia de protocolos.
- Endpoint SOAP: `POST /ws/facturacion` (WSDL en `/ws/facturacion.wsdl`).
- Entrada: envelope SOAP con `FacturaRequest {ruc, monto, descripcion}`.
- Salida: envelope SOAP con `FacturaResponse {numeroFactura, estado, fechaEmision}`.
- Seguridad: invocado internamente por el BUS con rol EMPLEADO o ADMIN.

**8. AutenticacionService** — Tipo: utilidad · Protocolo: REST/JSON · BD: `db_autenticacion` (tablas `usuarios`, `roles`)

- Responsabilidad: registrar usuarios, autenticar y emitir/validar tokens JWT.
- Endpoints: `POST /api/auth/registro`, `POST /api/auth/login`, `POST /api/auth/validar`.
- Entrada: `{usuario, password}`.
- Salida: `{token, tipo: Bearer, expiraEn, rol, idUsuario, nombre}`.
- Seguridad: único servicio con endpoints públicos (no requiere JWT para login/registro).

**9. MonitoreoService** — Tipo: utilidad · Protocolo: REST/JSON · BD: `db_monitoreo` (tabla `monitoreo_logs`)

- Responsabilidad: health checks y tiempos de respuesta de todos los servicios.
- Endpoints: `GET /api/monitoreo/estado`, `GET /api/monitoreo/historial`.
- Entrada: sin parámetros.
- Salida: lista `{nombre, estado: UP|DOWN, tiempoRespuestaMs}`.
- Seguridad: JWT con rol ADMIN.

---

# 4. Diseño de Esquemas (Bases de Datos y Mensajes)

## 4.1 Distribución de bases de datos (Database per Service)

| # | Servicio | Base de datos | Tablas principales | Migraciones Flyway |
|---|---|---|---|---|
| 1 | ClienteService | `db_cliente` | `clientes` | `techretail-cliente/src/main/resources/db/migration/` |
| 2 | ProductoService | `db_producto` | `productos`, `categorias` | `techretail-producto/...` |
| 3 | PedidoService | `db_pedido` | `pedidos`, `pedido_items` | `techretail-pedido/...` |
| 4 | PagoService | `db_pago` | `pagos` | `techretail-pago/...` |
| 5 | InventarioService | `db_inventario` | `inventarios`, `reservas`, `alertas` | `techretail-inventario/...` |
| 6 | NotificacionService | `db_notificacion` | `notificaciones` | `techretail-notificacion/...` |
| 7 | FacturacionSunatService | `db_facturacion` | `facturas` | `techretail-facturacion-sunat/...` |
| 8 | AutenticacionService | `db_autenticacion` | `usuarios`, `roles` | `techretail-autenticacion/...` |
| 9 | MonitoreoService | `db_monitoreo` | `monitoreo_logs` | `techretail-monitoreo/...` |
| — | BUS / ESB | `db_esb` | `auditoria_logs`, `servicios_registro` | `techretail-gateway/...` |

**Total: 10 bases de datos** en un único contenedor PostgreSQL, creadas con `docker/postgres-init.sql` al arrancar. La regla es que **ningún servicio accede a la base de datos de otro**: las referencias entre dominios (por ejemplo `id_cliente` en `pedidos`) son **claves lógicas**, no claves foráneas físicas entre bases de datos.

## 4.2 Esquemas de datos (DDL representativo)

**`db_cliente` — V1__create_tables.sql**

```sql
CREATE TABLE clientes (
    id               BIGSERIAL PRIMARY KEY,
    nombres          VARCHAR(120) NOT NULL,
    tipo_documento   VARCHAR(10)  NOT NULL,      -- DNI / RUC / CE
    numero_documento VARCHAR(20)  NOT NULL UNIQUE,
    email            VARCHAR(120) NOT NULL UNIQUE,
    telefono         VARCHAR(20),
    direccion        VARCHAR(200),
    ciudad           VARCHAR(60),
    estado           VARCHAR(10)  NOT NULL DEFAULT 'ACTIVO',
    fecha_registro   TIMESTAMP    NOT NULL DEFAULT NOW()
);
```

**`db_pedido` — V1__create_tables.sql**

```sql
CREATE TABLE pedidos (
    id             BIGSERIAL PRIMARY KEY,
    id_cliente     BIGINT NOT NULL,              -- referencia logica (no FK entre BD)
    id_tienda      BIGINT,
    estado         VARCHAR(12) NOT NULL DEFAULT 'PENDIENTE',
    subtotal       NUMERIC(10,2) NOT NULL,
    igv            NUMERIC(10,2) NOT NULL,
    total          NUMERIC(10,2) NOT NULL,
    metodo_pago    VARCHAR(15),
    fecha_creacion TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE TABLE pedido_items (
    id              BIGSERIAL PRIMARY KEY,
    id_pedido       BIGINT NOT NULL REFERENCES pedidos(id),
    id_producto     BIGINT NOT NULL,
    cantidad        INT    NOT NULL,
    precio_unitario NUMERIC(10,2) NOT NULL,
    subtotal        NUMERIC(10,2) NOT NULL
);
```

**`db_inventario` — V1__create_tables.sql**

```sql
CREATE TABLE inventarios (
    id           BIGSERIAL PRIMARY KEY,
    id_producto  BIGINT NOT NULL,
    id_tienda    BIGINT NOT NULL,
    stock        INT NOT NULL DEFAULT 0,
    stock_minimo INT NOT NULL DEFAULT 5,
    UNIQUE (id_producto, id_tienda)
);

CREATE TABLE reservas (
    id          BIGSERIAL PRIMARY KEY,
    id_pedido   BIGINT NOT NULL,
    id_producto BIGINT NOT NULL,
    id_tienda   BIGINT NOT NULL,
    cantidad    INT    NOT NULL,
    estado      VARCHAR(12) NOT NULL DEFAULT 'RESERVADO',
    fecha       TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE TABLE alertas (
    id           BIGSERIAL PRIMARY KEY,
    id_producto  BIGINT NOT NULL,
    id_tienda    BIGINT NOT NULL,
    stock_actual INT NOT NULL,
    estado       VARCHAR(12) NOT NULL DEFAULT 'PENDIENTE',
    fecha        TIMESTAMP   NOT NULL DEFAULT NOW()
);
```

**`db_esb` (BUS) — V1__create_tables.sql**

```sql
CREATE TABLE auditoria_logs (
    id               BIGSERIAL PRIMARY KEY,
    servicio_origen  VARCHAR(50)  NOT NULL,
    servicio_destino VARCHAR(50)  NOT NULL,
    operacion        VARCHAR(10)  NOT NULL,     -- GET, POST, PUT, DELETE
    endpoint         VARCHAR(200) NOT NULL,
    estado_respuesta INT          NOT NULL,     -- 200, 400, 409, 500...
    usuario          VARCHAR(100),
    timestamp        TIMESTAMP DEFAULT NOW(),
    detalle          TEXT
);

CREATE TABLE servicios_registro (
    id             BIGSERIAL PRIMARY KEY,
    nombre         VARCHAR(60)  NOT NULL UNIQUE,
    url            VARCHAR(120) NOT NULL,
    protocolo      VARCHAR(10)  NOT NULL,       -- REST / SOAP
    formato        VARCHAR(10)  NOT NULL,       -- JSON / XML
    wsdl           VARCHAR(160),
    version        VARCHAR(10)  NOT NULL DEFAULT '1.0',
    estado         VARCHAR(10)  NOT NULL DEFAULT 'UP',
    fecha_registro TIMESTAMP DEFAULT NOW()
);
```

## 4.3 Esquemas de mensajes (JSON Schema)

Además del DDL, cada mensaje REST se especifica con **JSON Schema**, de modo que el contrato de datos no dependa solo del código. Ejemplo para PedidoService:

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "title": "PedidoRequest",
  "type": "object",
  "required": ["idCliente", "items"],
  "properties": {
    "idCliente":  { "type": "integer" },
    "idTienda":   { "type": "integer" },
    "metodoPago": { "type": "string", "enum": ["TARJETA", "YAPE", "EFECTIVO"] },
    "items": {
      "type": "array",
      "minItems": 1,
      "items": {
        "type": "object",
        "required": ["idProducto", "cantidad"],
        "properties": {
          "idProducto": { "type": "integer" },
          "cantidad":   { "type": "integer", "minimum": 1 }
        }
      }
    }
  }
}
```

El servicio SOAP se especifica con **XSD** (Contract-First), que es la fuente de verdad del contrato y se detalla en la sección 7.2.

---

# 5. Diseño de Arquitectura SOA

## 5.1 Arquitectura en capas

La arquitectura objetivo se organiza en las cuatro capas clásicas de una SOA.

**Capa de Presentación.** Canales que consumen los servicios: tienda en línea (web), aplicación móvil, punto de venta físico (POS) y panel de empleado. Todos consumen la misma API en JSON y se autentican con JWT; ningún canal accede directamente a las bases de datos.

**Capa de Negocio.** Los nueve servicios del inventario, cada uno encapsulando un dominio de negocio, con su propia base de datos y su propio ciclo de despliegue.

**Capa de Integración.** El BUS/ESB (`techretail-gateway`) como único punto de entrada, encargado del enrutamiento, la mediación/transformación, la seguridad, la auditoría y el catálogo de servicios. Incluye la mensajería asíncrona (RabbitMQ).

**Capa de Datos.** Diez bases de datos PostgreSQL independientes —una por servicio y una para el BUS— con esquemas versionados por Flyway.

![Arquitectura SOA en capas de TechRetail](docs/diagramas-avance2/1_arquitectura_capas.png)

_Figura 1. Arquitectura SOA en capas: presentación, negocio, integración y datos._

## 5.2 Protocolos y convenciones

El diseño combina tres protocolos, cada uno con convenciones explícitas.

**REST/JSON (8 servicios).**

- Recurso base: `/api/{recurso}`; versión implícita `/api/v1/…` reservada para evoluciones.
- Cabeceras: `Content-Type: application/json; charset=UTF-8`, `Authorization: Bearer <JWT>`, `X-Correlation-Id` para trazabilidad.
- Códigos de estado: `200 OK` (consulta), `201 Created` (alta), `204 No Content` (baja), `400 Bad Request`, `401 Unauthorized`, `403 Forbidden`, `404 Not Found`, `409 Conflict`, `503 Service Unavailable`.
- Formato de error uniforme (basado en RFC 7807): `{timestamp, status, codigo, mensaje, path}`.
- Paginación estándar: `?page=0&size=20` → `{content, totalElements, totalPages}`.
- Idempotencia: soporte opcional de `Idempotency-Key` en operaciones de creación.

**SOAP/XML (1 servicio).**

- SOAP 1.1 sobre HTTP `POST`, estilo **document/literal wrapped**.
- Namespace del contrato: `http://techretail.com/facturacion`; operación `Facturar`.
- Contrato definido por **XSD** y WSDL auto-generado (Contract-First).
- Errores mediante `soap:Fault` con `faultcode` y `faultstring`.
- Seguridad: invocado internamente por el BUS; en la fase final puede incorporar WS-Security (UsernameToken).

**AMQP (mensajería asíncrona).**

- Exchange `techretail.events` de tipo **topic** con routing keys: `pedido.confirmado`, `pago.aprobado`, `stock.bajo`.
- Colas: `techretail.notificaciones` y `techretail.alertas`; cola de descarte `techretail.dlq`.
- Mensaje en formato JSON: `{evento, idPedido, timestamp, payload}`; confirmación manual (ack) y reintentos.

## 5.3 El BUS (Enterprise Service Bus) — Qué se propone y por qué

> **Aclaración sobre el término:** el sílabo menciona **"BUS"** = **Enterprise Service Bus (ESB)** (*"Infraestructura de los servicios en bus"*). **No es un API Gateway simple**: es el bus por donde circulan, se direccionan y se transforman los mensajes entre servicios.

**Qué se propone implementar.** Se propone un **BUS/ESB construido con Spring Cloud Gateway + Spring Boot**, desplegado como un componente propio del proyecto llamado `techretail-gateway`. Este componente materializa las cinco funciones clave de un ESB: enrutamiento, mediación/transformación de mensajes, seguridad, auditoría y registro/descubrimiento de servicios (UDDI).

**Por qué se propone esta opción (justificación).**

- **Coherencia con el stack:** el equipo ya trabaja con Java/Spring y Spring Security; no incorpora una nueva tecnología ni una curva de aprendizaje adicional.
- **Sin costos de licencia:** al ser de código abierto y propio, evita licencias y despliegues pesados, adecuados para un entorno académico.
- **Control y transparencia:** al ser código propio, el enrutamiento, la transformación y la auditoría son explícitos y verificables, lo que facilita la sustentación.
- **Suficiente para demostrar el concepto:** evidencia el desacoplamiento, el direccionamiento y la mediación sin la complejidad administrativa de un producto ESB completo.

**Alternativas evaluadas.** Se consideraron productos ESB reconocidos y se descartaron por las razones indicadas:

| Alternativa | Tipo | Ventaja | Por qué no se elige |
|---|---|---|---|
| WSO2 Enterprise Integrator | ESB open source | ESB completo, estándar | Instalación y curva elevadas para el alcance académico |
| Mule ESB / MuleSoft | ESB comercial | Muy difundido en industria | Licenciamiento y complejidad |
| Apache ServiceMix | ESB open source | Basado en OSGi | Tecnología legacy / mantenimiento limitado |
| Red Hat Fuse | ESB / integración | Soporte empresarial | Requiere suscripción |
| Spring Cloud Gateway (elegido) | Gateway/ESB liviano | Integración con Spring, ligero | Es la propuesta seleccionada |

**Alcance (propuesta de implementación, no implementación total).** El BUS se plantea como una **propuesta de arquitectura**: en el marco de las cuatro entregas no se implementa un ESB de producto ni la totalidad de sus capacidades (por ejemplo, mediación avanzada o WS-Security). Se implementará, como evidencia, un **subconjunto**: enrutamiento por tabla de direccionamiento, transformación JSON ↔ XML, validación de JWT, auditoría en `db_esb` y catálogo UDDI. El resto queda documentado como trabajo propuesto.

**Funciones del BUS/ESB.**

- Enrutamiento (direccionamiento) según tabla de rutas.
- Transformación de mensajes JSON ↔ XML (cuando REST conversa con SOAP).
- Seguridad: validación de JWT en un único punto de entrada.
- Auditoría: registro de cada petición en `auditoria_logs`.
- Registro/descubrimiento: catálogo de servicios con publish / find / bind (UDDI).

**Tabla de direccionamiento (routing).**

| Ruta de entrada (cliente) | Método | Servicio destino | Puerto | Formato | Seguridad |
|---|---|---|---|---|---|
| `/api/clientes/**` | GET/POST/PUT/DELETE | ClienteService | 8081 | JSON | JWT |
| `/api/productos/**` | GET/POST/PUT/DELETE | ProductoService | 8082 | JSON | GET público |
| `/api/pedidos/**` | GET/POST/PUT/DELETE | PedidoService | 8083 | JSON | JWT |
| `/api/pagos/**` | GET/POST | PagoService | 8084 | JSON | JWT (ADMIN/CLIENTE) |
| `/api/inventario/**` | GET/POST/PUT | InventarioService | 8085 | JSON | JWT (EMPLEADO/ADMIN) |
| `/api/notificaciones/**` | GET/POST | NotificacionService | 8086 | JSON | JWT |
| `/ws/facturacion` | POST (SOAP) | FacturacionSunatService | 8087 | XML | El BUS transforma JSON→XML |
| `/api/auth/**` | POST | AutenticacionService | 8088 | JSON | Único path sin JWT |
| `/api/monitoreo/**` | GET | MonitoreoService | 8089 | JSON | JWT (ADMIN) |
| `/api/registro/servicios` | GET | Catálogo UDDI (BUS) | 8090 | JSON | Público (descubrimiento) |

![BUS/ESB propuesto y tabla de direccionamiento](docs/diagramas-avance2/2_bus_direccionamiento.png)

_Figura 2. BUS/ESB propuesto: funciones, tabla de direccionamiento y catálogo UDDI._

**Catálogo de servicios (UDDI).** El sílabo exige "Registro de Servicios — UDDI"; como UDDI real es tecnología legacy, se implementa un catálogo REST con las tres operaciones clásicas almacenado en la tabla `servicios_registro` de `db_esb`.

| Operación UDDI | Implementación | Descripción |
|---|---|---|
| Publish | `POST /api/registro/servicios` | Cada servicio se registra al arrancar (nombre, URL, WSDL, versión) |
| Find | `GET /api/registro/servicios` | Lista servicios disponibles (descubrimiento) |
| Bind | El BUS usa la URL registrada | Enruta la petición al servicio registrado |

## 5.4 Comunicación síncrona y asíncrona

| Tipo | Tecnología | Caso de uso | ¿Quién espera la respuesta? |
|---|---|---|---|
| Síncrona | REST + OpenFeign | Consultar catálogo, verificar stock, procesar pago, emitir boleta | El cliente espera |
| Asíncrona | RabbitMQ (`techretail.notificaciones`, `techretail.alertas`) | Confirmaciones de pedido/pago y alertas de stock | No se espera: se publica el evento y el proceso continúa |

**Eventos asíncronos del proyecto.**

| Evento | Publica | Cola | Consume | Acción |
|---|---|---|---|---|
| `pedido.confirmado` | PedidoService | `techretail.notificaciones` | NotificacionService | Envía email/SMS de confirmación |
| `pago.aprobado` | PagoService | `techretail.notificaciones` | NotificacionService | Envía comprobante de pago |
| `stock.bajo` | InventarioService | `techretail.alertas` | NotificacionService | Avisa al gerente para reposición |

## 5.5 Ciclo de vida y buenas prácticas aplicadas

**Ciclo de vida del servicio:** definición → diseño → implementación → pruebas → despliegue → monitoreo → retiro. Cada servicio recorre este ciclo de forma independiente (por ejemplo, NotificacionService puede desplegarse sin reprocesar los demás).

**Buenas prácticas aplicadas.**

- Bajo acoplamiento: los servicios se conocen solo por sus contratos (OpenAPI/WSDL).
- Alta cohesión: cada servicio encapsula un único dominio de negocio.
- Stateless: el estado vive en la base de datos del servicio, no en memoria.
- Contrato explícito: SOAP Contract-First (XSD) y REST con OpenAPI + JSON Schema.
- Reutilización: los mismos servicios atienden web, móvil, POS y panel de empleado.
- Independencia de despliegue: bases de datos y versiones independientes por servicio.

---

# 6. Diseño de Composición/Orquestación

El proceso modelado es el **Proceso de Compra en línea**, que involucra a siete servicios (mínimo requerido: dos) y combina invocaciones síncronas con publicación asíncrona de eventos.

## 6.1 Diagrama del proceso de negocio

![Proceso de Compra en línea con decisiones](docs/diagramas-avance2/3_proceso_compra.png)

_Figura 3. Proceso de Compra en línea: flujo, decisiones y caminos de error._

El flujo incluye las compuertas de decisión (¿token JWT válido?, ¿hay stock suficiente?, ¿pago aprobado?) y sus caminos de error (`401 Unauthorized`, `409 Stock insuficiente`, pago rechazado). El diagrama BPMN detallado —con pools, lanes y eventos— se mantiene como respaldo en `docs/diagramas-bpmn/BPMN_PROCESOS.md`.

## 6.2 Secuencia de orquestación

PedidoService actúa como **orquestador**: invoca secuencialmente a los servicios síncronos y, al completar la compra, publica el evento asíncrono que dispara la notificación.

![Diagrama de secuencia de la orquestación](docs/diagramas-avance2/4_orquestacion_secuencia.png)

_Figura 4. Secuencia de orquestación del Proceso de Compra (llamadas síncronas y eventos asíncronos)._

## 6.3 Interacciones entre servicios

| # | Origen | Destino | Operación | Protocolo | Modo |
|---|---|---|---|---|---|
| 1 | Canal (web/app/POS) | PedidoService | Crear pedido | REST/JSON (vía BUS) | Síncrono |
| 2 | PedidoService | InventarioService | Reservar stock | REST/JSON (OpenFeign) | Síncrono |
| 3 | PedidoService | PagoService | Procesar pago | REST/JSON (OpenFeign) | Síncrono |
| 4 | PedidoService | FacturacionSunatService | Emitir boleta | SOAP/XML (transformado por el BUS) | Síncrono |
| 5 | PedidoService | RabbitMQ | Publicar `pedido.confirmado` | AMQP | Asíncrono |
| 6 | RabbitMQ | NotificacionService | Consumir evento y notificar | AMQP | Asíncrono |
| 7 | InventarioService | RabbitMQ | Publicar `stock.bajo` | AMQP | Asíncrono |
| 8 | Canal | MonitoreoService | Health checks | REST/JSON | Síncrono |

## 6.4 Orquestación y coreografía

La **orquestación** está a cargo de PedidoService, que coordina el flujo paso a paso y concentra la lógica de decisión (si no hay stock o el pago se rechaza, el proceso se aborta antes de facturar). La **notificación** y las **alertas de stock** se resuelven por **coreografía** sobre eventos: los servicios publican sin conocer quién consume y NotificacionService reacciona de forma independiente. Esta combinación evita que el flujo principal dependa de la disponibilidad de los servicios de notificación.

---

# 7. Especificación Técnica de Servicios

Se especifica un servicio REST/JSON (nueva construcción) y un servicio SOAP/XML (heredado).

## 7.1 PedidoService — Swagger/OpenAPI 3.0 (reforzado)

Especificación OpenAPI completa del servicio. En la implementación, SpringDoc OpenAPI genera la documentación interactiva (Swagger UI) a partir de estas definiciones y de las anotaciones del código; el BUS puede **agregar** los esquemas de todos los servicios en un solo Swagger UI.

```yaml
openapi: 3.0.3
info:
  title: TechRetail - PedidoService
  version: 1.0.0
  description: Gestion de pedidos y orquestacion del proceso de compra
servers:
  - url: http://localhost:8083
    description: Acceso directo
  - url: http://localhost:8090/api/pedidos
    description: A traves del BUS/ESB
tags:
  - name: Pedidos
    description: Operaciones sobre pedidos
paths:
  /api/pedidos:
    post:
      tags: [Pedidos]
      summary: Crea un nuevo pedido
      operationId: crearPedido
      security:
        - bearerAuth: []
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/PedidoRequest'
            examples:
              laptop:
                summary: Pedido de ejemplo
                value:
                  idCliente: 1
                  idTienda: 2
                  metodoPago: TARJETA
                  items:
                    - { idProducto: 10, cantidad: 2 }
                    - { idProducto: 15, cantidad: 1 }
      responses:
        '201':
          description: Pedido creado
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/PedidoResponse'
        '400':
          $ref: '#/components/responses/ErrorValidacion'
        '401':
          $ref: '#/components/responses/NoAutorizado'
        '409':
          description: Stock insuficiente
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Error'
    get:
      tags: [Pedidos]
      summary: Lista pedidos (paginado)
      parameters:
        - name: idCliente
          in: query
          schema: { type: integer }
        - name: page
          in: query
          schema: { type: integer, default: 0 }
        - name: size
          in: query
          schema: { type: integer, default: 20 }
      responses:
        '200':
          description: Lista de pedidos
          content:
            application/json:
              schema:
                type: object
                properties:
                  content:
                    type: array
                    items: { $ref: '#/components/schemas/PedidoResponse' }
                  totalElements: { type: integer }
                  totalPages: { type: integer }
  /api/pedidos/{id}:
    get:
      tags: [Pedidos]
      summary: Consulta un pedido por identificador
      parameters:
        - name: id
          in: path
          required: true
          schema: { type: integer }
      responses:
        '200':
          description: Detalle del pedido
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/PedidoResponse'
        '404':
          description: Pedido no encontrado
  /api/pedidos/{id}/estado:
    put:
      tags: [Pedidos]
      summary: Actualiza el estado del pedido
      parameters:
        - name: id
          in: path
          required: true
          schema: { type: integer }
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              properties:
                estado:
                  type: string
                  enum: [PENDIENTE, PAGADO, FACTURADO, CANCELADO]
      responses:
        '200':
          description: Estado actualizado
components:
  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
      bearerFormat: JWT
  responses:
    NoAutorizado:
      description: Token JWT ausente o invalido
      content:
        application/json:
          schema: { $ref: '#/components/schemas/Error' }
    ErrorValidacion:
      description: Datos de entrada invalidos
      content:
        application/json:
          schema: { $ref: '#/components/schemas/Error' }
  schemas:
    PedidoRequest:
      type: object
      required: [idCliente, items]
      properties:
        idCliente: { type: integer }
        idTienda:  { type: integer }
        metodoPago: { type: string, enum: [TARJETA, YAPE, EFECTIVO] }
        items:
          type: array
          minItems: 1
          items:
            type: object
            required: [idProducto, cantidad]
            properties:
              idProducto: { type: integer }
              cantidad:   { type: integer, minimum: 1 }
    PedidoResponse:
      type: object
      properties:
        idPedido: { type: integer }
        estado:   { type: string, enum: [PENDIENTE, PAGADO, FACTURADO, CANCELADO] }
        subtotal: { type: number }
        igv:      { type: number }
        total:    { type: number }
        fechaCreacion: { type: string, format: date-time }
    Error:
      type: object
      properties:
        timestamp: { type: string, format: date-time }
        status:    { type: integer }
        codigo:    { type: string }
        mensaje:   { type: string }
        path:      { type: string }
```

**Ejemplo de entrada (JSON):**

```json
{
  "idCliente": 1,
  "idTienda": 2,
  "metodoPago": "TARJETA",
  "items": [
    { "idProducto": 10, "cantidad": 2 },
    { "idProducto": 15, "cantidad": 1 }
  ]
}
```

**Ejemplo de salida (201 Created):**

```json
{
  "idPedido": 1001,
  "idCliente": 1,
  "estado": "PENDIENTE",
  "subtotal": 5598.98,
  "igv": 1007.82,
  "total": 6606.80,
  "items": [
    { "idProducto": 10, "cantidad": 2, "precioUnitario": 2499.99, "subtotal": 4999.98 },
    { "idProducto": 15, "cantidad": 1, "precioUnitario": 599.00, "subtotal": 599.00 }
  ],
  "fechaCreacion": "2026-10-07T10:35:12Z"
}
```

**Documentación con SpringDoc.** Se agrega la dependencia `springdoc-openapi-starter-webmvc-ui`; la anotación `@Operation` documenta cada endpoint y `@Schema` cada campo del DTO. La UI queda disponible en `/swagger-ui.html` y el esquema en `/v3/api-docs`.

## 7.2 FacturacionSunatService — XSD y WSDL (SOAP/XML, Contract-First)

El servicio se especifica con enfoque **Contract-First**: el XSD es la fuente de verdad y Spring-WS genera el WSDL y las clases Java a partir de él.

**Contrato XSD (`facturacion.xsd`).**

```xml
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
           targetNamespace="http://techretail.com/facturacion"
           xmlns:fac="http://techretail.com/facturacion"
           elementFormDefault="qualified">
  <xs:element name="FacturaRequest">
    <xs:complexType>
      <xs:sequence>
        <xs:element name="ruc" type="xs:string"/>
        <xs:element name="monto" type="xs:decimal"/>
        <xs:element name="descripcion" type="xs:string"/>
      </xs:sequence>
    </xs:complexType>
  </xs:element>
  <xs:element name="FacturaResponse">
    <xs:complexType>
      <xs:sequence>
        <xs:element name="numeroFactura" type="xs:string"/>
        <xs:element name="estado" type="xs:string"/>
        <xs:element name="fechaEmision" type="xs:date"/>
      </xs:sequence>
    </xs:complexType>
  </xs:element>
</xs:schema>
```

**Generación del WSDL.** Spring-WS lee el XSD en `src/main/resources/wsdl/facturacion.xsd` y publica el WSDL en `http://localhost:8087/ws/facturacion.wsdl`. El plugin `jaxb2-maven-plugin` genera las clases `FacturaRequest` y `FacturaResponse` desde el mismo XSD.

**Ejemplo de entrada (SOAP Request).**

```xml
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                  xmlns:fac="http://techretail.com/facturacion">
  <soapenv:Header/>
  <soapenv:Body>
    <fac:FacturaRequest>
      <ruc>20414012345</ruc>
      <monto>6606.80</monto>
      <descripcion>Factura pedido 1001 - 2x Laptop Dell, 1x Mouse Logitech</descripcion>
    </fac:FacturaRequest>
  </soapenv:Body>
</soapenv:Envelope>
```

**Ejemplo de salida (SOAP Response).**

```xml
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                  xmlns:fac="http://techretail.com/facturacion">
  <soapenv:Body>
    <fac:FacturaResponse>
      <numeroFactura>F001-00045821</numeroFactura>
      <estado>ACEPTADA</estado>
      <fechaEmision>2026-10-07</fechaEmision>
    </fac:FacturaResponse>
  </soapenv:Body>
</soapenv:Envelope>
```

---

# 8. Modelo de Seguridad Inicial

## 8.1 Roles

| Rol | Permisos |
|---|---|
| ADMIN | Acceso total: usuarios, inventario, monitoreo y configuración |
| EMPLEADO | Gestión de inventario, pedidos internos y abastecimiento (B2B) |
| CLIENTE | Consulta de catálogo, creación y consulta de sus pedidos, devoluciones |

## 8.2 Autenticación con tokens JWT

Flujo de cinco pasos:

1. Registro: `POST /api/auth/registro` crea el usuario y le asigna un rol.
2. Login: `POST /api/auth/login` valida credenciales y devuelve un token JWT firmado.
3. Consumo: el cliente envía el token en `Authorization: Bearer <token>`.
4. Validación en el BUS: verifica firma, expiración y rol; si es inválido responde `401` sin llegar al servicio.
5. Autorización en el servicio: cada endpoint aplica `@PreAuthorize` (defensa en profundidad).

**Payload decodificado del token.**

```json
{
  "sub": "maria.fernandez@email.com",
  "rol": "CLIENTE",
  "idUsuario": 8,
  "iat": 1791360000,
  "exp": 1791363600
}
```

## 8.3 Endpoints y roles requeridos

| Endpoint | Rol requerido |
|---|---|
| POST /api/auth/registro, POST /api/auth/login | Público (sin JWT) |
| GET /api/productos | Público (catálogo) |
| POST /api/pedidos, GET /api/pedidos/{id} | CLIENTE o ADMIN |
| POST /api/pagos | CLIENTE o ADMIN |
| GET/POST /api/inventario/** | EMPLEADO o ADMIN |
| GET /api/monitoreo/estado | ADMIN |
| POST /ws/facturacion | EMPLEADO o ADMIN (invocado por PedidoService) |

**Auditoría.** Cada consumo que cruza el BUS se registra en `auditoria_logs` (`db_esb`) con origen, destino, operación, endpoint, código de respuesta, usuario y fecha, complementado con logs rotados (SLF4J + Logback).
