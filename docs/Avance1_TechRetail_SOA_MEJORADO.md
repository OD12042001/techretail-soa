# PROYECTO FINAL
## ARQUITECTURA ORIENTADA A SERVICIOS (SOA) (100000SI84)
### Avance 1 (Semana 5) — Análisis y Definición de Servicios

**Nombre del proyecto:** Diseño de una Arquitectura SOA para TechRetail S.A.

**Curso:** Arquitectura Orientada al Servicio (100000SI84)
**Ciclo:** 2026 — Ciclo 2 Agosto
**Carrera:** Ingeniería de Sistemas e Informática
**Docente:** Calderón Chuquillanqui, Anthony Vidal
**Equipo:** Grupo 1
**Integrante:** Mori Gomez, Victor Manuel

---

# 1. Resumen Ejecutivo

TechRetail S.A. es una cadena ficticia de tiendas de tecnología y electrónica dedicada a la venta de computadoras, celulares y accesorios, con presencia en tres ciudades del Perú y con dos canales de venta: tienda física y tienda en línea. El negocio está sujeto a una obligación legal con la SUNAT que lo obliga a emitir boletas electrónicas, lo cual introduce una restricción técnica relevante para este proyecto.

En la actualidad, las operaciones de TechRetail están soportadas por sistemas aislados que no se comunican entre sí: una aplicación monolítica desarrollada en PHP para ventas, hojas de cálculo Excel independientes por tienda para el control de inventario, y WhatsApp para la coordinación de pedidos entre tiendas y almacén central. La facturación electrónica depende de un proveedor externo que expone únicamente un servicio web SOAP con mensajería XML. Como consecuencia, la empresa sufre quiebres de stock, datos de clientes duplicados e inconsistentes, ausencia de trazabilidad y sobreventas no controladas.

El presente proyecto propone la modernización de la plataforma de operaciones de TechRetail mediante una Arquitectura Orientada a Servicios (SOA), integrando los dominios de negocio de clientes, catálogo de productos, pedidos, pagos, inventario, notificaciones, facturación electrónica, autenticación y monitoreo bajo un conjunto de nueve servicios independientes, reutilizables e interoperables, coordinados por un Enterprise Service Bus (ESB). El ESB actúa como mediador central: enruta las peticiones provenientes de los distintos canales, valida la seguridad mediante tokens JWT, transforma mensajes entre JSON y XML cuando los servicios REST necesitan comunicarse con el servicio SOAP heredado de SUNAT, y registra la auditoría de cada transacción.

La arquitectura propuesta permite coexistir de forma ordenada los servicios REST/JSON de nueva construcción con el servicio SOAP/XML heredado, elimina la duplicidad de datos, centraliza la autenticación y el monitoreo de disponibilidad, y da visibilidad en tiempo real del inventario. Para su implementación se utilizará Java con Spring Boot para los servicios REST, Spring Web Services con enfoque Contract-First para el servicio SOAP, PostgreSQL con **una base de datos por servicio** (patrón *Database per Service*), RabbitMQ para la comunicación asíncrona, Flyway para las migraciones y Postman para las pruebas de integración.

**Beneficios esperados:** reducción de quiebres de stock, trazabilidad completa de las operaciones, un único punto de seguridad y auditoría, interoperabilidad con el sistema de SUNAT y reutilización de servicios para futuros canales (app móvil, marketplace).

---

# 2. Situación Actual de la Organización

TechRetail S.A. es una empresa ficticia utilizada como caso de estudio académico para este proyecto. A continuación, se describe su situación actual, los problemas identificados y las oportunidades de mejora que una arquitectura SOA permitiría capturar.

## 2.1 Procesos críticos

- **Proceso de Compra:** abarca desde la selección de productos por parte del cliente (tienda física u online) hasta el registro del pedido, verificación de stock, procesamiento del pago, emisión de la boleta electrónica ante SUNAT y notificación al cliente.
- **Proceso de Devolución:** gestiona las solicitudes de devolución o cambio de producto, la verificación de la compra original, el reingreso del producto al inventario y el reembolso al cliente.
- **Proceso de Abastecimiento:** controla la reposición de stock en cada tienda a partir de alertas de inventario bajo, la generación de órdenes de compra a proveedores y la actualización del inventario al recibir la mercadería.

## 2.2 Problemas identificados

