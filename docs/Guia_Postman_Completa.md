# Guía completa de Postman — TechRetail SOA

Manual de referencia de la colección `docs/postman_collection/techretail-completa.json`.

Este documento explica **las 40 peticiones** de la colección: qué hace cada una, qué regla de negocio o de seguridad demuestra, si necesita token, qué variables lee o escribe y qué código HTTP debe devolver. No es un volcado de URLs: cada caso incluye el **por qué** técnico, porque en la sustentación lo que se evalúa es si entiendes la decisión, no si memorizaste la ruta.

> **Documento complementario.** `docs/Guia_Proyecto.md` enseña a levantar el proyecto y a usar Postman por primera vez. Esta guía asume que ya sabes ejecutar una petición y se concentra en el *significado* de cada caso.

---

## Cómo leer este documento

### Organización

El documento replica **exactamente** las 5 carpetas de la colección, en el mismo orden:

1. `01 Autenticacion` — 7 peticiones
2. `02 Catalogo de productos` — 16 peticiones
3. `03 Monitoreo` — 6 peticiones
4. `04 Auditoria` — 5 peticiones
5. `05 Seguridad` — 6 peticiones

**Total: 40 peticiones.**

Los identificadores van de P1 a P38, pero **no son consecutivos**: falta P32 y tres números tienen dos variantes (P8a/P8b, P13/P13b, P18a/P18b). El ID identifica el caso dentro de la auditoría HTTP del proyecto, no la posición en la lista. Si te preguntas "¿me falta alguna?", la respuesta es no: son 37 números base más 3 variantes adicionales = 40.

### Anatomía de una ficha

Cada petición se presenta igual:

- **Método y ruta** — el verbo HTTP y la URL exacta tal como está en el JSON.
- **Acceso** — `Público`, `ADMIN`, `EMPLEADO` o `CLIENTE`, y el token que envía (o no envía).
- **Variables** — las de colección que lee o escribe.
- **Body** — el JSON necesario, copiado de la colección.
- **Código esperado** — el estado HTTP y, cuando importa, el mensaje exacto.
- **Qué demuestra** — la regla que prueba el caso.
- **Ojo** — dependencias de orden, efectos secundarios o matices que suelen preguntarse.

### Los tres códigos que hay que saber distinguir

| Código | Significado | Pregunta que responde |
|---|---|---|
| **401** | No autenticado | ¿Quién eres? No lo sé, o tu credencial no es válida. |
| **403** | Autenticado, pero sin permiso | Sé quién eres, pero tu rol no alcanza para esto. |
| **400** | Petición mal formada | Tu identidad está bien; el problema es el dato que enviaste. |
| **404** | Recurso inexistente | La ruta o el id no existen. |
| **409** | Conflicto con el estado actual | El dato ya existe (usuario duplicado). |

La diferencia entre **401 y 403** es la que más se pregunta en la sustentación. La colección la demuestra tres veces: P7 contra P8b, P29 contra P30, y P33 contra P34.

### Las tres capas que pueden responder

Una petición de esta colección puede ser rechazada en tres puntos distintos. Saber **quién** respondió es parte de la prueba:

1. **El BUS (`techretail-gateway`, :8090).** Valida el JWT, aplica la matriz de roles y, si la ruta está en su tabla de direccionamiento, reenvía al servicio.
2. **El servicio destino (`:8081`–`:8089`).** Vuelve a validar el JWT y sus propios permisos. Es la segunda capa de seguridad (*defense in depth*).
3. **El *error dispatch* interno (`/error`).** Cuando un controlador lanza un 400/404, Spring hace un despacho interno a `/error`. Si esa ruta no estuviera liberada, el filtro JWT volvería a evaluar la petición y convertiría un 400 legítimo en 401. Por eso el gateway y los servicios declaran `/error` como `permitAll`.

Esta distinción explica por qué P7 y P18b devuelven el **mismo** 401 con el **mismo** mensaje, pero los emite capas diferentes.

### Variables de colección

| Variable | Valor inicial | Quién la escribe | Quién la usa |
|---|---|---|---|
| `baseUrl` | `http://localhost:8090` | Ya viene definida | Todas las peticiones que pasan por el BUS |
| `adminToken` | *(vacío)* | **P1** | P7, P9–P12, P13, P13b, P14, P15, P16, P17, P24–P26 |
| `empleadoToken` | *(vacío)* | **P19** | P28, P31, P35 |
| `clienteToken` | *(vacío)* | **P8a** | P8b, P30, P34 |
| `productoId` | `4` | **P9** | P10, P11, P23 |
| `categoriaId` | `1` | **P24** | P9 |

> **Cómo se envía el token.** La colección usa la pestaña **Headers**, con el valor completo `Bearer {{adminToken}}`. No uses la pestaña *Authorization* con tipo *Bearer Token*: ahí Postman agrega el prefijo por su cuenta y terminarías enviando `Bearer Bearer eyJ...`, que produce un 401.

### Orden de ejecución y dependencias

El orden dentro de cada carpeta **no es numérico** a propósito. La colección está pensada para ejecutarse de arriba hacia abajo, y los casos que producen tokens están al principio de su carpeta:

```text
P1  → guarda adminToken
P3  → crea el usuario fijo qa.avance3@techretail.pe
P8a → guarda clienteToken
P19 → guarda empleadoToken
P9  → guarda productoId
P24 → guarda categoriaId
```

Consecuencias prácticas:

- Si ejecutas P7, P8b, P9, P15, P16, P24, P25, P26, P28, P30, P31, P34 o P35 **antes** del login correspondiente, la cabecera sale como `Bearer ` (vacío) y recibirás 401.
- P20 depende de que P3 haya creado el usuario. Si ejecutas P20 en una base limpia sin haber ejecutado P3, devolverá 201 y el test fallará.
- P23, P10 y P11 dependen de P9. Si P9 falla, `productoId` queda con el valor anterior (`4`) y los tres casos apuntan a otro producto.
- P9 usa `{{categoriaId}}`, que vale `1` (Laptops) hasta que P24 lo sobrescribe con la categoría QA.

**Sobre re-ejecutar la colección completa:** P3 usa un usuario fijo. La primera ejecución devuelve 201; la segunda devuelve 409. Eso es correcto y es precisamente lo que prueba P20, pero hace que el test de P3 falle en la segunda pasada. Para repetir en limpio, borra el usuario `qa.avance3@techretail.pe` de `db_autenticacion` o acepta que P3 y P20 intercambian su resultado esperado. P24 tampoco es idempotente: crea una categoría nueva en cada ejecución (la tabla no tiene restricción única por nombre), así que P22 verá más de 5 categorías con el tiempo.

### Antes de ejecutar

1. Desde la raíz del repo: `docker compose up -d postgres rabbitmq jaeger`.
2. Levanta, como mínimo, `techretail-autenticacion` (:8088), `techretail-producto` (:8082), `techretail-monitoreo` (:8089) y `techretail-gateway` (:8090). Los casos de monitoreo esperan además el resto de servicios para que las sondas reporten `UP`.
3. Espera **30 segundos** después de arrancar monitoreo: el planificador hace la primera ronda de sondas en ese intervalo.
4. Ten claro que la colección **no siembra productos**. `db_producto` solo trae las 5 categorías de `V2__seed_categorias.sql`. Por eso P4 puede devolver `[]` en una base recién creada, y P5 devuelve `[]` salvo que exista un producto cuyo nombre contenga "monitor".

---

## 01 Autenticacion

Esta carpeta es el proveedor de identidad de toda la prueba. Aquí no se demuestra autorización, sino **autenticación**: quién es el usuario y qué token recibe. Los tres logins (P1, P8a, P19) existen para que las otras cuatro carpetas tengan credenciales con las que probar los tres roles del sistema.

