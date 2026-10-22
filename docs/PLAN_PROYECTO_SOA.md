# Plan de Proyecto SOA — "TechRetail S.A."

## Empresa Ficticia

**TechRetail S.A.** — Cadena de tiendas de tecnología y electrónica con presencia en 3 ciudades. Vende computadoras, celulares, accesorios. Tiene tienda física y online. Actualmente usa sistemas aislados (Excel, WhatsApp, sistema viejo en PHP) y necesita una arquitectura SOA para integrar todo.

**Contexto adicional:** TechRetail debe emitir boletas electrónicas ante SUNAT (Perú). El módulo de facturación es un sistema heredado que expone un servicio SOAP con XML, heredado de un proveedor externo. Esto justifica la coexistencia de REST y SOAP en la arquitectura.

---

## Stack Tecnológico

| Capa | Tecnología |
|------|-----------|
| Backend REST | **Spring Boot 3.x** + Java 17+ |
| Backend SOAP | **Spring Web Services (Spring-WS)** + Contract-First |
| BD | **PostgreSQL — una base de datos POR servicio** (`Database per Service`) |
| Migraciones DB | **Flyway** (un esquema versionado por servicio) |
| Mensajería **asíncrona** | **RabbitMQ** (colas de eventos, desacoplamiento) |
| Comunicación síncrona | **Spring Cloud OpenFeign** (llamada directa entre servicios) |
| Registro de servicios (UDDI) | **Catálogo de servicios** expuesto por el ESB (`publish / find / bind`) |
| Testing/API | **Postman** |
| Seguridad | **Spring Security + JWT** |
| Docs API REST | **SpringDoc OpenAPI** (Swagger UI automático) |
| Docs API SOAP | **WSDL** (auto-generado desde XSD por Spring-WS) |
| Logs/Auditoría | **SLF4J + Logback** + tabla `auditoria_logs` |

**Síncrono vs. Asíncrono** (tema obligatorio del sílabo):
- **Síncrono (REST + OpenFeign):** Cliente→Producto (consulta), Inventario (verifica stock antes de responder)
- **Asíncrono (RabbitMQ):** Notificaciones, alertas de stock bajo y eventos de pedido — nadie espera la respuesta

---

## Enfoque Contract-First para SOAP

El `FacturacionSunatService` usa el enfoque **Contract-First** (contrato primero), que es el recomendado para SOA:

**Por qué Contract-First:**
- El contrato (XSD) es la fuente de verdad, no el código
- Demuestra que se piensa en interoperabilidad antes de implementar
- Spring-WS auto-genera el WSDL completo a partir del XSD
- El profesor valora este enfoque en un curso de Arquitectura SOA

**Flujo Contract-First:**
```
1. Se define el XSD (estructura de datos)
        ↓
2. Spring-WS lee el XSD y genera el WSDL automáticamente
        ↓
3. Se expone el WSDL en una URL (ej: localhost:8080/ws/facturacion.wsdl)
        ↓
4. Se implementan las clases Java que procesan las peticiones SOAP
```

**Ejemplo de XSD (`facturacion.xsd`):**
```xml
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
           targetNamespace="http://techretail.com/facturacion">

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

**Stack adicional para SOAP:**
- `spring-boot-starter-web-services` — Soporte SOAP en Spring Boot
- `spring-ws-core` — Motor de Spring Web Services
- El XSD se ubica en `src/main/resources/wsdl/facturacion.xsd`

**Configuración del POM (`jaxb2-maven-plugin`):**
En el `pom.xml` de `techretail-facturacion-sunat`, incluir este plugin para que lea el XSD y genere las clases Java automáticamente:
```xml
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>jaxb2-maven-plugin</artifactId>
    <version>2.5.0</version>
    <executions>
        <execution>
            <goals>
                <goal>xjc</goal>
            </goals>
            <configuration>
                <sources>
                    <source>src/main/resources/wsdl</source>
                </sources>
                <outputDirectory>target/generated-sources/jaxb</outputDirectory>
            </configuration>
        </execution>
    </executions>