1. Los pedidos entre tiendas y el almacén central se coordinan manualmente por WhatsApp, sin trazabilidad ni respaldo formal, lo que genera errores y demoras.
2. El control de inventario se mantiene en hojas de cálculo (Excel) independientes por tienda, sin sincronización en tiempo real, provocando quiebres de stock o sobreventas.
3. El sistema de ventas es una aplicación monolítica antigua desarrollada en PHP, difícil de mantener y sin capacidad de exponer servicios a otros canales (app móvil, e-commerce).
4. La facturación electrónica depende de un proveedor externo que expone únicamente un servicio SOAP/XML, incompatible de forma nativa con el resto de sistemas basados en JSON.
5. No existe un mecanismo centralizado de seguridad, auditoría o monitoreo de las transacciones entre los distintos sistemas de la empresa.
6. La información de clientes está duplicada entre el sistema de tienda física, la web y las hojas de cálculo de atención al cliente, generando inconsistencias.

## 2.3 Oportunidades de mejora con SOA

1. Encapsular cada dominio de negocio (clientes, productos, pedidos, pagos, inventario, notificaciones, facturación, autenticación y monitoreo) en servicios independientes, reutilizables y desplegables por separado.
2. Incorporar un ESB / API Gateway que centralice el enrutamiento, la seguridad (JWT) y la auditoría de todas las peticiones entre canales y servicios.
3. Permitir la coexistencia ordenada de protocolos REST/JSON (servicios propios) y SOAP/XML (servicio heredado de facturación SUNAT), transformando mensajes en el ESB según sea necesario.
4. Dar visibilidad en tiempo real del inventario a todas las tiendas y al canal online, reduciendo quiebres de stock y ventas duplicadas.
5. Facilitar la incorporación de nuevos canales de venta (app móvil, marketplace) reutilizando los mismos servicios ya expuestos.
6. Registrar de forma centralizada (auditoría) cada consumo de servicio, mejorando la trazabilidad y el cumplimiento normativo frente a SUNAT.
7. Contar con un servicio de monitoreo que verifique la disponibilidad y el tiempo de respuesta de todos los servicios de la arquitectura.
8. Centralizar la autenticación mediante un servicio dedicado que administre usuarios, roles y tokens JWT.

## 2.4 Diagrama As-Is (Arquitectura Actual)

El siguiente diagrama muestra la arquitectura actual de TechRetail S.A., donde los sistemas se encuentran aislados sin integración formal.

*(Insertar aquí el diagrama As-Is pulido en draw.io — archivo de referencia: `docs/diagrama-as-is/as-is.drawio`)*

**Elementos del As-Is:**
- **Sistema PHP (monolítico):** ventas, clientes y pedidos en un solo bloque.
- **Hojas Excel por tienda:** inventario y stock sin sincronización.
- **WhatsApp:** coordinación de pedidos entre tienda y almacén, sin trazabilidad.
- **Sin integración formal:** no existe ESB, API centralizada, monitoreo ni auditoría unificada.
- **Proveedor SUNAT (SOAP/XML):** incompatible de forma nativa con los sistemas internos basados en JSON.
- **Duplicidad de clientes:** entre tienda física, web y hojas de Excel.

## 2.5 Diagrama BPMN completo del proceso crítico de negocio

El proceso crítico seleccionado para el modelamiento BPMN completo es el **Proceso de Compra en línea**, por ser el que integra a la mayor cantidad de servicios candidatos (Pedido, Inventario, Pago, Facturación SUNAT y Notificación) y el que mayor impacto tiene en la experiencia del cliente y en el cumplimiento tributario.

*(Insertar aquí el diagrama BPMN del Proceso de Compra completo — archivo de referencia: `docs/diagramas-bpmn/BPMN_PROCESOS.md`)*

El diagrama BPMN debe incluir:
- **Pools/Lanes:** Cliente, ESB / API Gateway, Capa de Servicios y Facturación SUNAT.
- **Eventos:** inicio, fin, y manejo de rechazo en cada compuerta de decisión.
- **Compuertas de decisión:** ¿Token JWT válido? ¿Hay stock? ¿Pago aprobado?
- **Flujos de error:** respuesta HTTP 401, stock insuficiente y pago rechazado.

---

# 3. Procesos de Negocio Seleccionados