### P1 — Login ADMIN

- **Método y ruta:** `POST {{baseUrl}}/api/auth/login`
- **Acceso:** Público (sin token)
- **Variables:** escribe `adminToken`
- **Código esperado:** `200 OK`

**Body**

```json
{
  "usuario": "admin@techretail.pe",
  "password": "Admin123!"
}
```

**Qué demuestra.** Que el login es el único camino de negocio sin JWT y que devuelve un token firmado con el rol dentro. La respuesta trae `token`, `tipo: "Bearer"`, `expiraEn: 3600` (segundos, no fecha), `rol: "ADMIN"`, `idUsuario` y `nombre`. El script de la colección valida las tres cosas que importan: `200`, `rol === "ADMIN"` y token no vacío; después guarda el token en `adminToken`.

**Ojo.** El token no está cifrado, está **firmado** (HS256). Cualquiera puede leer su contenido; lo que no puede es modificarlo sin invalidar la firma. Eso es lo que prueba P17. El login usa BCrypt con coste 12, así que la verificación es deliberadamente lenta: es la defensa contra fuerza bruta.

---

### P2 — Login con credenciales inválidas

- **Método y ruta:** `POST {{baseUrl}}/api/auth/login`
- **Acceso:** Público (sin token)
- **Variables:** ninguna
- **Código esperado:** `401 Unauthorized`

**Body**

```json
{
  "usuario": "admin@techretail.pe",
  "password": "incorrecta"
}
```

**Qué demuestra.** Que el sistema **no filtra si el usuario existe**. La respuesta es siempre `401` con el mensaje exacto `Credenciales invalidas`, tanto si el usuario no existe, como si la contraseña es incorrecta, como si la cuenta está inactiva. Si el mensaje distinguiera "usuario no encontrado" de "contraseña incorrecta", un atacante podría enumerar cuentas válidas.

El detalle técnico que sostiene esto está en `AuthService`: cuando el usuario no existe, igual se ejecuta un `passwordEncoder.matches(...)` contra un hash señuelo. Así el tiempo de respuesta no delata la existencia de la cuenta.

**Ojo.** La respuesta de error agrega `Cache-Control: no-store`, `X-Content-Type-Options: nosniff` y `X-Frame-Options: DENY`. Un error de credenciales nunca debe quedar en caché.

---

### P3 — Registro de cliente

- **Método y ruta:** `POST {{baseUrl}}/api/auth/registro`
- **Acceso:** Público (sin token)
- **Variables:** ninguna (crea el usuario fijo que usa P20)
- **Código esperado:** `201 Created`

**Body**

```json
{
  "usuario": "qa.avance3@techretail.pe",
  "password": "Cliente123!",
  "nombre": "QA Avance 3"
}
```

**Qué demuestra.** Que el registro público **no permite escalar privilegios**. El DTO `RegistroRequest` no tiene campo `rol`: si envías `"rol": "ADMIN"` en el JSON, se ignora. El servicio asigna siempre el rol `CLIENTE` desde la tabla `roles`, y la respuesta lo confirma con `rol: "CLIENTE"` (el script lo verifica).

La respuesta **no incluye token**: registrarse no autentica. Para obtener credenciales hay que pasar por el login, que es una decisión de diseño deliberada.

**Ojo.** Usuario fijo: la segunda ejecución devuelve 409 y el test de P3 falla. Es el insumo de P20.

---

### P20 — Registro duplicado

- **Método y ruta:** `POST {{baseUrl}}/api/auth/registro`
- **Acceso:** Público (sin token)
- **Variables:** ninguna
- **Código esperado:** `409 Conflict`

**Body**

```json
{
  "usuario": "qa.avance3@techretail.pe",
  "password": "Cliente123!",
  "nombre": "QA Avance 3"
}
```

**Qué demuestra.** Que la unicidad del usuario se defiende en dos niveles: una comprobación explícita (`existsByUsuario`) y la restricción `UNIQUE` de la tabla. El mensaje exacto es `El usuario ya esta registrado`.

El segundo nivel importa: si dos registros simultáneos pasan la comprobación al mismo tiempo, la base de datos rechaza uno y `DataIntegrityViolationException` también se traduce a `409`, no a un 500.

**Ojo.** Depende de P3. En una base limpia, ejecutar P20 sin P3 devuelve `201`, no `409`. El 409 aquí no es un error de autenticación ni de permisos: es un conflicto con el estado actual del recurso.

---

### P21 — Registro con contraseña corta

- **Método y ruta:** `POST {{baseUrl}}/api/auth/registro`
- **Acceso:** Público (sin token)
- **Variables:** ninguna
- **Código esperado:** `400 Bad Request`

**Body**

```json
{
  "usuario": "qa.corta@techretail.pe",
  "password": "corta",
  "nombre": "QA Password Corta"
}
```

**Qué demuestra.** Que la validación de entrada es del servicio, no solo del BUS. `RegistroRequest` declara `@NotBlank` y `@Size(min = 8, max = 72)` sobre `password`. La respuesta es `400` con un mensaje que empieza con `Solicitud invalida` y nombra el campo `password`; el script comprueba ambas subcadenas.

El máximo de 72 no es arbitrario: BCrypt trunca silenciosamente a 72 bytes. En lugar de aceptar una contraseña "recortada" sin avisar, el servicio la rechaza. Además de la anotación, `AuthService` valida el tamaño en bytes UTF-8, porque `@Size` cuenta caracteres y un carácter no ASCII puede ocupar más de un byte.

**Ojo.** Este caso es el contraste de P20: `400` es "tu dato está mal"; `409` es "tu dato está bien pero choca con lo que ya existe".

---

### P8a — Login CLIENTE

- **Método y ruta:** `POST {{baseUrl}}/api/auth/login`
- **Acceso:** Público (sin token)
- **Variables:** escribe `clienteToken`
- **Código esperado:** `200 OK`

**Body**

```json
{
  "usuario": "cliente@techretail.pe",
  "password": "Cliente123!"
}
```

**Qué demuestra.** Que los roles son datos, no código: el mismo endpoint emite tokens distintos según el usuario. Este token representa al actor con **menos privilegios** del sistema y se usa en tres casos negativos: P8b (no puede escribir el catálogo), P30 (no puede ver monitoreo) y P34 (no puede leer auditoría). Los tres esperan `403`.

**Ojo.** El usuario `cliente@techretail.pe` lo crea el `DemoUsuarioSeeder` al arrancar autenticación, junto con `admin@techretail.pe` y `empleado@techretail.pe`. Sin esos usuarios de demostración no habría forma de probar ADMIN y EMPLEADO, porque el registro público solo crea CLIENTE.

---

### P19 — Login EMPLEADO

- **Método y ruta:** `POST {{baseUrl}}/api/auth/login`
- **Acceso:** Público (sin token)
- **Variables:** escribe `empleadoToken`
- **Código esperado:** `200 OK`

**Body**

```json
{
  "usuario": "empleado@techretail.pe",
  "password": "Empleado123!"
}
```

**Qué demuestra.** Que existe un rol intermedio y que la autorización es **por ruta**, no por "estar logueado". EMPLEADO puede consultar monitoreo (P31) y auditoría (P35), pero no escribir el catálogo (P28 devuelve `403`). Es el ejemplo más claro de *least privilege* de la colección: el rol tiene exactamente los permisos de operación que necesita, ni uno más.

**Ojo.** Este token es el que permite distinguir "ADMIN y EMPLEADO" de "solo ADMIN" en la matriz de acceso del Anexo A.