</plugin>
```
Esto genera `FacturaRequest.java`, `FacturaResponse.java` y otras clases en `target/generated-sources/jaxb` cada vez que compilás con `mvn compile`.

---

## Herramientas Adicionales

| Herramienta | Para qué |
|-------------|----------|
| **Git + GitHub** | Control de versiones (obligatorio) |
| **Docker + Docker Compose** | Levantar PostgreSQL en un comando, sin instalar nada |
| **draw.io** (gratis, online) | Diagramas BPMN y de arquitectura |
| **Lombok** | Reducir boilerplate en Java (getters, setters, constructors) |
| **SpringDoc OpenAPI** | Genera Swagger UI automático para servicios REST |
| **Flyway** | Migraciones de BD versionadas (scripts SQL en `src/main/resources/db/migration`) |
| **Postman Collections** | Exportar todas las peticiones como `.json` para que el profesor las importe y pruebe |

---

## Servicios a Implementar (9) — Cada uno con su propia base de datos

| # | Servicio | Protocolo | Formato | Responsabilidad | **Base de datos propia** | Tablas |
|---|----------|-----------|---------|----------------|--------------------------|--------|
| 1 | **ClienteService** | REST | JSON | CRUD de clientes, datos de contacto | `db_cliente` | `clientes` |
| 2 | **ProductoService** | REST | JSON | Catálogo de productos, precios, categorías | `db_producto` | `productos`, `categorias` |
| 3 | **PedidoService** | REST | JSON | Gestión de pedidos (crear, estado, historial) | `db_pedido` | `pedidos`, `pedido_items` |
| 4 | **PagoService** | REST | JSON | Procesamiento de pagos, estados | `db_pago` | `pagos` |
| 5 | **InventarioService** | REST | JSON | Stock por tienda, reservas, alertas | `db_inventario` | `inventarios`, `reservas`, `alertas` |
| 6 | **NotificacionService** | REST | JSON | Envío de confirmaciones (email/SMS mock) | `db_notificacion` | `notificaciones` |
| 7 | **FacturacionSunatService** | **SOAP** | **XML** | Emisión de boletas electrónicas SUNAT | `db_facturacion` | `facturas` |
| 8 | **AutenticacionService** | REST | JSON | Registro, login, emisión/validación de JWT, gestión de roles | `db_autenticacion` | `usuarios`, `roles` |
| 9 | **MonitoreoService** | REST | JSON | Health checks, estado/disponibilidad de servicios, tiempos de respuesta | `db_monitoreo` | `monitoreo_logs` |
| — | **ESB (techretail-gateway)** | — | — | Enrutamiento, transformación, JWT, auditoría, **catálogo UDDI** | `db_esb` | `auditoria_logs`, `servicios_registro` |

> **⚠️ Decisión de arquitectura (comentario del docente):** cada servicio **posee su propia base de datos** — patrón **`Database per Service`**. Ningún servicio accede directamente a las tablas de otro: la integración ocurre **únicamente vía servicios expuestos** (REST/SOAP) o por **mensajería (RabbitMQ)**. Esto garantiza **encapsulamiento, independencia de despliegue y evolución independiente del esquema**.

> **Nota académica:** El servicio SOAP (#7) representa un sistema heredado/externo, justificando la coexistencia de protocolos. Los servicios #8 y #9 agregan capa de seguridad transversal (autenticación) y gestión de operaciones (monitoreo), exigidos por el docente.

---

## Comunicación: Síncrona y Asíncrona (Unidad 1 del sílabo)

| Tipo | Tecnología | Caso de uso | ¿Quién espera? |
|------|-----------|-------------|-----------------|
| **Síncrona** | REST + OpenFeign | Consultar catálogo, verificar stock, procesar pago | El cliente **espera** la respuesta |
| **Asíncrona** | **RabbitMQ** (cola `techretail.notificaciones`) | Notificar al cliente, alertar stock bajo, avisar al gerente | El emisor **NO espera**: publica el evento y sigue |

**Eventos asíncronos del proyecto:**

| Evento | Publica | Cola | Consume | Acción |
|--------|---------|------|---------|--------|
| `pedido.confirmado` | PedidoService | `techretail.notificaciones` | NotificacionService | Envía email/SMS de confirmación |
| `pago.aprobado` | PagoService | `techretail.notificaciones` | NotificacionService | Envía comprobante de pago |
| `stock.bajo` | InventarioService | `techretail.alertas` | NotificacionService | Avisa al gerente para reposición |

> **Por qué importa académicamente:** el sílabo exige explícitamente **"Sincronismo vs. Asincronismo"**. Sin este diferenciador, el proyecto se lee como "10 APIs REST" y no como una **arquitectura orientada a servicios**. Además demuestra **desacoplamiento temporal**: si NotificacionService está caído, el pedido se sigue procesando igual.

---

## Arquitectura de Datos — Base de Datos por Servicio (`Database per Service`)

> **Decisión del docente:** cada módulo/servicio con su **propia base de datos**. Una sola BD compartida rompe el encapsulamiento: cualquier cambio de esquema en un servicio afectaría a todos.

### Distribución de bases de datos

| # | Servicio | Base de datos | Migraciones Flyway |
|---|----------|---------------|--------------------|
| 1 | ClienteService | `db_cliente` | `techretail-cliente/src/main/resources/db/migration/` |
| 2 | ProductoService | `db_producto` | `techretail-producto/src/main/resources/db/migration/` |
| 3 | PedidoService | `db_pedido` | `techretail-pedido/src/main/resources/db/migration/` |
| 4 | PagoService | `db_pago` | `techretail-pago/src/main/resources/db/migration/` |
| 5 | InventarioService | `db_inventario` | `techretail-inventario/src/main/resources/db/migration/` |
| 6 | NotificacionService | `db_notificacion` | `techretail-notificacion/src/main/resources/db/migration/` |
| 7 | FacturacionSunatService | `db_facturacion` | `techretail-facturacion-sunat/src/main/resources/db/migration/` |
| 8 | AutenticacionService | `db_autenticacion` | `techretail-autenticacion/src/main/resources/db/migration/` |
| 9 | MonitoreoService | `db_monitoreo` | `techretail-monitoreo/src/main/resources/db/migration/` |
| — | ESB / Gateway | `db_esb` | `techretail-gateway/src/main/resources/db/migration/` |

**Total: 10 bases de datos** dentro de **un solo contenedor PostgreSQL** (no hace falta 10 contenedores — se crean con un script de inicialización).

### docker-compose.yml

```yaml
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_USER: techretail
      POSTGRES_PASSWORD: techretail123
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
      - ./docker/postgres-init.sql:/docker-entrypoint-initdb.d/init.sql  # crea las 10 BD

  rabbitmq:
    image: rabbitmq:3-management
    ports:
      - "5672:5672"    # AMQP
      - "15672:15672"  # Panel de administración web

  pgadmin:
    image: dpage/pgadmin4
    ports:
      - "5050:80"

volumes:
  pgdata:
```

### docker/postgres-init.sql (creación de las 10 BD al arrancar)

```sql
CREATE DATABASE db_cliente;
CREATE DATABASE db_producto;
CREATE DATABASE db_pedido;
CREATE DATABASE db_pago;
CREATE DATABASE db_inventario;
CREATE DATABASE db_notificacion;
CREATE DATABASE db_facturacion;
CREATE DATABASE db_autenticacion;
CREATE DATABASE db_monitoreo;
CREATE DATABASE db_esb;
```

### Configuración por servicio (`application.yml` de ClienteService)

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/db_cliente
    username: techretail
    password: techretail123
  flyway:
    enabled: true
    locations: classpath:db/migration
```

Cada servicio apunta **solo a su BD**. Si PedidoService intentara consultar `db_cliente` directamente, **estaría violando la arquitectura**.

### Reglas de la arquitectura de datos

1. **Cada servicio es dueño de sus datos** — solo él escribe y lee su BD
2. **Nunca hay JOINs entre bases de datos de servicios distintos** — la integración es vía API o mensajería
3. **Cada servicio tiene sus propias migraciones Flyway** (V1, V2, V3... independientes por servicio)
4. **Se desacoplan:** se puede migrar la BD de Pago sin tocar la de Pedido
5. **Réplicas/consultas cruzadas:** si PedidoService necesita datos de Cliente, hace un **GET /api/clientes/{id} vía el BUS**, no un `SELECT` sobre `db_cliente`

> **Ejemplo para sustentar:** "Si mañana cambiamos el esquema de `clientes` (ej: separar nombres y apellidos), solo se migra `db_cliente`. `db_pedido` no se entera — su FK guarda el `idCliente`, no los datos."

---