Para el desarrollo de la arquitectura SOA se seleccionaron los siguientes tres procesos de negocio de TechRetail S.A. (mínimo requerido: 2). El Proceso de Compra fue modelado en BPMN completo en la sección anterior; a continuación, se presentan los diagramas de flujo de los procesos de Devolución y Abastecimiento.

## 3.1 Proceso de Compra (ver Figura BPMN, sección 2.5)

**Servicios involucrados:** ClienteService, ProductoService, PedidoService, InventarioService, PagoService, FacturacionSunatService y NotificacionService.

**Flujo:**
1. El cliente selecciona productos del catálogo (ProductoService).
2. El cliente solicita el checkout enviando el carrito (PedidoService crea el pedido en estado PENDIENTE).
3. Se verifica y reserva el stock (InventarioService). Si no hay stock, el pedido es rechazado.
4. Se procesa el pago (PagoService). Si el pago es rechazado, se cancela la reserva.
5. Se emite la boleta electrónica ante SUNAT a través del servicio SOAP heredado (FacturacionSunatService). El ESB transforma el mensaje de JSON a XML.
6. Se envía la confirmación al cliente (NotificacionService).

## 3.2 Proceso de Devolución de Producto

Permite a un cliente solicitar la devolución de un producto adquirido. El sistema valida que la solicitud se encuentre dentro del plazo y condiciones establecidas; de ser así, se reingresa el producto al inventario y se procesa el reembolso; en caso contrario, se notifica el rechazo al cliente.

*(Insertar aquí el diagrama de flujo — archivo de referencia: `docs/diagramas-bpmn/BPMN_PROCESOS.md`, Proceso 2)*

**Servicios involucrados:** PedidoService, PagoService, InventarioService y NotificacionService.

**Flujo:**
1. El cliente solicita la devolución (PedidoService valida la compra original).
2. Se verifica el plazo y las condiciones de devolución.
3. Si procede: InventarioService reingresa el producto al stock y PagoService procesa el reembolso.
4. NotificacionService informa el resultado al cliente.
5. Si no procede: NotificacionService informa el rechazo.

## 3.3 Proceso de Abastecimiento y Reposición de Stock

Se activa cuando el InventarioService detecta que el stock de un producto cae por debajo del umbral mínimo en alguna tienda. El sistema genera una orden de reposición, notifica al proveedor correspondiente y, una vez recibida la mercadería, actualiza el inventario.

*(Insertar aquí el diagrama de flujo — archivo de referencia: `docs/diagramas-bpmn/BPMN_PROCESOS.md`, Proceso 3)*

**Servicios involucrados:** InventarioService, PedidoService, PagoService y NotificacionService.

**Flujo:**
1. InventarioService detecta stock bajo y genera una alerta de reposición.
2. PedidoService genera la orden de compra al proveedor.
3. PagoService emite el pago B2B al proveedor.
4. Al recibir la mercadería, InventarioService actualiza el stock.
5. NotificacionService avisa al gerente sobre la operación completada.

---

# 4. Definición Preliminar de Servicios Candidatos

A partir de los procesos de negocio analizados se identificaron **nueve servicios candidatos** para la arquitectura SOA de TechRetail S.A. Ocho de ellos se implementarán como servicios REST/JSON de nueva construcción; el noveno (FacturacionSunatService) corresponde a un servicio heredado SOAP/XML de un proveedor externo, lo que justifica la coexistencia de ambos protocolos dentro de la misma arquitectura. Adicionalmente, se incorporan un servicio de autenticación (seguridad transversal) y un servicio de monitoreo (gestión de operaciones).

| # | Nombre del servicio | Descripción | Tipo | Protocolo | Formato |
|---|---|---|---|---|---|
| 1 | **ClienteService** | Gestión de datos de clientes: registro, actualización y consulta de información de contacto y perfil. | Entidad | REST | JSON |
| 2 | **ProductoService** | Administra el catálogo de productos: precios, categorías, descripciones y disponibilidad general. | Entidad | REST | JSON |
| 3 | **PedidoService** | Gestiona la creación, estado e historial de los pedidos realizados por los clientes. | Proceso | REST | JSON |
| 4 | **PagoService** | Procesa los pagos asociados a un pedido y gestiona su estado (aprobado, rechazado, reembolsado). | Tarea | REST | JSON |
| 5 | **InventarioService** | Controla el stock disponible por tienda, reserva unidades y emite alertas de reposición. | Utilidad | REST | JSON |
| 6 | **NotificacionService** | Envía confirmaciones y notificaciones a clientes (correo/SMS simulado) sobre el estado de sus pedidos. | Utilidad | REST | JSON |
| 7 | **FacturacionSunatService** | Servicio heredado (SOAP/XML) de un proveedor externo que emite boletas electrónicas ante SUNAT. | Tarea | SOAP | XML |
| 8 | **AutenticacionService** | Registro de usuarios, inicio de sesión, emisión y validación de tokens JWT y gestión de roles. | Utilidad | REST | JSON |
| 9 | **MonitoreoService** | Verifica la disponibilidad y el estado de todos los servicios, registrando tiempos de respuesta. | Utilidad | REST | JSON |