---

## 02 Catalogo de productos

La carpeta más grande: 16 peticiones que cubren el ciclo de vida completo del recurso (leer, crear, actualizar, retirar) y, alrededor de él, los casos de borde que suelen romper un CRUD mal hecho: sin token, con rol insuficiente, con id inexistente, con id no numérico, con precio inválido y con categoría inexistente.

La política del catálogo es simple y se repite en las dos capas: **lectura pública, escritura solo ADMIN**. En el gateway y en el servicio están las mismas reglas, a propósito.

### P4 — Catálogo público

- **Método y ruta:** `GET {{baseUrl}}/api/productos`
- **Acceso:** Público (sin token)
- **Variables:** ninguna
- **Código esperado:** `200 OK`

**Qué demuestra.** Que la lectura del catálogo es un caso de negocio público: un visitante puede ver productos sin registrarse. Se cumple en el BUS (`GET /api/productos/**` es `permitAll`) y otra vez en el servicio.

La respuesta es un array de `ProductoResponse` con `id`, `nombre`, `descripcion`, `precio`, `idCategoria`, `categoria`, `stockReferencial`, `disponible` y `fechaCreacion`.

**Ojo.** No hay productos precargados. En una base recién creada la respuesta es `[]`, y eso es un `200` correcto: una lista vacía es una respuesta válida, no un recurso inexistente. Después de P9 habrá al menos uno.

---

### P5 — Catálogo filtrado

- **Método y ruta:** `GET {{baseUrl}}/api/productos?q=monitor&soloDisponibles=true`
- **Acceso:** Público (sin token)
- **Variables:** ninguna
- **Código esperado:** `200 OK`

**Qué demuestra.** Que el filtrado se resuelve en el servidor y se combina con **AND**, no con OR. `q=monitor` busca coincidencia parcial, sin distinguir mayúsculas, sobre el nombre; `soloDisponibles=true` agrega la condición `disponible = true`. El resultado son los productos que cumplen **ambas** cosas.

El texto de `q` se escapa antes de armar el `LIKE`: los caracteres `%`, `_` y `\` se tratan como literales. Sin ese escape, un usuario podría enviar `q=%` y convertir el filtro en "todo". Es una defensa silenciosa pero real.

**Ojo.** El producto que crea P9 es un teclado, así que **no** coincide con "monitor". En una base recién creada, P5 devuelve `[]` y el test pasa igual, porque solo valida que la respuesta sea un array. Si quieres ver un resultado no vacío, crea un producto cuyo nombre contenga "monitor".

---

### P22 — Categorías públicas

- **Método y ruta:** `GET {{baseUrl}}/api/productos/categorias`
- **Acceso:** Público (sin token)
- **Variables:** ninguna
- **Código esperado:** `200 OK`

**Qué demuestra.** Que los datos de referencia también son de lectura pública y que el catálogo no obliga al cliente a adivinar los ids de categoría. La respuesta es un array de `{id, nombre, descripcion}` ordenado alfabéticamente por nombre, con las 5 categorías del seed `V2__seed_categorias.sql`.

El script valida tres cosas: que sea un array, que tenga **al menos 5** elementos y que el primero traiga `id` y `nombre`. El "al menos" es deliberado: P24 agrega una categoría por ejecución, así que el número crece.

**Ojo.** Esta ruta convive con `GET /api/productos/{id}`. Si Spring tratara `categorias` como un `{id}`, fallaría al convertirlo a `Long`; no lo hace porque el patrón literal es más específico que el variable. Es un detalle de enrutamiento que vale la pena tener presente.

> En el archivo del seed, el nombre de la tercera categoría aparece como `PerifAcricos` por un problema de codificación del propio `.sql`. Es un dato de ejemplo, no un comportamiento de la API.

---

### P6 — Detalle inexistente

- **Método y ruta:** `GET {{baseUrl}}/api/productos/999999`
- **Acceso:** Público (sin token)
- **Variables:** ninguna
- **Código esperado:** `404 Not Found`

**Mensaje esperado:** `No existe el producto 999999`

**Qué demuestra.** Que un id **bien formado pero ausente** produce `404`, y que el mensaje incluye el id para que el cliente sepa qué buscó. `ProductoService.obtener` lanza `ProductoNoEncontradoException`, que el `@RestControllerAdvice` traduce a un sobre de error uniforme `{status, error, message, timestamp}`.

**Ojo.** Este caso y P27 son la pareja que hay que saber explicar: `999999` es un número válido que no existe → `404`; `abc` no es un número → `400`. El servidor primero tiene que poder interpretar el identificador; recién después puede decir si existe.

---

### P7 — Escritura sin token

- **Método y ruta:** `POST {{baseUrl}}/api/productos`
- **Acceso:** ADMIN requerido · se envía **sin token** (caso negativo)
- **Variables:** ninguna
- **Código esperado:** `401 Unauthorized`

**Mensaje esperado:** `No autenticado: se requiere un token JWT valido`

**Body**

```json
{
  "nombre": "Producto no autorizado",
  "descripcion": "sin token",
  "precio": 10,
  "idCategoria": 1,
  "stockReferencial": 1
}
```

**Qué demuestra.** Que el BUS **rechaza antes de enrutar**. La petición no llega a `techretail-producto`: el gateway ve que la ruta de escritura exige `ADMIN`, no encuentra autenticación y responde `401`. El servicio destino nunca se enteró del intento.

Es la diferencia entre autenticación y autorización: sin token no hay identidad, así que el sistema ni siquiera puede evaluar el rol. Por eso es `401` y no `403`.

**Ojo.** El `401` lo emite `RestAuthenticationEntryPoint` del gateway. P18b devuelve el mismo mensaje, pero lo emite el servicio de producto: mismo síntoma, capa distinta.

---

### P8b — Escritura con rol CLIENTE

- **Método y ruta:** `POST {{baseUrl}}/api/productos`
- **Acceso:** CLIENTE · `Bearer {{clienteToken}}` (caso negativo)
- **Variables:** lee `clienteToken`
- **Código esperado:** `403 Forbidden`

**Mensaje esperado:** `Acceso denegado: el rol del token no autoriza esta operacion`

**Body**

```json
{
  "nombre": "Producto de cliente",
  "descripcion": "rol CLIENTE",
  "precio": 10,
  "idCategoria": 1,
  "stockReferencial": 1
}
```

**Qué demuestra.** La otra mitad de la distinción: el token es **válido** (la autenticación funciona), pero el rol no alcanza (la autorización falla). Eso es exactamente un `403`.

El flujo es: el filtro JWT valida la firma y publica la identidad en el `SecurityContext`; después, la regla `hasRole(ADMIN)` encuentra un `ROLE_CLIENTE` y lanza `AccessDeniedException`; el `AccessDeniedHandler` responde en JSON.

**Ojo.** Si te preguntan "¿por qué no 401?", la respuesta es: porque el sistema **sí sabe** quién eres. El 401 se reserva para cuando no hay identidad válida.

---

### P9 — Crear producto

- **Método y ruta:** `POST {{baseUrl}}/api/productos`
- **Acceso:** ADMIN · `Bearer {{adminToken}}`
- **Variables:** lee `categoriaId`; escribe `productoId`
- **Código esperado:** `201 Created` + cabecera `Location`

**Body**

```json
{
  "nombre": "Teclado Mecanico Keychron K8 Pro",
  "descripcion": "TKL, switches Gateron Brown, inalambrico",
  "precio": 549.90,
  "idCategoria": {{categoriaId}},
  "stockReferencial": 25
}
```

**Qué demuestra.** El camino feliz de la escritura y el contrato REST de creación: `201`, cuerpo con el recurso creado y cabecera `Location` apuntando a su URI. El script verifica el `201`, exige que exista `Location` y guarda `id` en `productoId`.

El `Location` se construye con `ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")`, es decir, se deriva de la URL real de la petición, no de una constante. Si el servicio se publica detrás de otro prefijo, el header sigue siendo correcto.