## Detalle de Entradas/Salidas por Servicio

El inventario formal del Avance 2 debe incluir estos esquemas detallados. Ejemplo de JSON con la estructura completa:

**ClienteService — POST /api/clientes**

```json
// ENTRADA
{
  "nombres": "María Fernández",
  "tipoDocumento": "DNI",
  "numeroDocumento": "40214587",
  "email": "maria.fernandez@email.com",
  "telefono": "+51999888777",
  "direccion": "Av. Larco 123, Miraflores",
  "ciudad": "Lima"
}

// SALIDA (201 Created)
{
  "idCliente": 1,
  "nombres": "María Fernández",
  "tipoDocumento": "DNI",
  "numeroDocumento": "40214587",
  "email": "maria.fernandez@email.com",
  "telefono": "+51999888777",
  "direccion": "Av. Larco 123, Miraflores",
  "ciudad": "Lima",
  "fechaRegistro": "2026-09-06T10:30:00Z",
  "estado": "ACTIVO"
}
```

**PedidoService — POST /api/pedidos**

```json
// ENTRADA
{
  "idCliente": 1,
  "idTienda": 2,
  "items": [
    { "idProducto": 10, "cantidad": 2, "precioUnitario": 2499.99 },
    { "idProducto": 15, "cantidad": 1, "precioUnitario": 599.00 }
  ],
  "total": 5598.98,
  "metodoPago": "TARJETA"
}

// SALIDA (201 Created)
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
  "fechaCreacion": "2026-09-06T10:35:12Z"
}
```

**PagoService — POST /api/pagos**

```json
// ENTRADA
{
  "idPedido": 1001,
  "monto": 6606.80,
  "metodoPago": "TARJETA",
  "numeroTarjeta": "4111111111111111",
  "titular": "MARIA FERNANDEZ"
}

// SALIDA (200 OK)
{
  "idPago": 501,
  "idPedido": 1001,
  "monto": 6606.80,
  "estado": "APROBADO",
  "operacion": "AUTH-20260906-000123",
  "fecha": "2026-09-06T10:35:45Z"
}
```

**InventarioService — POST /api/inventario/reservar**

```json
// ENTRADA
{
  "idProducto": 10,
  "idTienda": 2,
  "cantidad": 2,
  "idPedido": 1001
}

// SALIDA (200 OK)
{
  "idReserva": 77,
  "idProducto": 10,
  "idTienda": 2,
  "cantidadReservada": 2,
  "stockAnterior": 15,
  "stockActual": 13,
  "estado": "RESERVADO"
}

// SALIDA ALTERNATIVA (409 Conflict — stock insuficiente)
{
  "codigo": "STOCK_INSUFICIENTE",
  "mensaje": "Solo hay 1 unidad disponible en la tienda",
  "stockDisponible": 1
}
```

**AutenticacionService — POST /api/auth/login**

```json
// ENTRADA
{
  "usuario": "maria.fernandez@email.com",
  "password": "Clave123!"
}

// SALIDA (200 OK)
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tipo": "Bearer",
  "expiraEn": 3600,
  "rol": "CLIENTE",
  "idUsuario": 8,
  "nombre": "María Fernández"
}
```

**MonitoreoService — GET /api/monitoreo/estado**

```json
// SALIDA (200 OK)
{
  "fechaConsulta": "2026-09-06T11:00:00Z",
  "servicios": [
    { "nombre": "ClienteService", "estado": "UP", "tiempoRespuestaMs": 45 },
    { "nombre": "ProductoService", "estado": "UP", "tiempoRespuestaMs": 38 },
    { "nombre": "PedidoService", "estado": "UP", "tiempoRespuestaMs": 52 },
    { "nombre": "PagoService", "estado": "UP", "tiempoRespuestaMs": 120 },
    { "nombre": "InventarioService", "estado": "UP", "tiempoRespuestaMs": 41 },
    { "nombre": "NotificacionService", "estado": "UP", "tiempoRespuestaMs": 33 },
    { "nombre": "FacturacionSunatService", "estado": "DOWN", "tiempoRespuestaMs": 0 },
    { "nombre": "AutenticacionService", "estado": "UP", "tiempoRespuestaMs": 29 }
  ]
}
```

**FacturacionSunatService — SOAP Request/Response**

```xml
<!-- ENTRADA (SOAP Envelope) -->
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

<!-- SALIDA (SOAP Envelope) -->
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
  <soapenv:Body>
    <fac:FacturaResponse>
      <numeroFactura>F001-00045821</numeroFactura>
      <estado>ACEPTADA</estado>
      <fechaEmision>2026-09-06</fechaEmision>
    </fac:FacturaResponse>
  </soapenv:Body>
</soapenv:Envelope>
```

---

## Rol del BUS / ESB (Enterprise Service Bus) — Direccionamiento

> **Aclaración:** el docente habla de **"BUS"** = **Enterprise Service Bus (ESB)**, término literal del sílabo (*"Infraestructura de los servicios en bus"*). **No es un BUSS ni solo un API Gateway**: es el bus por donde **circulan y se direccionan** los mensajes entre servicios.

El **techretail-gateway** es el BUS de TechRetail. Además de mediador, **enruta con tabla de direccionamiento explícita** y transforma mensajes:

### Tabla de Direccionamiento (Routing)

| Ruta de entrada (cliente) | Método | Servicio destino | Puerto | Formato interno | Notas |
|---------------------------|--------|------------------|--------|-----------------|-------|
| `/api/clientes/**` | GET/POST/PUT/DELETE | ClienteService | 8081 | JSON | Requiere JWT |
| `/api/productos/**` | GET/POST/PUT/DELETE | ProductoService | 8082 | JSON | GET público |
| `/api/pedidos/**` | GET/POST/PUT/DELETE | PedidoService | 8083 | JSON | Requiere JWT |
| `/api/pagos/**` | GET/POST | PagoService | 8084 | JSON | Requiere JWT + rol ADMIN/CLIENTE |
| `/api/inventario/**` | GET/POST/PUT | InventarioService | 8085 | JSON | Requiere JWT + rol EMPLEADO/ADMIN |
| `/api/notificaciones/**` | GET/POST | NotificacionService | 8086 | JSON | Requiere JWT |
| `/ws/facturacion` | POST (SOAP) | FacturacionSunatService | 8087 | **XML** | **El BUS transforma JSON→XML** |
| `/api/auth/**` | POST | AutenticacionService | 8088 | JSON | **Único path sin JWT** (login/registro) |
| `/api/monitoreo/**` | GET | MonitoreoService | 8089 | JSON | Requiere rol ADMIN |
| `/api/registro/servicios` | GET | **Catálogo UDDI (en el BUS)** | 8090 | JSON | **Público — descubrimiento** |