## 4.1 Entradas y Salidas detalladas por servicio

### 4.1.1 ClienteService — POST /api/clientes

**Entrada:**
```json
{
  "nombres": "María Fernández",
  "tipoDocumento": "DNI",
  "numeroDocumento": "40214587",
  "email": "maria.fernandez@email.com",
  "telefono": "+51999888777",
  "direccion": "Av. Larco 123, Miraflores",
  "ciudad": "Lima"
}
```

**Salida (201 Created):**
```json
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

### 4.1.2 ProductoService — GET /api/productos

**Entrada (query params):**
```
GET /api/productos?categoria=portatiles&precioMax=3000
```

**Salida (200 OK):**
```json
{
  "productos": [
    {
      "idProducto": 10,
      "nombre": "Laptop Dell Inspiron 15",
      "categoria": "PORTATILES",
      "precio": 2499.99,
      "descripcion": "Intel Core i5, 16GB RAM, 512GB SSD",
      "disponible": true
    }
  ]
}
```

### 4.1.3 PedidoService — POST /api/pedidos

**Entrada:**
```json
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
```

**Salida (201 Created):**
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
  "fechaCreacion": "2026-09-06T10:35:12Z"
}
```

### 4.1.4 PagoService — POST /api/pagos

**Entrada:**
```json
{
  "idPedido": 1001,
  "monto": 6606.80,
  "metodoPago": "TARJETA",
  "numeroTarjeta": "4111111111111111",
  "titular": "MARIA FERNANDEZ"
}
```

**Salida (200 OK):**
```json
{
  "idPago": 501,
  "idPedido": 1001,
  "monto": 6606.80,
  "estado": "APROBADO",
  "operacion": "AUTH-20260906-000123",
  "fecha": "2026-09-06T10:35:45Z"
}
```

### 4.1.5 InventarioService — POST /api/inventario/reservar

**Entrada:**
```json
{
  "idProducto": 10,
  "idTienda": 2,
  "cantidad": 2,
  "idPedido": 1001
}
```

**Salida (200 OK) — reserva exitosa:**
```json
{
  "idReserva": 77,
  "idProducto": 10,
  "idTienda": 2,
  "cantidadReservada": 2,
  "stockAnterior": 15,
  "stockActual": 13,
  "estado": "RESERVADO"
}
```

**Salida (409 Conflict) — stock insuficiente:**
```json
{
  "codigo": "STOCK_INSUFICIENTE",
  "mensaje": "Solo hay 1 unidad disponible en la tienda",
  "stockDisponible": 1
}
```

### 4.1.6 NotificacionService — POST /api/notificaciones

**Entrada:**
```json
{
  "destinatario": "maria.fernandez@email.com",
  "tipoEvento": "PEDIDO_CONFIRMADO",
  "canal": "EMAIL",
  "mensaje": "Tu pedido 1001 ha sido confirmado"
}
```

**Salida (200 OK):**
```json
{
  "idNotificacion": 12,
  "destinatario": "maria.fernandez@email.com",
  "tipoEvento": "PEDIDO_CONFIRMADO",
  "canal": "EMAIL",
  "estado": "ENVIADO",
  "fechaEnvio": "2026-09-06T10:36:00Z"
}
```

### 4.1.7 FacturacionSunatService — SOAP (XML)

**Entrada (SOAP Envelope):**
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

**Salida (SOAP Envelope):**
```xml
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

*Nota: el contrato se define con enfoque Contract-First (archivo XSD que genera el WSDL automáticamente con Spring Web Services).*

### 4.1.8 AutenticacionService — POST /api/auth/login

**Entrada:**
```json
{
  "usuario": "maria.fernandez@email.com",
  "password": "Clave123!"
}
```

**Salida (200 OK):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tipo": "Bearer",
  "expiraEn": 3600,
  "rol": "CLIENTE",
  "idUsuario": 8,
  "nombre": "María Fernández"
}
```