**Ojo.** `disponible` se omite en el body: el servicio lo interpreta como `true`. La categoría se resuelve **antes** de guardar; si no existe, el resultado es 404 (ver P26), no un error de clave foránea. Y como lee `{{categoriaId}}`, después de ejecutar P24 una vez, los productos nuevos se crean en la categoría QA.

---

### P23 — Detalle del producto creado

- **Método y ruta:** `GET {{baseUrl}}/api/productos/{{productoId}}`
- **Acceso:** Público (sin token)
- **Variables:** lee `productoId`
- **Código esperado:** `200 OK`

**Qué demuestra.** Que el id devuelto por P9 es usable y que escritura y lectura son consistentes. El script compara el `id` de la respuesta con la variable y exige que el nombre no esté vacío.

**Ojo.** Depende de P9. Si P9 falló, `productoId` conserva su valor inicial (`4`), que puede no existir en tu base; en ese caso verás un 404 y el problema no está en P23.

---

### P10 — Actualizar producto

- **Método y ruta:** `PUT {{baseUrl}}/api/productos/{{productoId}}`
- **Acceso:** ADMIN · `Bearer {{adminToken}}`
- **Variables:** lee `productoId`
- **Código esperado:** `200 OK`

**Body**

```json
{
  "nombre": "Teclado Mecanico Keychron K8 Pro",
  "descripcion": "TKL, switches Gateron Brown, inalambrico",
  "precio": 499.9,
  "idCategoria": 3,
  "stockReferencial": 20
}
```

**Qué demuestra.** Que `PUT` **reemplaza la representación completa**, no modifica un campo suelto. El body trae todos los datos del producto, no solo el precio. Si enviaras únicamente `{"precio": 499.9}`, la validación fallaría porque `nombre`, `idCategoria` y `stockReferencial` son obligatorios: eso sería un `PATCH`, y este recurso no lo ofrece.

El script valida `200` y que el precio devuelto sea exactamente `499.90`. El producto cambia además de categoría (de la 1 a la 3) y baja el stock a 20.

**Ojo.** `disponible` se omite otra vez; al ser `null`, el servicio aplica `true`. Si el producto venía retirado de una ejecución anterior, este PUT lo vuelve a poner disponible. Es un efecto secundario real de usar un PUT de representación completa.

---

### P11 — Retiro lógico

- **Método y ruta:** `PUT {{baseUrl}}/api/productos/{{productoId}}`
- **Acceso:** ADMIN · `Bearer {{adminToken}}`
- **Variables:** lee `productoId`
- **Código esperado:** `200 OK`

**Body**

```json
{
  "nombre": "Teclado Mecanico Keychron K8 Pro",
  "descripcion": "TKL, switches Gateron Brown, inalambrico",
  "precio": 499.9,
  "idCategoria": 3,
  "stockReferencial": 20,
  "disponible": false
}
```

**Qué demuestra.** Que la baja de un producto es **lógica**, no física. Y esta es una de las decisiones que más vale la pena entender:

- **No existe `DELETE`.** En `ProductoController` solo hay `GET`, `POST` y `PUT`. No es un olvido: es una decisión de negocio.
- **Se conserva la fila.** Un producto puede estar referenciado por pedidos o facturas de otros servicios. Borrarlo dejaría referencias huérfanas y, en una arquitectura distribuida, no hay una transacción global que garantice el borrado en todos lados.
- **Se conserva la historia.** El catálogo necesita poder explicar qué se vendió. Un borrado físico destruye esa evidencia.
- **El estado es explícito.** `disponible=false` significa "retirado de la venta", no "inexistente": P23 sigue devolviendo el producto, y P5 con `soloDisponibles=true` deja de mostrarlo.
- **`PUT` es idempotente.** Repetir la petición deja el mismo estado; un `DELETE` repetido tendría que distinguir entre "borrado" y "ya no estaba".

El script verifica `200` y `disponible === false`.

**Ojo.** Después de P11, el producto sigue existiendo pero ya no aparece en el filtro de disponibilidad. Si vuelves a ejecutar P10, lo revives (`disponible` omitido = `true`); si vuelves a ejecutar P9, creas otro producto distinto.

---

### P12 — Validación de entrada

- **Método y ruta:** `POST {{baseUrl}}/api/productos`
- **Acceso:** ADMIN · `Bearer {{adminToken}}`
- **Variables:** ninguna
- **Código esperado:** `400 Bad Request`

**Body**

```json
{
  "nombre": "Precio invalido",
  "descripcion": "precio negativo",
  "precio": -5,
  "idCategoria": 1,
  "stockReferencial": 1
}
```

**Qué demuestra.** Que la validación de datos vive en el servicio y no solo en el gateway. `ProductoRequest` declara `@NotNull`, `@DecimalMin("0.01")`, `@Digits(integer = 8, fraction = 2)` y `@Min(0)` sobre el stock. Un precio negativo no llega a la base de datos.

El `@RestControllerAdvice` convierte `MethodArgumentNotValidException` en un `400` cuyo mensaje lista los campos inválidos. El script solo exige el `400`: no depende del texto exacto ni del idioma del mensaje.

**Ojo.** Este caso y P26 cubren dos tipos de invalidez distintos: el **formato** del dato (P12) y la **existencia de una referencia** (P26). El primero es `400`; el segundo, `404`.

---

### P24 — Crear categoría

- **Método y ruta:** `POST {{baseUrl}}/api/productos/categorias`
- **Acceso:** ADMIN · `Bearer {{adminToken}}`
- **Variables:** escribe `categoriaId`
- **Código esperado:** `201 Created`

**Body**

```json
{
  "nombre": "Categoria QA Postman",
  "descripcion": "Creada por la coleccion completa"
}
```

**Qué demuestra.** Que la escritura de datos de referencia también exige ADMIN: la regla `hasRole(ADMIN)` cubre `/api/productos/**` completo, incluidas las subrutas. El script valida `201` y que el `id` sea numérico; después lo guarda en `categoriaId` para que las siguientes ejecuciones de P9 creen productos en esta categoría.

**Ojo.** La tabla `categorias` no tiene restricción única sobre `nombre`. Cada ejecución crea una categoría nueva, sin `409`. Por eso P22 dice "al menos 5" y no "exactamente 5". Si quieres una colección repetible al 100 %, tendrías que borrar la categoría QA entre corridas.

---

### P25 — Actualizar producto inexistente

- **Método y ruta:** `PUT {{baseUrl}}/api/productos/999999`
- **Acceso:** ADMIN · `Bearer {{adminToken}}`
- **Variables:** ninguna
- **Código esperado:** `404 Not Found`

**Mensaje esperado:** `No existe el producto 999999`

**Body**

```json
{
  "nombre": "Fantasma",
  "descripcion": "no existe",
  "precio": 10,
  "idCategoria": 1,
  "stockReferencial": 1
}
```

**Qué demuestra.** Que `PUT` **no es un upsert**. El servicio busca el producto antes de modificar; si no existe, lanza `ProductoNoEncontradoException` y responde `404`. No se crea un producto con id 999999.

También demuestra que la autorización se evalúa primero: el token ADMIN es válido, así que el rechazo no es de seguridad sino de negocio.