**Reglas de direccionamiento del BUS:**
1. **Ruta exacta por prefijo** → redirige al servicio correspondiente (tabla anterior)
2. **Transformación de formato:** JSON ↔ XML cuando el destino es SOAP
3. **Validación de JWT** en un solo punto (el bus) antes de enrutar
4. **Auditoría:** cada petición cruzada queda registrada en `auditoria_logs`
5. **Si el servicio destino está caído:** responde `503 Service Unavailable` (lo detecta MonitoreoService)
6. **Registro dinámico:** los servicios **se publican** en el bus al arrancar (ver UDDI abajo)

### Catálogo de Servicios (UDDI)

El sílabo exige **"Registro de Servicios — UDDI"**. Como UDDI real es tecnología legacy, se implementa un **catálogo REST con las 3 operaciones clásicas**:

| Operación UDDI | Implementación | Descripción |
|----------------|----------------|-------------|
| **Publish** | `POST /api/registro/servicios` | Cada servicio se registra al arrancar (nombre, URL, WSDL, versión) |
| **Find** | `GET /api/registro/servicios` | Lista servicios disponibles (descubrimiento) |
| **Bind** | El ESB usa la URL registrada | Enruta la petición al servicio registrado |

```json
// GET /api/registro/servicios (catálogo)
[
  { "nombre": "PedidoService", "url": "http://pedido:8083", "protocolo": "REST",
    "formato": "JSON", "wsdl": null, "version": "1.0", "estado": "UP" },
  { "nombre": "FacturacionSunatService", "url": "http://facturacion:8087",
    "protocolo": "SOAP", "formato": "XML",
    "wsdl": "http://facturacion:8087/ws/facturacion.wsdl", "version": "1.0", "estado": "UP" }
]
```

> Guardado en la tabla `servicios_registro` (BD `db_esb` del propio BUS).

---

### Funciones del BUS/ESB
- **Enrutamiento (direccionamiento):** redirige según la tabla de rutas al microservicio correcto
- **Transformación:** convierte JSON ↔ XML cuando REST necesita hablar con SOAP
- **Registro/descubrimiento:** catálogo UDDI de los servicios (publish/find/bind)
- **Seguridad:** valida tokens JWT en un solo punto de entrada
- **Log de auditoría:** registra todas las peticiones que cruzan el bus

**Transformación JSON→XML en el BUS:**

```
Cliente (JSON) → Gateway → [transforma JSON a XML] → FacturacionSunatService (SOAP/XML)
FacturacionSunatService (XML) → Gateway → [transforma XML a JSON] → PedidoService (REST/JSON)
```

**Diagrama de Secuencia — Transformación JSON→XML en el ESB:**

```
┌─────────┐     ┌─────────┐     ┌──────────────┐     ┌─────────────────────┐
│ Cliente │     │ Gateway │     │ PedidoService│     │ FacturacionSunat    │
│ (REST)  │     │  (ESB)  │     │   (REST)     │     │   (SOAP/XML)        │
└────┬────┘     └────┬────┘     └──────┬───────┘     └──────────┬──────────┘
     │  POST /pedidos│                 │                        │
     │  {JSON}       │                 │                        │
     │──────────────>│                 │                        │
     │               │  POST /pedidos  │                        │
     │               │  {JSON}         │                        │
     │               │────────────────>│                        │
     │               │                 │  Crear pedido          │
     │               │                 │  OK {pedidoId: 1}      │
     │               │<────────────────│                        │
     │               │                 │                        │
     │               │  POST /ws/      │                        │
     │               │  facturacion    │  <soap:Envelope>       │
     │               │  [transforma    │  <soap:Body>           │
     │               │   JSON→XML]     │  <FacturaRequest>      │
     │               │─────────────────────────────────────────>│
     │               │                 │                        │  Emitir boleta
     │               │                 │                        │  SUNAT
     │               │                 │                        │
     │               │                 │  <soap:Envelope>       │
     │               │  [transforma    │  <soap:Body>           │
     │               │   XML→JSON]     │  <FacturaResponse>     │
     │               │<─────────────────────────────────────────│
     │               │                 │                        │
     │  201 Created  │                 │                        │
     │  {pedido,     │                 │                        │
     │   factura}    │                 │                        │
     │<──────────────│                 │                        │
```

> **Nota para sustentación:** Tener este diagrama en draw.io listo para mostrar en la presentación final. Los docentes valoran ver gráficamente cómo el bus intercepta, transforma y enruta.

---

## Diagramas As-Is y To-Be (Estado Actual vs. Estado Objetivo)

El Avance 1 debe incluir ambos diagramas de arquitectura, obligatorio según el docente. Realizarlos en draw.io con el mismo estilo visual (mismas formas, colores y leyenda) para que el contraste antes/después sea evidente.

### Arquitectura As-Is (Estado Actual)

Sistema actual de TechRetail: aplicaciones aisladas sin integración formal.

```
┌────────────────────────────────────────────────────────────────────────────┐
│                        ARQUITECTURA ACTUAL — AS-IS                         │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌───────────────┐      ┌───────────────┐      ┌───────────────────┐        │
│  │  Sistema PHP   │      │  Hojas Excel   │      │    WhatsApp       │        │
│  │  (Monolítico)  │      │  por tienda    │      │ (sin trazabilidad)│        │
│  │  · Ventas      │      │  · Inventario  │      │  · Coordinación   │        │
│  │  · Clientes    │      │  · Stock       │      │  · Pedidos inter  │        │
│  │  · Pedidos     │      │                │      │    tienda-almacén │        │
│  └───────┬───────┘      └──────┬──────────┘      └────────┬──────────┘       │
│          │                     │                          │                 │
│          │      Datos duplicados / sin sincronización     │                 │
│          ▼                     ▼                          ▼                 │
│  ┌─────────────────────────────────────────────────────────────────┐        │
│  │            SIN INTEGRACIÓN FORMAL ENTRE SISTEMAS                │        │
│  │  · Sin ESB · Sin API · Sin monitoreo · Sin auditoría central    │        │
│  └─────────────────────────────────────────────────────────────────┘        │
│          │                                                                   │
│          ▼                                                                   │
│  ┌───────────────────────┐         ┌────────────────────────────┐            │
│  │  Proveedor SUNAT      │         │  Duplicidad de clientes    │            │
│  │  (SOAP/XML heredado)  │         │  (física, web, Excel)      │            │
│  │  · Incompatible con   │         └────────────────────────────┘            │
│  │    sistemas JSON      │                                                 │
│  └───────────────────────┘                                                 │
│                                                                             │
└────────────────────────────────────────────────────────────────────────────┘
```