### 4.1.9 MonitoreoService — GET /api/monitoreo/estado

**Entrada:** sin parámetros (consulta del estado de los servicios).

**Salida (200 OK):**
```json
{
  "fechaConsulta": "2026-09-06T11:00:00Z",
  "servicios": [
    { "nombre": "ClienteService", "estado": "UP", "tiempoRespuestaMs": 45 },
    { "nombre": "ProductoService", "estado": "UP", "tiempoRespuestaMs": 38 },
    { "nombre": "PedidoService", "estado": "UP", "tiempoRespuestaMs": 52 },
    { "nombre": "PagoService", "estado": "UP", "tiempoRespuestaMs": 120 },
    { "nombre": "InventarioService", "estado": "UP", "tiempoRespuestaMs": 41 },
    { "nombre": "NotificacionService", "estado": "UP", "tiempoRespuestaMs": 33 },
    { "nombre": "FacturacionSunatService", "estado": "UP", "tiempoRespuestaMs": 180 },
    { "nombre": "AutenticacionService", "estado": "UP", "tiempoRespuestaMs": 29 }
  ]
}
```

---

# 5. Mapa de Arquitectura Inicial

## 5.1 Diagrama To-Be (Arquitectura Objetivo)

El siguiente mapa presenta la arquitectura SOA objetivo de TechRetail S.A., mostrando las cajas principales: los clientes/canales de acceso, el ESB / API Gateway como mediador central, la capa de los nueve servicios candidatos y los repositorios de datos.

*(Insertar aquí el diagrama To-Be pulido en draw.io — archivo de referencia: `docs/diagrama-arquitectura/to-be.drawio`)*

## 5.2 Descripción de las cajas

- **Clientes / Canales:** usuarios finales que acceden vía web, aplicación móvil o tienda física, y personal interno (empleados) a través de un panel administrativo.
- **ESB / API Gateway:** punto único de entrada que enruta las peticiones al servicio correspondiente, transforma mensajes JSON↔XML cuando se requiere comunicación con el servicio SOAP, valida la seguridad (JWT) y registra la auditoría de cada transacción.
- **Capa de Servicios:** los nueve servicios candidatos descritos en la sección 4, ocho de ellos REST/JSON y uno SOAP/XML.
- **Repositorio de datos:** **base de datos por servicio** (patrón *Database per Service*): cada uno de los nueve servicios posee su propia base de datos PostgreSQL (`db_cliente`, `db_producto`, `db_pedido`, `db_pago`, `db_inventario`, `db_notificacion`, `db_facturacion`, `db_autenticacion`, `db_monitoreo`) y el ESB mantiene `db_esb` con la auditoría (`auditoria_logs`) y el catálogo de servicios. Los servicios nunca acceden directamente a las tablas de otro: la integración se realiza exclusivamente a través de los servicios expuestos o de colas de mensajería (RabbitMQ), preservando el encapsulamiento y la independencia de despliegue. La emisión de boletas se conecta con el sistema externo de SUNAT.

**Comparativa As-Is vs. To-Be:**

| Aspecto | As-Is (actual) | To-Be (objetivo) |
|---|---|---|
| Arquitectura | Monolito PHP + Excel + WhatsApp | SOA con 9 servicios independientes |
| Integración | Sin ESB, sistemas aislados | ESB mediador con transformación JSON↔XML |
| Inventario | Excel por tienda, sin sincronización | Tiempo real en `db_inventario` (BD propia del servicio) |
| Datos | Duplicados entre sistemas | **Base de datos por servicio** (Database per Service) |
| Seguridad | Sin autenticación centralizada | AutenticacionService con JWT y roles |
| Monitoreo | No existe | MonitoreoService con health checks |
| Auditoría | Sin trazabilidad | Tabla `auditoria_logs` por transacción |
| Facturación | Conexión directa y forzada al SOAP | Transformación de mensajes en el ESB |

Este mapa inicial será refinado en el Avance 2, donde se detallará la arquitectura en capas (presentación, negocio, integración y datos), el diseño formal del ESB y las especificaciones técnicas (OpenAPI/WSDL) de los servicios.