**Ojo.** Mismo mensaje que P6, con verbo distinto. Es intencional: el recurso no existe, independientemente de lo que intentaras hacer con él.

---

### P26 — Crear producto con categoría inexistente

- **Método y ruta:** `POST {{baseUrl}}/api/productos`
- **Acceso:** ADMIN · `Bearer {{adminToken}}`
- **Variables:** ninguna
- **Código esperado:** `404 Not Found`

**Mensaje esperado:** `No existe la categoria 999999`

**Body**

```json
{
  "nombre": "Producto sin categoria",
  "descripcion": "idCategoria invalido",
  "precio": 10,
  "idCategoria": 999999,
  "stockReferencial": 1
}
```

**Qué demuestra.** Que la integridad referencial se valida en el **servicio**, con un mensaje útil, antes de tocar la base. `ProductoService.crear` resuelve la categoría con `categorias.findById(...)`; si no está, lanza `CategoriaNoEncontradaException` y el handler responde `404`.

Si se dejara librado a la base de datos, el resultado sería un error de clave foránea traducido a `500`, que no le dice nada al cliente y ensucia los logs. La regla es: **valida antes de persistir**.

**Ojo.** Es un `404` de negocio, no de ruta: `/api/productos` existe; lo que no existe es la categoría referenciada.

---

### P27 — Detalle con id no numérico

- **Método y ruta:** `GET {{baseUrl}}/api/productos/abc`
- **Acceso:** Público (sin token)
- **Variables:** ninguna
- **Código esperado:** `400 Bad Request`

**Mensaje esperado:** `Peticion mal formada`

**Qué demuestra.** Que el identificador se valida antes de llegar al controlador. `{id}` está declarado como `Long`; Spring intenta convertirlo y falla con `MethodArgumentTypeMismatchException`, que el handler traduce a `400` con el mensaje `Peticion mal formada`.

La comparación con P6 es el corazón del caso:

| Petición | Valor | Interpretación | Resultado |
|---|---|---|---|
| P6 | `999999` | Número válido, recurso ausente | `404` |
| P27 | `abc` | No es un número | `400` |

**Ojo.** Este es el caso que justifica liberar `/error`. El `400` nace en el despacho de error interno; si el filtro JWT volviera a evaluarlo, la respuesta se convertiría en `401`. La colección lo documenta como regresión: antes de esa corrección, P14 y P27 devolvían `401` en lugar de `400`.

---

### P28 — Escritura con rol EMPLEADO

- **Método y ruta:** `POST {{baseUrl}}/api/productos`
- **Acceso:** EMPLEADO · `Bearer {{empleadoToken}}` (caso negativo)
- **Variables:** lee `empleadoToken`
- **Código esperado:** `403 Forbidden`

**Mensaje esperado:** `Acceso denegado: el rol del token no autoriza esta operacion`

**Body**

```json
{
  "nombre": "Producto de empleado",
  "descripcion": "rol EMPLEADO",
  "precio": 10,
  "idCategoria": 1,
  "stockReferencial": 1
}
```

**Qué demuestra.** Que "tener más permisos que un CLIENTE" no significa "tener todos". EMPLEADO puede operar monitoreo y auditoría, pero el catálogo es territorio de ADMIN. La regla del gateway es `hasRole(ADMIN)` para escritura, sin excepciones para EMPLEADO.

**Ojo.** Este caso evita un error común de diseño: usar un único rol "interno" para todo. La matriz del Anexo A muestra que los permisos son por recurso y por verbo.

---

## 03 Monitoreo

Esta carpeta prueba dos cosas a la vez: que el servicio de monitoreo **hace su trabajo** (sondea la salud de los demás y guarda historia) y que sus datos **no son públicos**. El acceso está restringido a ADMIN y EMPLEADO en el gateway y otra vez en el propio servicio.

### P13 — Estado de los servicios

- **Método y ruta:** `GET {{baseUrl}}/api/monitoreo/estado`
- **Acceso:** ADMIN · `Bearer {{adminToken}}` (EMPLEADO también permitido)
- **Variables:** lee `adminToken`
- **Código esperado:** `200 OK`

**Qué demuestra.** Que el BUS enruta `/api/monitoreo/**` al servicio de monitoreo y que ese servicio devuelve **una fila por servicio** con su última lectura. Cada fila trae `id`, `servicio`, `estado`, `tiempoRespuestaMs` y `fechaConsulta`.

El origen de los datos es un planificador (`@Scheduled`) que cada **30 segundos** sondea el `/actuator/health` de los 9 servicios restantes y guarda el resultado en `monitoreo_logs`. La consulta `ultimaPorServicio()` toma, para cada nombre, la fila con el `id` más alto.

El script es deliberadamente flexible: exige `200`, que sea un array y que tenga **al menos un elemento**. No exige 9 ni exige que todos estén `UP`.

**Ojo.** Si acabas de arrancar el stack, espera 30 segundos: antes de la primera ronda la lista está vacía y el assert falla. Y si un servicio está caído, la fila aparece como `DOWN`: eso **es** el dato que el monitoreo existe para mostrar, no un fallo de la prueba.

---

### P13b — Historial de monitoreo

- **Método y ruta:** `GET {{baseUrl}}/api/monitoreo/historial?limite=5`
- **Acceso:** ADMIN · `Bearer {{adminToken}}`
- **Variables:** lee `adminToken`
- **Código esperado:** `200 OK`

**Qué demuestra.** La diferencia entre una **foto** y una **serie temporal**. `/estado` devuelve la última lectura de cada servicio; `/historial` devuelve las últimas N lecturas, ordenadas de la más reciente a la más antigua (`ORDER BY fecha_consulta DESC, id DESC`) y limitadas por el parámetro.

El script solo valida `200` y que sea un array: con `limite=5` puede haber menos filas si el planificador todavía no acumuló historia.

**Ojo.** Este endpoint es el que usaría un dashboard para graficar disponibilidad en el tiempo. El límite se valida en el controlador: debe estar entre 1 y 500 (ver P14).

---

### P14 — Historial con límite inválido

- **Método y ruta:** `GET {{baseUrl}}/api/monitoreo/historial?limite=0`
- **Acceso:** ADMIN · `Bearer {{adminToken}}`
- **Variables:** lee `adminToken`
- **Código esperado:** `400 Bad Request`

**Qué demuestra.** Que el servicio valida sus parámetros de negocio: `limite < 1` o `limite > 500` produce `400` con el mensaje `limite debe estar entre 1 y 500`.

Pero lo más importante del caso es lo que **no** pasa: la respuesta es `400`, no `401`. Un error del cliente no debe disfrazarse de error de autenticación. Ese disfraz ocurría cuando el despacho interno a `/error` volvía a pasar por la cadena de seguridad; la corrección fue declarar `/error` como `permitAll` en el gateway y en los servicios. Este caso es, literalmente, la prueba de regresión de ese bug.

**Ojo.** El token es válido y el rol alcanza; por eso el análisis de seguridad termina antes y el error es puramente funcional. Es el mismo patrón que P27 en el catálogo.

---

### P29 — Monitoreo sin token

- **Método y ruta:** `GET {{baseUrl}}/api/monitoreo/estado`
- **Acceso:** ADMIN/EMPLEADO requerido · se envía **sin token** (caso negativo)
- **Variables:** ninguna
- **Código esperado:** `401 Unauthorized`

**Mensaje esperado:** `No autenticado: se requiere un token JWT valido`

**Qué demuestra.** Que la información de salud de la infraestructura es **interna**. La regla del gateway es `hasAnyRole(ADMIN, EMPLEADO)`; sin identidad no hay forma de evaluarla, así que responde `401` antes de enrutar.