**Problemas visibles en el As-Is:**
1. Sin punto único de entrada (cada sistema aislado se conecta directo al proveedor SUNAT)
2. Inventario en Excel sin sincronización → quiebres de stock y sobreventa
3. Coordinación por WhatsApp → sin trazabilidad ni auditoría
4. Clientes duplicados entre canales → inconsistencias
5. Sin mecanismo de monitoreo de disponibilidad
6. Sin autenticación centralizada → cada sistema maneja credenciales a su manera

### Arquitectura To-Be (Estado Objetivo)

Sistema objetivo: arquitectura SOA con ESB como mediador central y 9 servicios.

```
┌────────────────────────────────────────────────────────────────────────────────┐
│                     ARQUITECTURA OBJETIVO — TO-BE                             │
├────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌──────────────────────┐    │
│  │  Web Store  │  │  App Móvil  │  │ Tienda Física│ │  Panel Empleado      │    │
│  │             │  │             │  │  (POS)       │ │  (JSON + JWT)        │    │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘  └──────────┬───────────┘    │
│         │                │                │                   │               │
│         └────────────────┴────────────────┴───────────────────┘               │
│                                    │                                            │
│                        ┌───────────▼────────────┐                               │
│                        │   ESB / API GATEWAY    │                               │
│                        │   techretail-gateway   │                               │
│                        │  ┌──────────────────┐  │                               │
│                        │  │ · Enrutamiento   │  │                               │
│                        │  │ · Transformación │  │                               │
│                        │  │   JSON ↔ XML     │  │                               │
│                        │  │ · Autenticación  │  │                               │
│                        │  │   JWT            │  │                               │
│                        │  │ · Auditoría      │  │                               │
│                        │  └──────┬───────────┘  │                               │
│                        └─────────┼──────────────┘                               │
│                                  │                                               │
│  ┌───────────────┬───────────────┼───────────────┬───────────────┐               │
│  │               │               │               │               │               │
│  ▼               ▼               ▼               ▼               ▼               │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐                    │
│  │Cliente  │ │Producto │ │ Pedido  │ │  Pago   │ │Inventar │                    │
│  │Service  │ │Service  │ │ Service │ │ Service │ │ioService│                    │
│  │ REST    │ │ REST    │ │ REST    │ │ REST    │ │ REST    │                    │
│  └────┬────┘ └────┬────┘ └────┬────┘ └────┬────┘ └────┬────┘                    │
│       │           │           │           │           │                           │
│  ┌────┴────┐ ┌────┴────┐ ┌────┴────┐ ┌────┴────┐ ┌────┴────────┐                │
│  │Notifica│ │Facturac│ │Autentic│ │Monitoreo│ │  SUNAT       │                │
│  │Service │ │ionSunat│ │Service │ │Service  │ │  (SOAP/XML)  │                │
│  │ REST   │ │ (SOAP) │ │ REST   │ │ REST    │ │  externo     │                │
│  └────────┘ └─────────┘ └────────┘ └─────────┘ └─────────────┘                │
│                                                                                  │
│  ┌──────────────────────────────────────────────────────────────────────────┐   │
│  │          PostgreSQL — 1 BASE DE DATOS POR SERVICIO (Database per Service)│   │
│  │  db_cliente · db_producto · db_pedido · db_pago · db_inventario            │   │
│  │  db_notificacion · db_facturacion · db_autenticacion · db_monitoreo        │   │
│  │  db_esb (auditoria_logs · servicios_registro)                             │   │
│  └──────────────────────────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────┐                                                 │
│  │  RabbitMQ (colas async)  │  → techretail.notificaciones · techretail.alertas│
│  └──────────────────────────┘                                                 │
└────────────────────────────────────────────────────────────────────────────────┘
```

**Cambios que resuelve el To-Be:**
1. Punto único de entrada (BUS/ESB) para todos los canales, con **tabla de direccionamiento**
2. Transformación JSON↔XML en el BUS → coexistencia ordenada REST/SOAP
3. **Base de datos por servicio** (`Database per Service`) → encapsulamiento e independencia de despliegue
4. Autenticación JWT centralizada (AutenticacionService)
5. Monitoreo permanente de disponibilidad (MonitoreoService)
6. Auditoría completa de cada transacción (auditoria_logs)
7. **Comunicación asíncrona (RabbitMQ)** → notificaciones y alertas sin bloquear el flujo principal
8. **Catálogo de servicios (UDDI)** → publicación y descubrimiento en el BUS

---

## Procesos de Negocio (3)

### Proceso 1 — Compra Online (principal)

```
Cliente
  → ProductoService (consulta catálogo)                         [SÍNCRONO]
  → PedidoService (crea pedido)                                 [SÍNCRONO]
  → InventarioService (reserva stock)                           [SÍNCRONO]
  → PagoService (procesa pago)                                  [SÍNCRONO]
  → FacturacionSunatService (emite boleta SUNAT via SOAP)       [SÍNCRONO + transformación JSON→XML]
  → publica evento "pedido.confirmado" en RabbitMQ              [ASÍNCRONO]
  → NotificacionService consume el evento y envía confirmación  [ASÍNCRONO]
```

### Proceso 2 — Devolución de Producto

```
Cliente
  → PedidoService (solicita devolución)                         [SÍNCRONO]
  → PagoService (reembolso)                                     [SÍNCRONO]
  → InventarioService (devuelve stock)                          [SÍNCRONO]
  → publica evento "devolucion.procesada" → RabbitMQ            [ASÍNCRONO]
  → NotificacionService notifica al cliente                     [ASÍNCRONO]
```

### Proceso 3 — Abastecimiento de Inventario (B2B)

```
Empleado (detecta stock bajo)
  → InventarioService (genera alerta + publica "stock.bajo")    [SÍNCRONO + ASÍNCRONO]
  → PedidoService (genera orden de compra a proveedor)          [SÍNCRONO]
  → PagoService (emite pago B2B a proveedor)                    [SÍNCRONO]
  → publica "pago.b2b.emitido" → RabbitMQ                       [ASÍNCRONO]
  → NotificacionService avisa al gerente                        [ASÍNCRONO]
```

---

## Modelo de Seguridad y Autenticación

| Rol | Permisos |
|-----|----------|
| `ADMIN` | Acceso total a todos los servicios, monitoreo y configuración |
| `EMPLEADO` | Gestión de inventario, pedidos internos, abastecimiento |
| `CLIENTE` | Consulta catálogo, crea pedidos, consulta estado, solicita devoluciones |

**Flujo de autenticación completo (AutenticacionService):**

1. **Registro:** POST `/api/auth/registro` → crea usuario + asigna rol
2. **Login:** POST `/api/auth/login` → valida credenciales → devuelve token JWT
3. **Consumo:** el cliente envía el token en header `Authorization: Bearer <token>`
4. **Validación:** el ESB/Gateway valida firma, expiración y rol antes de enrutar
5. **Endpoints protegidos:** cada servicio verifica roles con `@PreAuthorize`
   - `ADMIN` y `EMPLEADO` → gestión de usuarios, inventario, monitoreo
   - `CLIENTE` → solo sus propios pedidos y datos

---

## Auditoría

Cada consumo de servicio que cruza el BUS se registra en la tabla `auditoria_logs` (ubicada en `db_esb`, la base de datos del propio ESB):

```sql
CREATE TABLE auditoria_logs (
    id BIGSERIAL PRIMARY KEY,
    servicio_origen VARCHAR(50) NOT NULL,
    servicio_destino VARCHAR(50) NOT NULL,
    operacion VARCHAR(10) NOT NULL,        -- GET, POST, PUT, DELETE
    endpoint VARCHAR(200) NOT NULL,
    estado_respuesta INT NOT NULL,         -- 200, 400, 500, etc.
    usuario VARCHAR(100),
    timestamp TIMESTAMP DEFAULT NOW(),
    detalle TEXT
);
```

Además, SLF4J + Logback genera archivos `.log` rotados diariamente en `logs/techretail.log`.

---

## Gotchas de Flyway (Importante)

**⚠️ Checksum mismatch:** Si modificás un script SQL que Flyway ya ejecutó (ej: `V1__create_tables.sql`), te saltará un error de checksum. **NUNCA** modificar un archivo SQL ya ejecutado.

**Regla:** Siempre crear un nuevo archivo versionado:

```
techretail-cliente/src/main/resources/db/migration/     → db_cliente
├── V1__create_clientes.sql
├── V2__add_index_email.sql

techretail-pedido/src/main/resources/db/migration/       → db_pedido
├── V1__create_pedidos.sql
├── V2__create_pedido_items.sql
```

> Cada servicio versiona **su propia BD** con independencia total. PedidoService puede tener V5 mientras ClienteService sigue en V2 — los esquemas no se mezclan.

**Si perdés la BD en desarrollo local** y necesitás re-ejecutar todo:
```bash
mvn flyway:clean    # Borra la BD (¡SOLO en desarrollo!)
mvn flyway:migrate  # Re-ejecuta todas las migraciones
```

---

## Cobertura del Sílabo (matriz de verificación)

Cruce obligatorio: **cada tema del sílabo debe tener un entregable en el proyecto**.

### Unidad 1 — Fundamentos de SOA (Semanas 1-5) → Avance 1

| Tema del sílabo | Estado | Dónde se cubre |
|---|---|---|
| Fundamentos, objetivos y beneficios de SOA | ✅ | Resumen ejecutivo + oportunidades |
| Orientación a servicios y terminología | ✅ | Todo el plan |
| **Ciclo de vida de servicios** | ✅ | Documento: plan → diseño → implementación → monitoreo → retiro (Avance 2) |
| Tipos de servicios (entidad, utilidad, tarea, proceso) | ✅ | Tabla de 9 servicios clasificada |
| **Buenas prácticas en diseño de servicios** | ✅ | Principios aplicados en cada servicio (Avance 2) |
| Seguridad en los servicios | ✅ | JWT + roles + endpoints protegidos |
| **Sincronismo vs. Asincronismo** | ✅ | Sección "Comunicación: Síncrona y Asíncrona" (RabbitMQ) |
| **Stateless vs. Stateful / encapsulación** | ✅ | Stateless + Database per Service (Avance 2) |
| Interoperabilidad / remoto | ✅ | REST + SOAP coexisten vía BUS |
| XML y XML Schema | ✅ | XSD Contract-First |
| **XSLT, XQuery, XPath** | ✅ | Transformación JSON↔XML en el BUS (equivale a XSLT) |
| **UDDI** | ✅ | Catálogo publish/find/bind en el BUS |
| WSDL, SOAP, JSON | ✅ | Completos |
| **Principios y tipos de arquitectura SOA** | ✅ | Arquitectura en capas (Avance 2) |

### Unidad 2 — Diseño y Arquitectura (Semanas 6-10) → Avance 2

| Tema | Estado | Dónde se cubre |
|---|---|---|
| Inventario de Servicios | ✅ | Tabla de 9 servicios con E/S detalladas |
| Diseño de Servicios | ✅ | Especificaciones OpenAPI + XSD |
| Diseño de Composición de Servicios | ✅ | Diagrama de composición/orquestación |
| **Enterprise Service Bus** | ✅ | Sección "Rol del BUS/ESB" con tabla de direccionamiento |
| Orquestación de Servicios | ✅ | BPMN + diagrama de secuencia |
| **Capas de SOA** | ✅ | Presentación → Negocio → Integración (BUS) → Datos |

### Unidad 3 — Programación Distribuida (Semanas 11-18) → Avance 3 + Final