**Ojo.** Un endpoint de monitoreo expuesto sin autenticación es un hallazgo clásico de auditoría: revela qué servicios existen, cuáles están caídos y cuánto tardan. Es información útil para un atacante.

---

### P30 — Monitoreo con rol CLIENTE

- **Método y ruta:** `GET {{baseUrl}}/api/monitoreo/estado`
- **Acceso:** CLIENTE · `Bearer {{clienteToken}}` (caso negativo)
- **Variables:** lee `clienteToken`
- **Código esperado:** `403 Forbidden`

**Mensaje esperado:** `Acceso denegado: el rol del token no autoriza esta operacion`

**Qué demuestra.** Que un cliente autenticado sigue sin poder ver la topología interna. El token es válido, pero `ROLE_CLIENTE` no está en `hasAnyRole(ADMIN, EMPLEADO)`.

**Ojo.** Este `403` lo emite el gateway. Si alguien saltara el BUS, el servicio de monitoreo tiene su propia regla equivalente y también respondería `403` (o `401` sin token). La seguridad no depende de una sola puerta.

---

### P31 — Monitoreo con rol EMPLEADO

- **Método y ruta:** `GET {{baseUrl}}/api/monitoreo/estado`
- **Acceso:** EMPLEADO · `Bearer {{empleadoToken}}`
- **Variables:** lee `empleadoToken`
- **Código esperado:** `200 OK`

**Qué demuestra.** Que EMPLEADO sí pertenece al conjunto de roles operativos. Es la contraparte positiva de P30 y confirma que la matriz no es "solo ADMIN" sino "roles de operación".

El script valida `200` y que la respuesta sea un array.

**Ojo.** Este caso y P28 usan el mismo token con resultados opuestos (`200` vs `403`). Es la mejor demostración de que el permiso depende del **recurso**, no del usuario.

---

## 04 Auditoria

Cada petición que cruza el BUS se registra en `db_esb.auditoria_logs`, incluidas las rechazadas por seguridad. Esta carpeta prueba que ese registro existe, que se puede consultar y que no cualquiera puede leerlo.

### P15 — Auditoría del BUS

- **Método y ruta:** `GET {{baseUrl}}/api/auditoria?limite=5`
- **Acceso:** ADMIN · `Bearer {{adminToken}}` (EMPLEADO también permitido)
- **Variables:** lee `adminToken`
- **Código esperado:** `200 OK`

**Qué demuestra.** Que el gateway tiene su propia auditoría y la expone. Cada fila trae `id`, `servicioOrigen`, `servicioDestino`, `operacion` (el verbo), `endpoint`, `estadoRespuesta`, `usuario` y `timestamp`. La tabla guarda además una columna `detalle` con el rol, la duración, el remitente y, en los rechazos, el motivo; ese detalle no se incluye en la respuesta de este endpoint.

Dos decisiones de diseño se ven aquí:

1. **`/api/auditoria` es un controlador local del gateway**, no una ruta proxeada. No aparece en la tabla de direccionamiento y no depende de otro servicio.
2. **La escritura es asíncrona.** `AuditFilter` captura la transacción y la entrega a un publicador con un ejecutor acotado. Un fallo o una base lenta no bloquean ni rompen la respuesta al cliente.

**Ojo.** Por ser asíncrona, la fila de la petición que acabas de hacer puede no estar todavía. Si consultas y no la ves, vuelve a mirar: no es un bug. Además, `/actuator/**` queda fuera del filtro, así que P37 no genera fila.

---

### P16 — Resumen de auditoría

- **Método y ruta:** `GET {{baseUrl}}/api/auditoria/resumen`
- **Acceso:** ADMIN · `Bearer {{adminToken}}`
- **Variables:** lee `adminToken`
- **Código esperado:** `200 OK`

**Qué demuestra.** Que la auditoría no es solo un log: también es un agregado consultable. La respuesta tiene tres claves (ejemplo ilustrativo; los números dependen de tu base):

```json
{
  "total": 42,
  "errores": 7,
  "porServicio": {
    "techretail-producto": 18,
    "techretail-autenticacion": 12,
    "techretail-monitoreo": 5,
    "desconocido": 7
  }
}
```

- `total`: cantidad de filas.
- `errores`: filas con `estado_respuesta >= 400`. Es decir, **todos los 401, 403, 400 y 404 de esta colección cuentan aquí**. Es la métrica que demuestra que los intentos de bypass quedan registrados.
- `porServicio`: conteo agrupado por `servicio_destino`. El destino se resuelve por prefijo de ruta (`/api/productos` → `techretail-producto`, `/api/auth` → `techretail-autenticacion`, etc.); lo que no coincide queda como `desconocido`.

**Ojo.** Si ejecutas la colección varias veces, `total` y `errores` crecen y los números de un ejemplo dejan de coincidir. Lo que se valida es la **forma** de la respuesta, no los valores.

---

### P33 — Auditoría sin token

- **Método y ruta:** `GET {{baseUrl}}/api/auditoria`
- **Acceso:** ADMIN/EMPLEADO requerido · se envía **sin token** (caso negativo)
- **Variables:** ninguna
- **Código esperado:** `401 Unauthorized`

**Mensaje esperado:** `No autenticado: se requiere un token JWT valido`

**Qué demuestra.** Que la auditoría contiene identidades, rutas y resultados de operaciones: es información sensible desde el punto de vista de privacidad y cumplimiento. Sin autenticación, no se expone.

**Ojo.** El intento mismo queda registrado por `AuditFilter`, que se ejecuta **antes** de la cadena de seguridad precisamente para poder observar los rechazos. Es decir: la petición que falla por no estar autenticada también genera una fila de auditoría.

---

### P34 — Auditoría con rol CLIENTE

- **Método y ruta:** `GET {{baseUrl}}/api/auditoria`
- **Acceso:** CLIENTE · `Bearer {{clienteToken}}` (caso negativo)
- **Variables:** lee `clienteToken`
- **Código esperado:** `403 Forbidden`

**Mensaje esperado:** `Acceso denegado: el rol del token no autoriza esta operacion`

**Qué demuestra.** Que estar autenticado no da derecho a leer la actividad de otros usuarios. Un cliente podría ver endpoints, nombres de usuario y códigos de respuesta de toda la organización; eso viola el principio de mínimo privilegio y, en un caso real, normas de protección de datos.

**Ojo.** La distinción con P33 es la misma de siempre: `401` = no sé quién eres; `403` = sé quién eres y no te corresponde.

---

### P35 — Auditoría con rol EMPLEADO

- **Método y ruta:** `GET {{baseUrl}}/api/auditoria`
- **Acceso:** EMPLEADO · `Bearer {{empleadoToken}}`
- **Variables:** lee `empleadoToken`
- **Código esperado:** `200 OK`

**Qué demuestra.** Que EMPLEADO es un rol de operación y soporte: necesita ver la auditoría para diagnosticar incidentes, igual que necesita ver el monitoreo. El script valida `200` y array.

**Ojo.** Este caso cierra la matriz de la carpeta: el mismo recurso devuelve `401` sin token (P33), `403` con CLIENTE (P34) y `200` con EMPLEADO (P35).

---

## 05 Seguridad

Las otras carpetas prueban funcionalidad; esta prueba **resistencia**. Los seis casos simulan intentos de saltarse los controles: modificar un token, llamar directo al servicio, usar un esquema de autenticación distinto, consultar infraestructura y descubrir una ruta declarada pero no implementada.

### P17 — Token alterado

- **Método y ruta:** `GET {{baseUrl}}/api/productos/1`
- **Acceso:** Público en teoría · se envía un token **manipulado** (caso negativo)
- **Variables:** lee `adminToken`
- **Código esperado:** `401 Unauthorized`