| Tema | Estado | Dónde se cubre |
|---|---|---|
| Implementación de SOA / servicios web | ✅ | Spring Boot + Spring-WS |
| Tipos de servicios | ✅ | REST, SOAP, asíncrono (colas) |
| **Modelos de creación de servicios web** | ✅ | Contract-First (XSD→WSDL) + code-first REST |
| **Registro de Servicios — UDDI** | ✅ | Catálogo en el BUS (publish/find/bind) |
| Integración de procesos empresariales | ✅ | 3 procesos de negocio |
| **Business to Business** | ✅ | Proceso 3 (abastecimiento B2B) |
| **BPM y su relación con SOA** | ✅ | BPMN ↔ orquestación de servicios (Avance 2) |
| **Integridad de procesos / mejores prácticas SOA** | ✅ | Manejo de errores transaccionales (Avance 3) |
| Infraestructura de servicios en bus / Servicio bus | ✅ | BUS/ESB con direccionamiento |
| Auditoría | ✅ | `auditoria_logs` + SLF4J/Logback |

---

## Mapeo a las 4 Entregas

### Avance 1 (Semana 5) — Análisis y Definición

- [ ] Carátula + Resumen Ejecutivo de TechRetail
- [ ] Situación actual: sistemas aislados, problemas de inventario, pedidos por WhatsApp
- [ ] **Diagrama As-Is** (arquitectura actual con los problemas)
- [ ] **Diagrama To-Be** (arquitectura objetivo SOA con 9 servicios)
- [ ] Diagrama BPMN completo del **Proceso de Compra**
- [ ] Definición de los **3 procesos de negocio**
- [ ] Definición preliminar de los **9 servicios candidatos** (nombre, descripción, tipo, E/S, protocolo, formato)
- [ ] **Base de datos asignada por servicio** (`Database per Service` — 1 BD por módulo)
- [ ] **Clasificación de tipos de servicios** (entidad, utilidad, tarea, proceso)
- [ ] Mapa de arquitectura inicial (cajas: clientes, servicios, BUS/ESB, bases de datos por servicio)

### Avance 2 (Semana 10) — Diseño de Arquitectura

- [ ] **Inventario de Servicios** (tabla completa: nombre, descripción, **E/S detalladas**, protocolo REST/SOAP, formato XML/JSON, **BD propia**)
- [ ] Arquitectura SOA en capas: Presentación → Negocio → Integración (BUS) → Datos
- [ ] **Ciclo de vida de servicios** (planificación → diseño → implementación → despliegue → monitoreo → retiro)
- [ ] **Principios y buenas prácticas de diseño de servicios** aplicados (stateless, encapsulamiento, bajo acoplamiento, alta cohesión)
- [ ] **Síncrono vs. asíncrono:** diseño de colas RabbitMQ (qué eventos, quién publica, quién consume)
- [ ] **BUS/ESB con tabla de direccionamiento** (ruta → servicio → puerto → formato)
- [ ] **Catálogo de servicios (UDDI):** operaciones publish / find / bind
- [ ] Diseño del BUS: Spring Cloud Gateway como mediador con transformación de mensajes (JSON ↔ XML)
- [ ] Diagrama de composición/orquestación del Proceso de Compra (incluye servicio SOAP)
- [ ] **Relación BPM ↔ SOA:** cómo los BPMN se orquestan sobre los servicios
- [ ] **Especificación técnica OpenAPI** de 2 servicios REST (PedidoService y PagoService) **con ejemplos de request/response JSON**
- [ ] **Contrato XSD** del FacturacionSunatService (Contract-First: definir estructura antes del código)
- [ ] **WSDL auto-generado** a partir del XSD por Spring-WS (mostrar URL de exposición)
- [ ] Modelo de seguridad JWT: **flujo de autenticación completo** (registro → login → token → consumo protegido)

### Avance 3 (Semana 15) — Implementación Parcial

- [ ] **ClienteService** y **ProductoService** implementados en Spring Boot (REST/JSON)
- [ ] **Cada servicio conectado a SU propia BD** (`db_cliente`, `db_producto`) con Flyway independiente
- [ ] **AutenticacionService** implementado (registro + login + JWT)
- [ ] Capturas de Postman (GET, POST, PUT, DELETE) con JSON
- [ ] Explicación de conexión con PostgreSQL (**1 BD por servicio**)
- [ ] **Demostración de asincronismo:** publicar un evento en RabbitMQ y consumirlo (producer → consumer)
- [ ] **FacturacionSunatService** implementado con Contract-First (Spring-WS + XSD)
- [ ] **Captura de WSDL** generado automáticamente (localhost:8080/ws/facturacion.wsdl)
- [ ] **Invocación SOAP** del FacturacionSunatService con Postman (request XML → response XML)
- [ ] **Exportar Colección Postman** como `.json` y guardar en `docs/postman_collection/techretail-avance3.json`
- [ ] **Catálogo UDDI** funcional: registrar y consultar servicios
- [ ] Lecciones aprendidas / dificultades encontradas

### Proyecto Final (Semana 18) — Sistema Completo

- [ ] Los **9 servicios** implementados y funcionando (8 REST + 1 SOAP Contract-First)
- [ ] **10 bases de datos** (9 servicios + BUS) — `Database per Service` documentado
- [ ] **RabbitMQ:** eventos `pedido.confirmado`, `pago.aprobado`, `stock.bajo` publicados y consumidos
- [ ] **Catálogo UDDI** completo (publish/find/bind) expuesto por el BUS
- [ ] Arquitectura final con diagrama de capas completo (incluye BUS/ESB)
- [ ] **Diagramas As-Is y To-Be** pulidos en draw.io
- [ ] BPMN final de los 3 procesos de negocio
- [ ] **Diagrama de secuencia del BUS** (transformación JSON→XML, en draw.io)
- [ ] Seguridad JWT implementada (registro, login, roles, tokens, **endpoints protegidos**)
- [ ] **MonitoreoService** implementado (health checks de los 8 servicios, tiempos de respuesta)
- [ ] **Auditoría:** tabla `auditoria_logs` + archivos `.log` con SLF4J
- [ ] Capturas de Postman de todos los servicios (REST con JSON y SOAP con XML)
- [ ] **Colección Postman completa** exportada como `.json` en `docs/postman_collection/techretail-final.json`
- [ ] **Orquestación documentada** (cómo el BUS transforma JSON→XML para SOAP)
- [ ] **XSD del contrato** + **WSDL auto-generado** por Spring-WS
- [ ] **POM con `jaxb2-maven-plugin`** configurado para generar clases desde XSD
- [ ] Flyway: migraciones **por servicio** documentadas (V1, V2, V3... en cada módulo)
- [ ] **Cobertura del sílabo:** matriz de verificación completada (tema → entregable)
- [ ] Conclusiones y recomendaciones

---

## Estructura del Proyecto

```
techretail-soa/
├── docker-compose.yml                  # PostgreSQL (10 BD) + RabbitMQ + pgAdmin
├── docker/
│   └── postgres-init.sql               # Crea las 10 bases de datos al arrancar
├── techretail-gateway/                 # BUS/ESB: enrutamiento, transformación, JWT, auditoría, catálogo UDDI
│   └── src/main/resources/db/migration/   → db_esb (auditoria_logs, servicios_registro)
├── techretail-cliente/                 # ClienteService (REST/JSON)
│   └── src/main/resources/db/migration/   → db_cliente
├── techretail-producto/                # ProductoService (REST/JSON)
│   └── src/main/resources/db/migration/   → db_producto
├── techretail-pedido/                  # PedidoService (REST/JSON)
│   └── src/main/resources/db/migration/   → db_pedido
├── techretail-pago/                    # PagoService (REST/JSON)
│   └── src/main/resources/db/migration/   → db_pago
├── techretail-inventario/              # InventarioService (REST/JSON)
│   └── src/main/resources/db/migration/   → db_inventario
├── techretail-notificacion/            # NotificacionService (REST/JSON + consumidor RabbitMQ)
│   └── src/main/resources/db/migration/   → db_notificacion
├── techretail-facturacion-sunat/       # FacturacionSunatService (SOAP/XML - Contract-First)
│   └── src/main/resources/
│       ├── wsdl/facturacion.xsd        # ← Contrato XSD (fuente de verdad)
│       └── db/migration/               # → db_facturacion
├── techretail-autenticacion/           # AutenticacionService (REST/JSON + JWT)
│   └── src/main/resources/db/migration/   → db_autenticacion
├── techretail-monitoreo/               # MonitoreoService (REST/JSON + health checks)
│   └── src/main/resources/db/migration/   → db_monitoreo
├── docs/
│   ├── diagramas-bpmn/                 # Diagramas de los 3 procesos
│   ├── diagrama-arquitectura/          # Diagrama de capas SOA (To-Be)
│   ├── diagrama-as-is/                 # Diagrama de arquitectura actual
│   ├── diagrama-esb-sequence/          # Diagrama de secuencia del BUS (draw.io)
│   ├── postman_collection/             # Colecciones Postman exportadas (.json)
│   └── wsdl/
│       └── facturacion.wsdl            # WSDL auto-generado por Spring-WS
└── logs/                               # Archivos de auditoría (.log rotados)
```

---

## Cronograma

| Semana | Avance | Qué hacer |
|--------|--------|-----------|
| 1-4 | **Avance 1** | Empresa, diagramas As-Is/To-Be, 3 procesos BPMN, 9 servicios candidatos + **BD por servicio** |
| 5-9 | **Avance 2** | Arquitectura en capas, inventario con E/S detalladas, **BUS con direccionamiento + UDDI**, **ciclo de vida + principios**, **síncrono/asíncrono (RabbitMQ)**, OpenAPI + XSD/WSDL, flujo de autenticación |
| 10-14 | **Avance 3** | ClienteService, ProductoService, AutenticacionService (**cada uno con su BD + Flyway**), FacturacionSunatService (Contract-First), **demo async RabbitMQ**, **catálogo UDDI**, probar con Postman |
| 15-17 | **Final** | Los 9 servicios, **10 BDs (Database per Service)**, **RabbitMQ completo**, orquestación, monitoreo, auditoría, documentación |
| 18 | **Entrega** | Presentación final |

---

## Evaluación

| Entrega | Porcentaje | Semana |
|---------|------------|--------|
| APF1 (Avance 1) | 20% | 5 |
| APF2 (Avance 2) | 20% | 10 |
| APF3 (Avance 3) | 20% | 15 |
| PROY (Proyecto Final) | 40% | 18 |

**Nota mínima aprobatoria: 12**

---

## Checklist de Cumplimiento con la Rúbrica

| Requisito | Estado |
|--------------------------|--------|
| Inventario con protocolo REST/SOAP | ✅ 8 REST + 1 SOAP |
| Formato XML/JSON | ✅ JSON en REST, XML en SOAP |
| Ejemplo WSDL | ✅ Auto-generado desde XSD con Spring-WS (Contract-First) |
| Mínimo 4 servicios | ✅ 9 servicios |
| Mínimo 3 procesos de negocio | ✅ Compra, Devolución, Abastecimiento |
| BPMN de procesos | ✅ 3 diagramas |
| **Diagramas As-Is y To-Be** | ✅ Arquitectura actual vs. objetivo (requisito del docente) |
| **Entradas/Salidas detalladas** | ✅ Ejemplos JSON/XML con esquema completo por servicio |
| **BUS/ESB con direccionamiento** | ✅ Tabla de rutas: ruta → servicio → puerto → formato |
| **Registro de servicios (UDDI)** | ✅ Catálogo publish/find/bind expuesto por el BUS |
| **Base de datos por servicio** | ✅ 10 BDs (`Database per Service`) — comentario del docente |
| **Síncrono vs. asíncrono** | ✅ REST/OpenFeign + RabbitMQ (3 eventos) |
| ESB/Integración documentada | ✅ BUS como mediador con transformación JSON↔XML |
| Diagrama de secuencia ESB | ✅ Transformación JSON→XML documentada visualmente |
| **Autenticación** | ✅ AutenticacionService: registro, login, JWT, roles |
| **Monitoreo de servicios** | ✅ MonitoreoService: health checks, tiempos de respuesta |
| Auditoría de servicios | ✅ Tabla `auditoria_logs` (en `db_esb`) + archivos `.log` |
| Orquestación documentada | ✅ Flujo entre servicios en cada proceso (síncrono y asíncrono) |
| Pruebas con Postman | ✅ Capturas de request/response (REST y SOAP) |
| Colección Postman exportada | ✅ Archivo `.json` listo para importar por el profesor |
| Enfoque Contract-First | ✅ XSD como contrato → WSDL auto-generado |
| jaxb2-maven-plugin | ✅ Configurado para generar clases desde XSD |
| Flyway versionado | ✅ Migraciones independientes por servicio (V1, V2, V3...) |
| **Cobertura total del sílabo** | ✅ Matriz Unidades 1-3 verificada (tema → entregable) |