**Cabecera enviada:** `Authorization: Bearer {{adminToken}}firma_alterada`

**Mensaje esperado:** `token invalido o expirado`

**Qué demuestra.** La integridad de la firma. Un JWT tiene tres partes separadas por puntos: cabecera, cuerpo y firma. Si cambias un solo carácter del cuerpo (por ejemplo, para subir el rol a ADMIN) o de la firma, el cálculo HMAC-SHA256 deja de coincidir y JJWT rechaza el token.

El detalle fino: la ruta `GET /api/productos/1` es **pública**. Si el filtro ignorara las cabeceras inválidas "porque total la ruta es pública", un atacante podría enviar un token falso y ser tratado como anónimo. La política es **fail-closed**: si viene una cabecera `Authorization`, se valida; si no valida, la petición termina en `401` aunque el recurso fuera accesible. Así el intento de bypass nunca continúa y queda auditado con `motivo=token invalido o expirado`.

**Ojo.** `401` y no `403` porque no se puede establecer una identidad: el token no es confiable.

---

### P18a — Acceso directo (lectura pública)

- **Método y ruta:** `GET http://localhost:8082/api/productos`
- **Acceso:** Público (sin token, **sin pasar por el BUS**)
- **Variables:** ninguna
- **Código esperado:** `200 OK`

**Qué demuestra.** Que el servicio de producto tiene su propia cadena de seguridad y que la lectura pública también funciona cuando se lo llama directo. Este caso prepara el contraste con P18b: si el servicio no tuviera seguridad propia, P18b también respondería `200` y el catálogo estaría abierto a cualquiera dentro de la red.

**Ojo.** La URL es absoluta (`http://localhost:8082`), no `{{baseUrl}}`. Es el único par de la colección que **no** pasa por el gateway, y esa es toda su razón de ser. Si el servicio no está levantado, verás un error de conexión, no un código HTTP.

---

### P18b — Acceso directo (escritura sin token)

- **Método y ruta:** `POST http://localhost:8082/api/productos`
- **Acceso:** ADMIN requerido · sin token y **sin pasar por el BUS** (caso negativo)
- **Variables:** ninguna
- **Código esperado:** `401 Unauthorized`

**Mensaje esperado:** `No autenticado: se requiere un token JWT valido`

**Body**

```json
{
  "nombre": "Directo sin token",
  "descripcion": "x",
  "precio": 10,
  "idCategoria": 1,
  "stockReferencial": 1
}
```

**Qué demuestra.** El concepto de **Zero Trust** y la segunda capa de seguridad. En un modelo tradicional de "castillo y foso", el perímetro (el BUS) es el único control: si alguien ya está dentro de la red, puede llamar a los servicios internos y hacer lo que quiera. Aquí no.

El servicio `techretail-producto` tiene su propio `SecurityFilterChain`, su propio `JwtAuthenticationFilter` y su propio `JwtService`. No confía en que el BUS ya haya validado nada: **vuelve a validar la firma y el rol**. Por eso este POST directo, sin token, recibe exactamente el mismo `401` que P7, pero de una capa distinta.

Si comparas P7 y P18b:

| | P7 | P18b |
|---|---|---|
| Destino | `:8090` (BUS) | `:8082` (servicio) |
| Quién responde | Gateway | techretail-producto |
| Código y mensaje | `401` / mismo texto | `401` / mismo texto |
| Qué prueba | El BUS filtra | El servicio se defiende solo |

**Ojo.** Hay un matiz honesto que conviene mencionar en la sustentación: como esta petición **no pasa por el BUS**, tampoco aparece en `db_esb.auditoria_logs`. Zero Trust protege el dato, pero la auditoría centralizada sigue dependiendo del gateway. Cerrar esa brecha requeriría que cada servicio publique sus propios eventos de seguridad; hoy no lo hace.

---

### P36 — Formato de Authorization inválido

- **Método y ruta:** `GET {{baseUrl}}/api/productos/1`
- **Acceso:** Público en teoría · cabecera con esquema **Basic** (caso negativo)
- **Variables:** ninguna
- **Código esperado:** `401 Unauthorized`

**Cabecera enviada:** `Authorization: Basic YWRtaW46YWRtaW4=`

**Mensaje esperado:** `esquema de Authorization no soportado`

**Qué demuestra.** Que el sistema solo acepta `Bearer` y que **no existe una vía alterna de autenticación**. El filtro no intenta interpretar Basic, Digest ni ningún otro esquema: si la cabecera no empieza con `Bearer ` y no trae un token no vacío, rechaza.

Esto cierra una puerta clásica: si HTTP Basic estuviera habilitado, un atacante podría probar credenciales por fuerza bruta contra cualquier ruta, o usar credenciales por defecto. En este proyecto `httpBasic`, `formLogin` y `logout` están explícitamente deshabilitados; no hay usuario ni contraseña por defecto a los que recurrir.

La misma respuesta se aplica si llegan **dos** cabeceras `Authorization`: ambigüedad = rechazo.

**Ojo.** Es un `401` en una ruta pública, igual que P17. La lección es la misma: la seguridad no se relaja porque el recurso sea de lectura; si hay una credencial inválida, se rechaza.

---

### P37 — Health público del gateway

- **Método y ruta:** `GET {{baseUrl}}/actuator/health`
- **Acceso:** Público (sin token)
- **Variables:** ninguna
- **Código esperado:** `200 OK`

**Respuesta esperada**

```json
{
  "status": "UP"
}
```

**Qué demuestra.** Que la infraestructura tiene un punto de salud público y **mínimo**. El gateway expone solo `health` e `info`, y con `show-details=never`: no revela componentes internos, versiones de la base ni estado de conexiones. Es lo justo para que un orquestador o el propio monitoreo sepan si el proceso está vivo.

El servicio de monitoreo consulta este endpoint (junto con el de los otros ocho servicios) cada 30 segundos; de ahí salen las filas de P13. El script valida `200` y `status === "UP"`.

**Ojo.** `/actuator/**` está excluido de `AuditFilter`: sería ruido registrar un chequeo cada 30 segundos por servicio. Por eso P37 no genera fila en la auditoría.

---

### P38 — Ruta UDDI sin implementar

- **Método y ruta:** `GET {{baseUrl}}/api/registro/servicios`
- **Acceso:** Público según la configuración, pero **sin implementación**
- **Variables:** ninguna
- **Código esperado:** `404 Not Found`

**Qué demuestra.** Que `permitAll` no crea endpoints. La regla de seguridad del gateway declara `GET /api/registro/servicios` como pública (pensando en un futuro registro UDDI de descubrimiento), pero no existe ningún controlador que la atienda ni ruta que la reenvíe. El resultado es un `404`.

La lección conceptual: **autorización y enrutamiento son responsabilidades distintas**. Pasar el filtro de seguridad no garantiza que haya algo del otro lado. Un `404` aquí no es una falla de seguridad; es una funcionalidad pendiente, documentada como hueco de implementación UDDI en la auditoría del proyecto.

**Ojo.** `ServiceDestinationResolver` sí mapea el prefijo `/api/registro` a `techretail-gateway` para la columna `servicio_destino`, pero eso solo afecta cómo se etiquetaría una fila de auditoría; no crea la ruta.

---

## Anexo A — Matriz de acceso por rol

Esta es la política que aplican **el gateway y cada servicio** (defense in depth). `—` significa que la petición no llega a evaluarse porque no hay identidad válida.

| Recurso | Público | CLIENTE | EMPLEADO | ADMIN |
|---|---|---|---|---|
| `POST /api/auth/login` | ✅ | ✅ | ✅ | ✅ |
| `POST /api/auth/registro` | ✅ | ✅ | ✅ | ✅ |
| `GET /api/productos/**` | ✅ | ✅ | ✅ | ✅ |
| `POST /api/productos/**` | — | 403 | 403 | ✅ |
| `PUT /api/productos/**` | — | 403 | 403 | ✅ |
| `GET /api/monitoreo/**` | — | 403 | ✅ | ✅ |
| `GET /api/auditoria/**` | — | 403 | ✅ | ✅ |
| `GET /actuator/health`, `/actuator/info` | ✅ | ✅ | ✅ | ✅ |
| `GET /api/registro/servicios` | 404 (sin controller) | 404 | 404 | 404 |
| `/api/clientes/**` | — | ✅ | ✅ | ✅ |
| `/api/pedidos/**` | — | ✅ | ✅ | ✅ |
| `/api/notificaciones/**` | — | ✅ | ✅ | ✅ |
| `/api/pagos/**` | — | ✅ | 403 | ✅ |
| `/api/inventario/**` | — | 403 | ✅ | ✅ |
| `/ws/facturacion/**` | — | 403 | ✅ | ✅ |

> Las cuatro últimas filas corresponden a módulos que aún no tienen endpoints de negocio implementados; se incluyen porque la política del gateway ya está declarada y sirve para leer la matriz completa.

---

## Anexo B — Los 40 casos en orden de ejecución

| # | ID | Método | Ruta | Acceso | Esperado |
|---:|---|---|---|---|---|
| 1 | P1 | POST | `/api/auth/login` | Público | 200 |
| 2 | P2 | POST | `/api/auth/login` | Público | 401 |
| 3 | P3 | POST | `/api/auth/registro` | Público | 201 |
| 4 | P20 | POST | `/api/auth/registro` | Público | 409 |
| 5 | P21 | POST | `/api/auth/registro` | Público | 400 |
| 6 | P8a | POST | `/api/auth/login` | Público | 200 |
| 7 | P19 | POST | `/api/auth/login` | Público | 200 |
| 8 | P4 | GET | `/api/productos` | Público | 200 |
| 9 | P5 | GET | `/api/productos?q=monitor&soloDisponibles=true` | Público | 200 |
| 10 | P22 | GET | `/api/productos/categorias` | Público | 200 |
| 11 | P6 | GET | `/api/productos/999999` | Público | 404 |
| 12 | P7 | POST | `/api/productos` | ADMIN (sin token) | 401 |
| 13 | P8b | POST | `/api/productos` | CLIENTE | 403 |
| 14 | P9 | POST | `/api/productos` | ADMIN | 201 |
| 15 | P23 | GET | `/api/productos/{{productoId}}` | Público | 200 |
| 16 | P10 | PUT | `/api/productos/{{productoId}}` | ADMIN | 200 |
| 17 | P11 | PUT | `/api/productos/{{productoId}}` | ADMIN | 200 |
| 18 | P12 | POST | `/api/productos` | ADMIN | 400 |
| 19 | P24 | POST | `/api/productos/categorias` | ADMIN | 201 |
| 20 | P25 | PUT | `/api/productos/999999` | ADMIN | 404 |
| 21 | P26 | POST | `/api/productos` | ADMIN | 404 |
| 22 | P27 | GET | `/api/productos/abc` | Público | 400 |
| 23 | P28 | POST | `/api/productos` | EMPLEADO | 403 |
| 24 | P13 | GET | `/api/monitoreo/estado` | ADMIN | 200 |
| 25 | P13b | GET | `/api/monitoreo/historial?limite=5` | ADMIN | 200 |
| 26 | P14 | GET | `/api/monitoreo/historial?limite=0` | ADMIN | 400 |
| 27 | P29 | GET | `/api/monitoreo/estado` | ADMIN (sin token) | 401 |
| 28 | P30 | GET | `/api/monitoreo/estado` | CLIENTE | 403 |
| 29 | P31 | GET | `/api/monitoreo/estado` | EMPLEADO | 200 |
| 30 | P15 | GET | `/api/auditoria?limite=5` | ADMIN | 200 |
| 31 | P16 | GET | `/api/auditoria/resumen` | ADMIN | 200 |
| 32 | P33 | GET | `/api/auditoria` | ADMIN (sin token) | 401 |
| 33 | P34 | GET | `/api/auditoria` | CLIENTE | 403 |
| 34 | P35 | GET | `/api/auditoria` | EMPLEADO | 200 |
| 35 | P17 | GET | `/api/productos/1` | Público (token alterado) | 401 |
| 36 | P18a | GET | `http://localhost:8082/api/productos` | Público (directo) | 200 |
| 37 | P18b | POST | `http://localhost:8082/api/productos` | ADMIN (directo, sin token) | 401 |
| 38 | P36 | GET | `/api/productos/1` | Público (Basic) | 401 |
| 39 | P37 | GET | `/actuator/health` | Público | 200 |
| 40 | P38 | GET | `/api/registro/servicios` | Público | 404 |

---

## Anexo C — Problemas frecuentes

| Síntoma | Causa probable | Caso que lo explica |
|---|---|---|
| `401` con `Bearer ` y el token vacío | No ejecutaste el login que llena la variable. | P1, P8a, P19 |
| `401` en un `GET` que debería ser público | Enviaste una cabecera `Authorization` inválida; el sistema es fail-closed. | P17, P36 |
| `403` con un token que parece correcto | El token es válido, pero el rol no alcanza para esa ruta. | P8b, P28, P30, P34 |
| `400` en una ruta pública | El identificador o un parámetro tiene formato inválido. | P12, P14, P27 |
| `404` en `/api/productos/999999` | El id es válido pero el producto no existe. | P6, P25 |
| `404` en `/api/productos/abc` | No es un `404`: el id no es numérico, debería ser `400`. Revisa que estés usando la URL correcta. | P27 |
| `404` en `/api/registro/servicios` | La ruta está declarada como pública pero no implementada. | P38 |
| `409` al registrar el usuario QA | Ya existe de una ejecución anterior. Es el comportamiento esperado de P20, pero rompe el `201` de P3. | P3, P20 |
| `200` con `[]` en P4 o P5 | No hay productos sembrados, o ninguno coincide con el filtro. Una lista vacía es una respuesta válida. | P4, P5 |
| P13 falla con lista vacía | El planificador todavía no completó su primera ronda (30 s). | P13 |
| P15 no muestra la petición recién hecha | La auditoría se escribe de forma asíncrona. | P15 |
| P3 falla en la segunda corrida completa | El usuario fijo ya existe; el resultado correcto pasa a ser `409`. | P3, P20 |
| P23, P10 o P11 apuntan a un producto raro | P9 falló y `productoId` quedó con el valor inicial `4`. | P9, P23 |

---

## Cierre

Si tuvieras que resumir esta colección en tres ideas para la sustentación, serían estas:

1. **La seguridad está en dos capas.** El BUS decide si la petición pasa; el servicio vuelve a decidir si la acepta. P7 y P18b devuelven lo mismo desde lugares distintos, y esa repetición es intencional.
2. **401 y 403 no son sinónimos.** El primero dice "no sé quién eres"; el segundo, "sé quién eres y no te corresponde". La colección demuestra la diferencia tres veces, con los tres roles.
3. **Los códigos de error son parte del contrato.** `400`, `404`, `409` y `401` cuentan historias distintas; confundirlos (por ejemplo, devolver `401` ante un `limite=0`) es un defecto, no un detalle. P14 y P27 existen precisamente para vigilar que eso no vuelva a pasar.
