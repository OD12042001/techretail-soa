# PROYECTO FINAL
## ARQUITECTURA ORIENTADA A SERVICIOS (100000SI84)
### Avance 3 (Semana 15) — Implementación Parcial

**Nombre del proyecto:** Implementación de una Arquitectura SOA para TechRetail S.A.

**Curso:** Arquitectura Orientada al Servicio (100000SI84)
**Ciclo:** 2026 — Ciclo 2 Agosto
**Carrera:** Ingeniería de Sistemas e Informática
**Docente:** Calderón Chuquillanqui, Anthony Vidal
**Equipo:** Grupo 1
**Integrante:** [COMPLETAR: Apellidos, Nombres]
**Fecha de entrega:** 16 de noviembre de 2026

---

# 1. Resumen Ejecutivo

Este Avance 3 materializa una porción funcional de la arquitectura diseñada en el Avance 2. Ya no se describe una propuesta: se presenta código ejecutable, con servicios Spring Boot independientes, bases de datos separadas, seguridad con tokens JWT y evidencia de pruebas automatizadas.

El alcance implementado cubre cuatro módulos: **Autenticación** (`techretail-autenticacion`, puerto 8088), que emite y valida los tokens de acceso con los roles `ADMIN`, `EMPLEADO` y `CLIENTE`; **Producto** (`techretail-producto`, puerto 8082), que expone el catálogo con lectura pública y escritura restringida a `ADMIN`; **Monitoreo** (`techretail-monitoreo`, puerto 8089), que sondea la salud de los demás servicios de forma periódica y persiste el resultado; y el **BUS/Gateway** (`techretail-gateway`, puerto 8090), que actúa como punto único de entrada, valida el token de forma centralizada, enruta hacia los servicios de destino y registra cada transacción en la tabla de auditoría.

Tres decisiones de diseño sostienen el conjunto:

- **Database per Service real.** Cada módulo tiene su propia base de datos PostgreSQL y su propio historial Flyway. Ningún servicio consulta las tablas de otro; la integración ocurre por HTTP a través del BUS.
- **Seguridad en dos capas.** El BUS rechaza toda petición sin token válido antes de enrutarla y aplica las reglas de rol por ruta; cada servicio de negocio vuelve a validar el token por su cuenta, de modo que un acceso directo al puerto del servicio no evade la política.
- **Auditoría asíncrona.** El BUS escribe una fila por cada transacción en `db_esb.auditoria_logs` sin bloquear la respuesta al cliente: un fallo de la base de auditoría no rompe la operación de negocio.

La observabilidad se resuelve en cuatro frentes complementarios (§2.6): sondas de salud con Actuator, trazas distribuidas con OpenTelemetry exportadas a Jaeger, monitoreo activo con persistencia en `monitoreo_logs` y auditoría asíncrona de cada transacción en el BUS.

El estado de pruebas automatizadas es de **115 pruebas** en los cuatro módulos implementados, distribuidas en 26 de autenticación, 37 de producto, 27 del BUS y 25 de monitoreo. La suite se ejecutó completa el **08/11/2026** con `.\mvnw.cmd clean test`: **115 pruebas, 0 fallos, 0 errores y 0 omitidas**, con `BUILD SUCCESS` en los cuatro módulos. Con las 12 pruebas de contexto de los seis módulos de andamiaje —también en verde—, el repositorio completo suma **127 pruebas**. Las pruebas manuales con Postman, con la petición y la respuesta reales de cada caso, se documentan en la sección 3.2.

La integración entre servicios se documenta como **orquestación mediada por el BUS** (sección 4), con diagrama de componentes y de secuencia: el BUS delega en Autenticación y en Producto, y Monitoreo invoca periódicamente a los demás servicios. La publicación de servicios se resuelve hoy con el registro estático de rutas del BUS y un contrato OpenAPI, con el catálogo UDDI como evolución (sección 5). La superficie expuesta por el BUS se somete a un escaneo activo con **OWASP ZAP** (sección 6).

**Alcance declarado con honestidad:** los seis servicios restantes (cliente, pedido, pago, inventario, notificación y facturación SUNAT) continúan como andamiaje —módulo, configuración y migración inicial— y no forman parte de la demostración de este avance.

---

# 2. Servicios Implementados

Los cuatro módulos son proyectos Maven **independientes**, no un reactor multi-módulo. Cada uno se compila y se ejecuta desde su propio directorio con su propio `mvnw`, sobre Java 21 y Spring Boot 4.1.1.

| Módulo | Puerto | Base de datos | Responsabilidad |
|---|---|---|---|
| `techretail-autenticacion` | 8088 | `db_autenticacion` | Registro, login y emisión de JWT |
| `techretail-producto` | 8082 | `db_producto` | Catálogo de productos y categorías |
| `techretail-monitoreo` | 8089 | `db_monitoreo` | Sondeo de salud y tiempos de respuesta |
| `techretail-gateway` | 8090 | `db_esb` | Enrutamiento, seguridad central y auditoría |

**Stack técnico** (verificado contra los `pom.xml` y `docker-compose.yml`):

| Capa | Tecnología | Dónde se usa |
|---|---|---|
| Lenguaje | Java 21 | Los cuatro módulos |
| Framework | Spring Boot 4.1.1 con starters modulares (`spring-boot-starter-webmvc`) | Los cuatro módulos |
| Seguridad | Spring Security + jjwt 0.11.5 (JWT HS256); BCrypt costo 12 | Emisión en autenticación; validación en BUS, producto y monitoreo |
| Persistencia | Spring Data JPA + Flyway | Autenticación, producto, monitoreo |
| Persistencia | `JdbcTemplate` + Flyway (sin JPA, a propósito) | BUS / Gateway |
| Base de datos | PostgreSQL 16 (`postgres:16-alpine`), una base por servicio | Los cuatro módulos |
| Integración | Spring Cloud Gateway Server WebMVC (servlet) | BUS / Gateway |
| Integración | `java.net.http.HttpClient` + `@Scheduled` | Sondeo de monitoreo |
| Validación | Bean Validation (`spring-boot-starter-validation`) | Autenticación, producto, BUS |
| Observabilidad | Actuator, `spring-boot-starter-opentelemetry`, Jaeger 1.62.0 | Los cuatro módulos |
| Build y pruebas | Maven Wrapper, JUnit Jupiter, MockMvc, H2 (`MODE=PostgreSQL`) | Los cuatro módulos |

## 2.1 Autenticación (`techretail-autenticacion`)

Es el único servicio con endpoints de negocio públicos: `/api/auth/**` es la puerta de entrada al sistema. El resto de sus rutas exige token, y la emisión de contraseñas se hace con BCrypt con costo 12.

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| `POST` | `/api/auth/registro` | Público | Crea una cuenta con rol `CLIENTE` y devuelve `201` |
| `POST` | `/api/auth/login` | Público | Valida credenciales y devuelve el token con su rol |
| `GET` | `/actuator/health` | Público | Sonda de salud para el servicio de monitoreo |

Los usuarios de demostración se aprovisionan de forma idempotente al arrancar, uno por rol (contraseñas `Admin123!`, `Empleado123!`, `Cliente123!`), y se pueden desactivar con `AUTH_DEMO_USERS=false`. Sin ellos no habría forma de demostrar los roles `ADMIN` y `EMPLEADO`, porque el registro público solo crea `CLIENTE`.

### 2.1.1 Emisión y validación del token — `JwtService`

El token se firma con **HS256** y un secreto de al menos 32 bytes, validado en el constructor: un secreto corto falla al arrancar, no en la primera petición. El payload respeta el contrato del Avance 2 §8.2 (`sub`, `rol`, `idUsuario`, `iat`, `exp`) más el emisor `iss` que el BUS exige.

`techretail-autenticacion/src/main/java/com/techretail/techretail_autenticacion/security/JwtService.java` (fragmento):

```java
@Service
public class JwtService {

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = construirClave(properties.getSecret());
    }

    private static SecretKey construirClave(String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "techretail.security.jwt.secret debe tener al menos 32 bytes para HS256");
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** Firma un token para el usuario autenticado. */
    public String emitir(String usuario, Rol rol, Long idUsuario) {
        Instant ahora = Instant.now();
        Instant expiracion = ahora.plus(properties.getExpirationMinutes(), ChronoUnit.MINUTES);

        var constructor = Jwts.builder()
                .setSubject(usuario)
                .claim(JwtClaims.ROL, rol.name())
                .setIssuer(properties.getIssuer())
                .setIssuedAt(Date.from(ahora))
                .setExpiration(Date.from(expiracion));
        if (idUsuario != null) {
            constructor.claim(JwtClaims.ID_USUARIO, idUsuario);
        }
        return constructor.signWith(signingKey, SignatureAlgorithm.HS256).compact();
    }

    /**
     * @return los datos del portador si el token es valido; vacio en cualquier
     *         otro caso (firma, expiracion, emisor, rol o formato).
     */
    public Optional<TokenValidado> validar(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .requireIssuer(properties.getIssuer())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            String usuario = claims.getSubject();
            Object rolClaim = claims.get(JwtClaims.ROL);
            Optional<Rol> rol = Rol.desde(rolClaim instanceof String valor ? valor : null);
            if (usuario == null || usuario.isBlank() || rol.isEmpty()) {
                return Optional.empty();
            }

            // Un JWT sin exp vivio para siempre: se rechaza aunque la firma sea valida.
            if (claims.getExpiration() == null) {
                return Optional.empty();
            }

            Long idUsuario = null;
            Object idClaim = claims.get(JwtClaims.ID_USUARIO);
            if (idClaim instanceof Number numero) {
                idUsuario = numero.longValue();
            }

            return Optional.of(new TokenValidado(usuario, rol.get(), idUsuario,
                    claims.getExpiration().toInstant()));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
```

Dos detalles importan para la rúbrica de seguridad: el mismo `JwtService` se replica en el BUS, producto y monitoreo (verificación sin llamar al servicio de autenticación en cada petición), y **un token sin `exp` se rechaza aunque la firma sea válida** — se trata de una corrección derivada de la verificación independiente descrita en la sección 5.

### 2.1.2 Política de seguridad — `SecurityConfig`

`techretail-autenticacion/src/main/java/com/techretail/techretail_autenticacion/config/SecurityConfig.java`:

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    SecurityFilterChain authenticationSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(cabeceras -> cabeceras.frameOptions(marco -> marco.deny()))
                .authorizeHttpRequests(autorizacion -> autorizacion
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint(new RestAuthenticationEntryPoint())
                        .accessDeniedHandler(new RestAccessDeniedHandler()));

        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
```

La cadena desactiva HTTP Basic, formulario de login y sesiones: no existe una credencial por defecto de Spring Boot que pueda usarse para saltarse la autenticación. Los errores `401` y `403` se devuelven en JSON mediante manejadores propios, nunca como una página HTML.

## 2.2 Producto (`techretail-producto`)

Expone el catálogo. La lectura es **pública** y la escritura requiere rol `ADMIN`. No existe `DELETE`: un producto se retira del catálogo con `disponible=false`, preservando el historial de precios y la referencia desde pedidos ya emitidos.

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| `GET` | `/api/productos` | Público | Lista con filtros `categoria`, `q` y `soloDisponibles` |
| `GET` | `/api/productos/{id}` | Público | Detalle de un producto (`404` si no existe) |
| `GET` | `/api/productos/categorias` | Público | Catálogo de categorías |
| `POST` | `/api/productos` | `ADMIN` | Crea un producto y devuelve `201` con cabecera `Location` |
| `POST` | `/api/productos/categorias` | `ADMIN` | Crea una categoría |
| `PUT` | `/api/productos/{id}` | `ADMIN` | Actualiza o retira un producto |

`techretail-producto/src/main/java/com/techretail/techretail_producto/producto/ProductoController.java`:

```java
@RestController
@RequestMapping("/api/productos")
public class ProductoController {

    private final ProductoService servicio;

    public ProductoController(ProductoService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public List<ProductoResponse> listar(
            @RequestParam(name = "categoria", required = false) Long categoria,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "soloDisponibles", defaultValue = "false") boolean soloDisponibles) {
        return servicio.listar(categoria, q, soloDisponibles);
    }

    @GetMapping("/categorias")
    public List<CategoriaResponse> categorias() {
        return servicio.categorias();
    }

    @PostMapping("/categorias")
    public ResponseEntity<CategoriaResponse> crearCategoria(@Valid @RequestBody CategoriaRequest peticion) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.crearCategoria(peticion));
    }

    @GetMapping("/{id}")
    public ProductoResponse obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }

    @PostMapping
    public ResponseEntity<ProductoResponse> crear(@Valid @RequestBody ProductoRequest peticion) {
        ProductoResponse creado = servicio.crear(peticion);
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(creado.id()).toUri();
        return ResponseEntity.created(ubicacion).body(creado);
    }

    @PutMapping("/{id}")
    public ProductoResponse actualizar(@PathVariable Long id, @Valid @RequestBody ProductoRequest peticion) {
        return servicio.actualizar(id, peticion);
    }
}
```

El cuerpo de entrada se valida con Bean Validation antes de tocar la base: `nombre` obligatorio de hasta 150 caracteres, `precio` mayor que cero con dos decimales, `idCategoria` y `stockReferencial` obligatorios (este último no negativo). `disponible` es opcional y su ausencia equivale a `true`.

## 2.3 Monitoreo (`techretail-monitoreo`)

Realiza sondeos periódicos de salud contra los otros servicios y guarda el resultado con su tiempo de respuesta. El scheduler está desactivado en el perfil de pruebas para que la suite sea determinista.

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| `GET` | `/api/monitoreo/estado` | `ADMIN`, `EMPLEADO` | Último estado registrado por servicio |
| `GET` | `/api/monitoreo/historial?limite=N` | `ADMIN`, `EMPLEADO` | Historial reciente; `limite` entre 1 y 500 (`400` fuera de rango) |

`techretail-monitoreo/src/main/java/com/techretail/techretail_monitoreo/salud/HttpHealthProbe.java`:

```java
/**
 * Sondea {@code GET <url>} y considera UP solo una respuesta 200 cuyo cuerpo
 * declare {@code "status":"UP"}. Cualquier otro resultado (codigo, timeout,
 * conexion rechazada) es DOWN.
 */
public class HttpHealthProbe implements HealthProbe {

    private final HttpClient cliente;
    private final Duration timeout;

    public HttpHealthProbe(int timeoutMs) {
        this.timeout = Duration.ofMillis(timeoutMs);
        this.cliente = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    @Override
    public ResultadoSonda sondear(ServicioObjetivo servicio) {
        long inicio = System.nanoTime();
        String estado = "DOWN";
        try {
            HttpRequest peticion = HttpRequest.newBuilder(URI.create(servicio.url()))
                    .timeout(timeout).GET().build();
            HttpResponse<String> respuesta = cliente.send(peticion, HttpResponse.BodyHandlers.ofString());
            if (respuesta.statusCode() == 200 && respuesta.body().replace(" ", "").contains("\"status\":\"UP\"")) {
                estado = "UP";
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            // Cualquier fallo de red o URL invalida es DOWN.
        }
        return new ResultadoSonda(estado, (int) ((System.nanoTime() - inicio) / 1_000_000L));
    }
}
```

El criterio es deliberadamente estricto: **un `200` no basta**. Si el cuerpo no declara `"status":"UP"`, el servicio se considera caído. Así, una página de error que responda `200` o un proxy mal configurado no se reportan como saludables.

## 2.4 BUS / Gateway (`techretail-gateway`)

Es el punto único de entrada al sistema. Se implementó sobre **Spring WebMVC (servlet)**, no reactivo, y usa `JdbcTemplate` contra `db_esb`: se descartaron deliberadamente `webflux` y R2DBC para que la demostración de auditoría sea verificable con SQL directo. Sus tres funciones son:

1. **Validar el token** en un filtro propio antes de enrutar. Sin token, token alterado o token expirado se responde `401` en JSON y la petición **nunca llega al servicio destino**.
2. **Aplicar la política de roles por ruta**: catálogo público en lectura y `ADMIN` en escritura; pagos para `ADMIN`/`CLIENTE`; inventario, monitoreo y auditoría para `ADMIN`/`EMPLEADO`; facturación para `ADMIN`/`EMPLEADO`; clientes, pedidos y notificaciones para cualquier usuario autenticado.
3. **Auditar de forma asíncrona**: `AuditFilter` entrega la entrada a un publicador con su propio ejecutor acotado, que inserta en `auditoria_logs` (`servicio_origen`, `servicio_destino`, `operacion`, `endpoint`, `estado_respuesta`, `usuario`, `detalle`). Un fallo de la base de auditoría se registra en log y no interrumpe la respuesta.

Además expone dos rutas locales que **no** se enrutan a ningún servicio, porque el prefijo `/api/monitoreo/**` sí lo hace y por eso viven fuera de él:

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| `GET` | `/api/auditoria?limite=N` | `ADMIN`, `EMPLEADO` | Transacciones recientes del BUS |
| `GET` | `/api/auditoria/resumen` | `ADMIN`, `EMPLEADO` | Total, errores y desagregado por servicio |

## 2.5 Conexión con PostgreSQL: una base de datos por servicio

Cada módulo declara su propio `spring.datasource.url` en `application.properties`, con placeholders que permiten sobrescribir la conexión por variable de entorno (`DB_HOST`, `DB_PORT`, `DB_USER`, `DB_PASSWORD`) sin recompilar. El esquema lo administra **Flyway**, no Hibernate: `spring.jpa.hibernate.ddl-auto=validate` obliga a que las entidades coincidan con las migraciones, y un desajuste impide el arranque en lugar de corromper datos en silencio.

| Módulo | Base de datos | Migración inicial |
|---|---|---|
| `techretail-autenticacion` | `db_autenticacion` | `V1__create_tables.sql` (usuarios, roles, `usuario_rol`) |
| `techretail-producto` | `db_producto` | `V1__create_tables.sql` (categorías, productos) |
| `techretail-monitoreo` | `db_monitoreo` | `V1__create_tables.sql` (`monitoreo_logs`) |
| `techretail-gateway` | `db_esb` | `V1__create_tables.sql` (`auditoria_logs`) |

Las diez bases se crean desde `docker/postgres-init.sql` al levantar el entorno (`docker compose up -d postgres rabbitmq`); ese script solo se ejecuta en la primera creación del volumen. Para las pruebas automatizadas cada módulo usa **H2 en memoria con `MODE=PostgreSQL`**, manteniendo las migraciones Flyway reales: la suite no necesita Docker ni una base levantada, pero sí ejercita el SQL de producción.

El contenedor publica PostgreSQL en el puerto **5433** del host (`5433:5432`), no en el 5432: ese puerto lo ocupa una instalación nativa de PostgreSQL 18 en la máquina de desarrollo, y el proyecto se levanta de forma aislada sin detenerla. Todos los `application.properties` usan `${DB_PORT:5433}` como valor por defecto, de modo que `DB_PORT=5432` sigue siendo la única variable necesaria para apuntar a otra instancia. La conexión se verificó contra el contenedor real: `techretail-producto` arrancó con `/actuator/health` en `UP` y Flyway aplicó `V1` y `V2` sobre `db_producto`.

## 2.6 Observabilidad (Monitoreo, Auditoría, Logs y OpenTelemetry)

La observabilidad se apoya en cuatro piezas que responden preguntas distintas y no se sustituyen entre sí: **¿está vivo el servicio?** (sondas de Actuator), **¿dónde se consumió el tiempo de una petición?** (trazas distribuidas con OpenTelemetry), **¿qué servicios están caídos y desde cuándo?** (monitoreo activo con persistencia) y **¿quién hizo qué y con qué resultado?** (auditoría del BUS). Una traza explica una petición puntual, pero no deja constancia consultable de la operación; una fila de auditoría registra el resultado, pero no muestra en qué servicio se perdió el tiempo. La demostración usa las cuatro.

### 2.6.1 Sondas de salud con Actuator

Los cuatro módulos implementados declaran `spring-boot-starter-actuator`. La exposición se limita a `health` —autenticación, producto y el BUS declaran además `info`— y el detalle se oculta:

```properties
management.endpoints.web.exposure.include=health,info
management.endpoint.health.show-details=never
```

`show-details=never` es una decisión de seguridad, no un descuido: el endpoint queda público porque lo consume el sondeo periódico, y un cliente anónimo no debe ver componentes internos, versiones de dependencias ni el estado de la base de datos. La cadena de seguridad de los cuatro módulos permite `/actuator/health` e `/actuator/info` y mantiene el resto bajo token.

| Módulo | URL pública | Consumidor |
|---|---|---|
| `techretail-autenticacion` | `http://localhost:8088/actuator/health` | monitoreo |
| `techretail-producto` | `http://localhost:8082/actuator/health` | monitoreo |
| `techretail-gateway` | `http://localhost:8090/actuator/health` | monitoreo |
| `techretail-monitoreo` | `http://localhost:8089/actuator/health` | operación (no se autosondea) |

El contrato que consume el sondeo es estricto: solo un `200` cuyo cuerpo declare `"status":"UP"` cuenta como servicio arriba (§2.3). Por eso la sonda no depende de que el servicio tenga tráfico ni de que su base esté sana: si Flyway no aplica o la conexión JDBC falla, el health lo refleja y el monitor lo registra.

### 2.6.2 Trazas distribuidas con OpenTelemetry y Jaeger

Los cuatro módulos incorporan `spring-boot-starter-opentelemetry` (Spring Boot 4), que integra Micrometer Tracing con el exportador OTLP; no hay SDK inicializado a mano ni agente externo. La configuración es idéntica en los cuatro `application.properties`:

```properties
management.tracing.sampling.probability=${OTEL_SAMPLING_PROBABILITY:1.0}
management.opentelemetry.tracing.export.otlp.endpoint=${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4318/v1/traces}
management.opentelemetry.tracing.export.otlp.transport=http
management.logging.export.otlp.enabled=false
management.otlp.metrics.export.enabled=false
```

Tres decisiones merecen explicación:

- **El namespace es el de Boot 4** (`management.opentelemetry.tracing.export.otlp.*`). En Boot 3 la propiedad era `management.otlp.tracing.endpoint`; conservar la clave vieja no falla al arrancar, simplemente no exporta nada. El endpoint incluye la ruta `/v1/traces` porque el exportador HTTP no la agrega por sí solo cuando se sobrescribe la URL.
- **Muestreo al 100 %** (`1.0`) para que la demostración registre todas las peticiones, sobrescribible con `OTEL_SAMPLING_PROBABILITY`. En producción se bajaría para no almacenar trazas que nadie consulta.
- **Logs y métricas OTLP desactivados**: Jaeger all-in-one ingiere trazas, no logs ni métricas; exportarlos solo agregaría tráfico y errores de collector. La sección 2.6.5 explica qué se hace con los logs.

En `docker-compose.yml` se agregó `jaegertracing/all-in-one:1.62.0`, con la UI en `http://localhost:16686` y el colector OTLP en `4318` (HTTP) y `4317` (gRPC). El almacenamiento es en memoria: al reiniciar el contenedor se pierden las trazas, suficiente para la demostración pero no para producción.

La propagación es W3C `traceparent`: el BUS reenvía la cabecera al servicio destino, de modo que ambos spans pertenecen a la misma traza y la relación padre-hijo se conserva. Las siguientes trazas reales, capturadas el 08/11/2026, lo muestran:

| Traza | Servicios | Spans | Operación raíz | Resultado |
|---|---|---|---|---|
| `3f1965538e01548974d341321f94ccb8` | `techretail-gateway` + `techretail-autenticacion` | 11 | `POST /api/auth/login` | `200` |
| `c13d6a3fb34de090f1ad52f773d5bb59` | `techretail-gateway` + `techretail-producto` | 11 | `GET /api/productos` | `200` |

En la primera, el span raíz del BUS (`http post /api/auth/**`, 636 ms) contiene el span de cliente `http post` (623 ms), que a su vez es padre del span de servidor `http post /api/auth/login` (615 ms) en autenticación: **615 de los 636 ms se consumieron dentro del servicio destino**, y esa diferencia es exactamente la que una traza permite atribuir. Además de los spans HTTP aparecen `security filterchain before/after`, `authorize request` y `secured request`, así que el costo de la cadena de seguridad también es visible. La consulta `GET http://localhost:16686/api/services` devuelve los cuatro servicios registrados (`techretail-gateway`, `techretail-autenticacion`, `techretail-producto`, `techretail-monitoreo`), prueba de que los cuatro exportadores están activos.

### 2.6.3 Monitoreo activo y persistencia

El servicio de monitoreo ejecuta un sondeo cada `MONITOREO_INTERVALO_MS` (30 s por defecto) con `@Scheduled(fixedDelayString=...)`: el siguiente ciclo empieza cuando termina el anterior, así que una base lenta o un timeout no acumula ejecuciones. Sondea los nueve servicios restantes —no a sí mismo— con un timeout de `MONITOREO_TIMEOUT_MS` (2 s) y persiste cada resultado en `monitoreo_logs` (`nombre_servicio`, `estado`, `tiempo_respuesta_ms`, `fecha_consulta`). Los endpoints de consulta son los de §2.3 y exigen `ADMIN` o `EMPLEADO`; el servicio revalida el JWT por su cuenta. En el perfil de pruebas el planificador está desactivado (`techretail.monitoreo.scheduler.enabled=false`) y las pruebas lo controlan explícitamente para que la suite sea determinista.

Con eso, la pregunta «¿qué servicios están caídos y desde cuándo?» se responde con SQL directo:

```sql
SELECT nombre_servicio, estado, tiempo_respuesta_ms, fecha_consulta
FROM monitoreo_logs
ORDER BY fecha_consulta DESC
LIMIT 10;
```

Los seis servicios de andamiaje no declaran Actuator, por lo que el sondeo los registrará `DOWN` hasta que se implementen: el monitor refleja el alcance real y no lo maquilla.

### 2.6.4 Auditoría de transacciones en el BUS

El BUS audita cada transacción en `db_esb.auditoria_logs` sin bloquear la respuesta. `AuditFilter` se ejecuta alrededor de la cadena, excluye `/actuator` —para que los sondeos de salud no inunden la tabla— y registra método, endpoint, servicio destino, estado, usuario y un detalle con `rol`, `duracion_ms`, remitente y, si hubo rechazo, el motivo. Los campos se truncan antes de persistir (endpoint 200 caracteres, usuario 100, detalle 500) para que una petición maliciosa no infle la tabla.

La escritura es asíncrona: `AuditPublisher` entrega la entrada a un ejecutor acotado (`auditExecutor`: 2 hilos base, máximo 4, cola de 1000, `AbortPolicy`, cierre ordenado con 5 s de gracia). Si la cola se satura, el evento se descarta con un `WARN`; si la escritura en `db_esb` falla, se registra un `WARN` y la respuesta al cliente no se altera. Si la cadena muere antes de fijar el estado, la fila se registra como `500` y la excepción se relanza (lección 3 de §5).

Las filas siguientes son reales, leídas de `db_esb` tras la demostración:

| `id` | Servicio destino | Operación | Estado | Usuario |
|---|---|---|---|---|
| 6 | `techretail-autenticacion` | `POST /api/auth/login` | `200` | `anonimo` |
| 5 | `techretail-autenticacion` | `POST /api/auth/login` | `401` | `anonimo` |
| 4 | `techretail-producto` | `GET /api/productos` | `200` | `anonimo` |

El `401` también se audita: los intentos rechazados son parte de la trazabilidad de seguridad, no ruido. `usuario=anonimo` aparece en las peticiones públicas; en las autenticadas se registra el sujeto del token. La consulta se expone por los endpoints locales `/api/auditoria` y `/api/auditoria/resumen` para `ADMIN`/`EMPLEADO` (§2.4). Como la escritura es asíncrona, una lectura inmediatamente posterior a la petición puede no ver la fila todavía; las pruebas de auditoría esperan explícitamente ese registro.

Queda una mejora identificada y no implementada: incluir el `traceId` en `detalle` para correlacionar una fila de auditoría con su traza en Jaeger. Hoy la correlación se hace por endpoint y hora.

### 2.6.5 Logs

Los cuatro módulos usan SLF4J/Logback con el formato de consola por defecto de Spring Boot y nivel `INFO`. No hay agregación centralizada (Loki/ELK) ni exportación OTLP de logs: `management.logging.export.otlp.enabled=false` es deliberado, porque el collector configurado es Jaeger y solo ingiere trazas. El registro consultable y persistente son las tablas `auditoria_logs` y `monitoreo_logs`, no los archivos de log.

Los logs se reservan para diagnóstico operativo: arranque, saturación de la cola de auditoría y fallos al escribir auditoría, ambos con `WARN` y sin propagarse al cliente. Es una limitación declarada: en producción correspondería logging estructurado en JSON con `traceId`/`spanId` y retención centralizada. Hoy el `traceId` se consulta en Jaeger, no en el log.

### 2.6.6 Cómo reproducir la demostración

```powershell
# 1. Infraestructura: PostgreSQL en 5433, RabbitMQ y Jaeger
docker compose up -d postgres rabbitmq jaeger

# 2. Los cuatro servicios, cada uno en su propia terminal
cd techretail-autenticacion; .\mvnw.cmd spring-boot:run
cd techretail-producto;     .\mvnw.cmd spring-boot:run
cd techretail-monitoreo;    .\mvnw.cmd spring-boot:run
cd techretail-gateway;      .\mvnw.cmd spring-boot:run
```

```powershell
# 3. Login a través del BUS y consulta del catálogo
$login = Invoke-RestMethod -Method Post http://localhost:8090/api/auth/login `
    -ContentType 'application/json' `
    -Body '{"usuario":"admin@techretail.pe","password":"Admin123!"}'

Invoke-RestMethod http://localhost:8090/api/productos

# 4. Auditoría de esas transacciones (requiere el token ADMIN)
Invoke-RestMethod "http://localhost:8090/api/auditoria?limite=5" `
    -Headers @{ Authorization = "Bearer $($login.token)" }
```

La verificación visual de las trazas se hace en `http://localhost:16686`: se elige el servicio `techretail-gateway`, se busca la operación `POST /api/auth/**` y se abre la traza de 11 spans, que incluye el span del servicio de autenticación como hijo del span de cliente del BUS. La salud de cualquier servicio se comprueba con `Invoke-RestMethod http://localhost:8082/actuator/health`; la auditoría persistida, con `docker exec techretail-postgres psql -U techretail -d db_esb -c "SELECT * FROM auditoria_logs ORDER BY id DESC LIMIT 10;"`.

---

# 3. Pruebas de Servicios

## 3.1 Inventario de pruebas automatizadas

Los cuatro módulos implementados suman **115 pruebas** en `src/test`, contadas por método anotado con `@Test` o `@ParameterizedTest`, y las 115 se ejecutaron en verde:

| Módulo | Pruebas | Qué cubren |
|---|---|---|
| `techretail-autenticacion` | 26 | Registro, login, emisión y validación de JWT, rechazo de `exp` ausente, normalización de usuarios, validación de contraseñas |
| `techretail-producto` | 37 | Contrato REST del catálogo, reglas de rol, validación de entrada, filtros de búsqueda, `404`, error dispatch sobre Tomcat real |
| `techretail-gateway` | 27 | Enrutamiento, rechazo de tokens inválidos antes del destino, política de roles por ruta, auditoría asíncrona, cabeceras de seguridad, error dispatch sobre Tomcat real |
| `techretail-monitoreo` | 25 | Sonda HTTP (`UP`/`DOWN`/timeout), persistencia del historial, límites del parámetro `limite`, seguridad propia, error dispatch sobre Tomcat real |

Los seis servicios que aún son andamiaje aportan 2 pruebas de contexto cada uno (12 en total), ejecutadas también el 08/11/2026 con `BUILD SUCCESS`: el **repositorio completo queda con 127 pruebas en verde**.

Ejecución del **08/11/2026** con `.\mvnw.cmd clean test` desde cada módulo (perfil `test`: H2 en memoria con `MODE=PostgreSQL` y migraciones Flyway reales):

| Módulo | `Tests run` | Failures | Errors | Skipped | `Total time` | Resultado |
|---|---|---|---|---|---|---|
| `techretail-autenticacion` | 26 | 0 | 0 | 0 | 56.771 s | `BUILD SUCCESS` |
| `techretail-producto` | 37 | 0 | 0 | 0 | 46.417 s | `BUILD SUCCESS` |
| `techretail-gateway` | 27 | 0 | 0 | 0 | 32.022 s | `BUILD SUCCESS` |
| `techretail-monitoreo` | 25 | 0 | 0 | 0 | 40.079 s | `BUILD SUCCESS` |
| **Total** | **115** | **0** | **0** | **0** | | **4/4 en verde** |

## 3.2 Pruebas manuales con Postman

Las pruebas se ejecutaron el **08/11/2026** contra el BUS en `http://localhost:8090`, con PostgreSQL en el puerto 5433 y los cuatro servicios levantados. Cada caso conserva la **petición exacta** y la **respuesta exacta** en bloques `http` y `json`, reproducibles pegándolos en Postman o importando la colección `docs/postman_collection/techretail-avance3.json`, y los casos principales se acompañan de la captura de pantalla de Postman con la petición y la respuesta.

> **[COMPLETAR: capturas de Postman]** Guardar en `docs/capturas/postman/` una captura por caso (petición y respuesta visibles) e insertarla debajo del bloque JSON correspondiente: `P1-login-admin.png`, `P4-catalogo-publico.png`, `P8-escritura-cliente-403.png`, `P9-alta-producto.png`, `P10-actualizacion-producto.png`, `P17-token-alterado-401.png`.

Todos los casos se ejecutan contra el BUS, salvo P18, que se incluye a propósito para demostrar la segunda capa de seguridad (el acceso directo al servicio destino).

### 3.2.1 Entorno y preparación

| Variable de Postman | Valor | Uso |
|---|---|---|
| `baseUrl` | `http://localhost:8090` | BUS / Gateway |
| `adminToken` | `token` devuelto por P1 | Escrituras y consultas de monitoreo/auditoría |
| `clienteToken` | `token` devuelto por el login de `cliente@techretail.pe` | Caso P8 |

Los usuarios de demostración los aprovisiona `DemoUsuarioSeeder` al arrancar autenticación (`admin@techretail.pe` / `Admin123!`, `empleado@techretail.pe` / `Empleado123!`, `cliente@techretail.pe` / `Cliente123!`). El catálogo **no tiene seed de productos**: la migración `V2__seed_categorias.sql` solo carga las cinco categorías, y los productos de esta evidencia se crearon con el propio endpoint `POST /api/productos`.

### 3.2.2 Resumen de la ejecución

| # | Caso | Método y ruta | Token | Esperado | Observado | Veredicto |
|---|---|---|---|---|---|---|
| P1 | Login ADMIN | `POST /api/auth/login` | — | `200` + `token`, `rol: ADMIN` | `200` | OK |
| P2 | Login credenciales inválidas | `POST /api/auth/login` | — | `401` JSON sin hash ni stack trace | `401` | OK |
| P3 | Registro de cliente | `POST /api/auth/registro` | — | `201` con `rol: CLIENTE` | `201` | OK |
| P4 | Catálogo público | `GET /api/productos` | — | `200` con la lista | `200` (3 productos) | OK |
| P5 | Catálogo filtrado | `GET /api/productos?q=monitor&soloDisponibles=true` | — | `200` con subconjunto | `200` (1 producto) | OK |
| P6 | Detalle inexistente | `GET /api/productos/999999` | — | `404` JSON | `404` | OK |
| P7 | Escritura sin token | `POST /api/productos` | — | `401` | `401` | OK |
| P8 | Escritura con rol `CLIENTE` | `POST /api/productos` | CLIENTE | `403` | `403` | OK |
| P9 | Crear producto | `POST /api/productos` | ADMIN | `201` + cabecera `Location` | `201` | OK |
| P10 | Actualizar producto | `PUT /api/productos/4` | ADMIN | `200` con los datos nuevos | `200` | OK |
| P11 | Retiro lógico | `PUT /api/productos/4` con `"disponible": false` | ADMIN | `200`; deja de aparecer con `soloDisponibles=true` | `200` y el producto sale del filtro | OK |
| P12 | Validación de entrada | `POST /api/productos` con `precio: -5` | ADMIN | `400` con detalle de validación | `400` | OK |
| P13 | Estado de servicios | `GET /api/monitoreo/estado` | ADMIN | `200` con el último estado por servicio | `200` (9 servicios) | OK |
| P14 | Historial con límite inválido | `GET /api/monitoreo/historial?limite=0` | ADMIN | `400` | `400` | OK |
| P15 | Auditoría del BUS | `GET /api/auditoria?limite=5` | ADMIN | `200` con las transacciones | `200` | OK |
| P16 | Resumen de auditoría | `GET /api/auditoria/resumen` | ADMIN | `200` con total, errores y desglose | `200` | OK |
| P17 | Token alterado | `GET /api/productos/1` con firma modificada | alterado | `401`; el destino no recibe la petición | `401` | OK |
| P18 | Acceso directo al servicio | `GET`/`POST http://localhost:8082/api/productos` | — | `200` lectura pública; `401` en escritura | `200` / `401` | OK |

### 3.2.3 P2 — Login con credenciales inválidas

```http
POST http://localhost:8090/api/auth/login
Content-Type: application/json

{"usuario":"admin@techretail.pe","password":"incorrecta"}
```

```json
HTTP/1.1 401 Unauthorized

{"status":401,"error":"Unauthorized","message":"Credenciales invalidas"}
```

La respuesta no revela si el usuario existe ni incluye el hash almacenado: el mensaje es idéntico para usuario inexistente y contraseña incorrecta.

### 3.2.4 P1 — Login ADMIN

```http
POST http://localhost:8090/api/auth/login
Content-Type: application/json

{"usuario":"admin@techretail.pe","password":"Admin123!"}
```

```json
HTTP/1.1 200 OK

{"token":"eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbkB0ZWNocmV0YWlsLnBlIiwicm9sIjoiQURNSU4iLCJpc3MiOiJ0ZWNocmV0YWlsLWF1dGgiLCJpYXQiOjE3OTQxNjY0MjMsImV4cCI6MTc5NDE3MDAyMywiaWRVc3VhcmlvIjoxfQ.2p7pB9JPHKqnau4Rg7wf1wtNiFRp6t9RNXhZZD6ZTCo","tipo":"Bearer","expiraEn":3600,"rol":"ADMIN","idUsuario":1,"nombre":"Administrador TechRetail"}
```

El token es un JWT HS256 con `sub`, `rol`, `iss`, `iat`, `exp` e `idUsuario`. `expiraEn` está en segundos (3600 = 1 hora). En Postman conviene guardarlo en una variable de colección con un script de test: `pm.collectionVariables.set("adminToken", pm.response.json().token)`.

### 3.2.5 P4 — Catálogo público (GET)

```http
GET http://localhost:8090/api/productos
```

```json
HTTP/1.1 200 OK

[{"id":1,"nombre":"Laptop Lenovo ThinkPad E14 Gen 6","descripcion":"14 pulgadas, Ryzen 7, 16 GB RAM, 512 GB SSD","precio":3499.90,"idCategoria":1,"categoria":"Laptops","stockReferencial":15,"disponible":true,"fechaCreacion":"2026-11-08T14:18:45.218125"},{"id":2,"nombre":"Mouse Logitech MX Master 3S","descripcion":"Inalambrico, 8000 DPI, silencioso","precio":349.90,"idCategoria":3,"categoria":"Periféricos","stockReferencial":40,"disponible":true,"fechaCreacion":"2026-11-08T14:18:45.439692"},{"id":3,"nombre":"Monitor Dell UltraSharp U2724D","descripcion":"27 pulgadas QHD, IPS Black, 120 Hz","precio":1899.00,"idCategoria":3,"categoria":"Periféricos","stockReferencial":8,"disponible":true,"fechaCreacion":"2026-11-08T14:19:29.979783"}]
```

La lectura es pública: no requiere token. Los datos provienen de `db_producto` (tablas `productos` y `categorias`), y el nombre de la categoría se resuelve por la relación JPA, no por una consulta cruzada a otro servicio.

### 3.2.6 P9 — Alta de producto (POST)

```http
POST http://localhost:8090/api/productos
Authorization: Bearer {{adminToken}}
Content-Type: application/json

{"nombre":"Teclado Mecanico Keychron K8 Pro","descripcion":"TKL, switches Gateron Brown, inalambrico","precio":549.90,"idCategoria":3,"stockReferencial":25}
```

```json
HTTP/1.1 201 Created
Location: http://localhost:8082/api/productos/4

{"id":4,"nombre":"Teclado Mecanico Keychron K8 Pro","descripcion":"TKL, switches Gateron Brown, inalambrico","precio":549.90,"idCategoria":3,"categoria":"Periféricos","stockReferencial":25,"disponible":true,"fechaCreacion":"2026-11-08T14:33:47.1678946"}
```

`disponible` se omite en la petición y el servicio lo interpreta como `true`. La cabecera `Location` la construye el servicio de producto con su propia URL (`8082`), porque el BUS no reescribe el host original; el recurso es accesible igualmente como `http://localhost:8090/api/productos/4`.

### 3.2.7 P10 y P11 — Actualización y retiro lógico (PUT)

Actualización de precio y stock:

```http
PUT http://localhost:8090/api/productos/4
Authorization: Bearer {{adminToken}}
Content-Type: application/json

{"nombre":"Teclado Mecanico Keychron K8 Pro","descripcion":"TKL, switches Gateron Brown, inalambrico","precio":499.90,"idCategoria":3,"stockReferencial":20}
```

```json
HTTP/1.1 200 OK

{"id":4,"nombre":"Teclado Mecanico Keychron K8 Pro","descripcion":"TKL, switches Gateron Brown, inalambrico","precio":499.90,"idCategoria":3,"categoria":"Periféricos","stockReferencial":20,"disponible":true,"fechaCreacion":"2026-11-08T14:33:47.167895"}
```

Retiro lógico: mismo `PUT` con `"disponible": false`.

```json
HTTP/1.1 200 OK

{"id":4,"nombre":"Teclado Mecanico Keychron K8 Pro","descripcion":"TKL, switches Gateron Brown, inalambrico","precio":499.90,"idCategoria":3,"categoria":"Periféricos","stockReferencial":20,"disponible":false,"fechaCreacion":"2026-11-08T14:33:47.167895"}
```

Después del retiro, `GET /api/productos?soloDisponibles=true` devuelve los tres productos que siguen disponibles: el `id 4` ya no aparece. El registro no se borra de la base, que es lo que exige la trazabilidad del negocio.

### 3.2.8 P13 — Estado de monitoreo (GET)

```http
GET http://localhost:8090/api/monitoreo/estado
Authorization: Bearer {{adminToken}}
```

```json
HTTP/1.1 200 OK

[{"id":152,"servicio":"techretail-autenticacion","estado":"UP","tiempoRespuestaMs":393,"fechaConsulta":"2026-11-08T14:33:23.365465"},{"id":145,"servicio":"techretail-cliente","estado":"DOWN","tiempoRespuestaMs":76,"fechaConsulta":"2026-11-08T14:33:22.522042"},{"id":151,"servicio":"techretail-facturacion-sunat","estado":"DOWN","tiempoRespuestaMs":2,"fechaConsulta":"2026-11-08T14:33:22.968467"},{"id":153,"servicio":"techretail-gateway","estado":"UP","tiempoRespuestaMs":455,"fechaConsulta":"2026-11-08T14:33:23.837093"},{"id":149,"servicio":"techretail-inventario","estado":"DOWN","tiempoRespuestaMs":2,"fechaConsulta":"2026-11-08T14:33:22.957605"},{"id":150,"servicio":"techretail-notificacion","estado":"DOWN","tiempoRespuestaMs":1,"fechaConsulta":"2026-11-08T14:33:22.962849"},{"id":148,"servicio":"techretail-pago","estado":"DOWN","tiempoRespuestaMs":2,"fechaConsulta":"2026-11-08T14:33:22.951946"},{"id":147,"servicio":"techretail-pedido","estado":"DOWN","tiempoRespuestaMs":2,"fechaConsulta":"2026-11-08T14:33:22.945387"},{"id":146,"servicio":"techretail-producto","estado":"UP","tiempoRespuestaMs":401,"fechaConsulta":"2026-11-08T14:33:22.928916"}]
```

El resultado es coherente con el entorno: los tres servicios implementados que estaban levantados (`autenticacion`, `producto`, `gateway`) aparecen `UP`; los seis módulos de andamiaje, `DOWN` porque no estaban corriendo. La consulta la sirve el servicio de monitoreo desde `db_monitoreo`, y el BUS la enruta exigiendo rol `ADMIN` o `EMPLEADO`.

### 3.2.9 P15 — Auditoría del BUS (GET)

```http
GET http://localhost:8090/api/auditoria?limite=5
Authorization: Bearer {{adminToken}}
```

```json
HTTP/1.1 200 OK

[{"id":69,"servicioOrigen":"GATEWAY","servicioDestino":"techretail-producto","operacion":"GET","endpoint":"/api/productos/1","estadoRespuesta":401,"usuario":"anonimo","timestamp":"2026-11-08T14:33:48.206841"},{"id":68,"servicioOrigen":"GATEWAY","servicioDestino":"desconocido","operacion":"GET","endpoint":"/api/auditoria/resumen","estadoRespuesta":200,"usuario":"admin@techretail.pe","timestamp":"2026-11-08T14:33:48.177989"},{"id":67,"servicioOrigen":"GATEWAY","servicioDestino":"desconocido","operacion":"GET","endpoint":"/api/auditoria","estadoRespuesta":200,"usuario":"admin@techretail.pe","timestamp":"2026-11-08T14:33:48.126635"},{"id":66,"servicioOrigen":"GATEWAY","servicioDestino":"techretail-monitoreo","operacion":"GET","endpoint":"/api/monitoreo/historial","estadoRespuesta":400,"usuario":"admin@techretail.pe","timestamp":"2026-11-08T14:33:48.085316"},{"id":65,"servicioOrigen":"GATEWAY","servicioDestino":"techretail-monitoreo","operacion":"GET","endpoint":"/api/monitoreo/historial","estadoRespuesta":200,"usuario":"admin@techretail.pe","timestamp":"2026-11-08T14:33:48.02162"}]
```

Cada petición que atraviesa el BUS deja una fila en `db_esb.auditoria_logs`, incluidos los rechazos: el `401` de P17 quedó registrado con `usuario: "anonimo"` y el `400` de P14 con el estado real, no con el `200` por defecto. Las rutas locales del BUS (`/api/auditoria/**`) se auditan con `servicioDestino: "desconocido"` porque no se enrutan a ningún servicio.

### 3.2.10 Controles de seguridad y validación

Los casos siguientes se ejecutaron con el mismo token ADMIN salvo P8, que usa el de `cliente@techretail.pe`.

Escritura sin token (P7) y con token alterado (P17):

```json
HTTP/1.1 401 Unauthorized

{"status":401,"error":"Unauthorized","message":"No autenticado: se requiere un token JWT valido"}
```

```json
HTTP/1.1 401 Unauthorized

{"status":401,"error":"Unauthorized","message":"token invalido o expirado"}
```

El primero lo emite el BUS al no encontrar credenciales; el segundo, al validar la firma del token manipulado. En ambos casos el servicio destino no recibe la petición.

Escritura con rol `CLIENTE` (P8):

```json
HTTP/1.1 403 Forbidden

{"status":403,"error":"Forbidden","message":"Acceso denegado: el rol del token no autoriza esta operacion"}
```

Validación de entrada (P12) y límite fuera de rango (P14):

```json
HTTP/1.1 400 Bad Request

{"status":400,"error":"Bad Request","message":"precio: debe ser mayor que o igual a 0.01","timestamp":"2026-11-08T14:33:47.5995474"}
```

```json
HTTP/1.1 400 Bad Request

{"timestamp":"2026-11-08T19:33:48.064Z","status":400,"error":"Bad Request","path":"/api/monitoreo/historial"}
```

El `400` de P14 es el que motivó una corrección durante esta misma sesión de pruebas: el error dispatch interno de Tomcat (`/error`) quedaba detrás de la cadena de seguridad y convertía cualquier `400` o `404` en `401`. Se corrigió en `SecurityConfig` de gateway, monitoreo y producto, y se agregó una prueba de regresión sobre Tomcat real, porque la prueba equivalente con MockMvc pasaba en verde sin reproducir el problema (ver §7, lección 4).

### 3.2.11 Sobre DELETE

El catálogo no expone `DELETE` por diseño; la baja es lógica con `disponible=false` (caso P11). Se documenta explícitamente para que la rúbrica no lo lea como un faltante: un producto retirado debe seguir existiendo para que la auditoría y el historial de ventas conserven sentido.

## 3.3 Cobertura de la rúbrica del Avance 3

| Requisito de la rúbrica | Estado | Dónde se evidencia |
|---|---|---|
| Carátula y resumen ejecutivo | Cumplido | Carátula y §1 |
| Servicios implementados (mínimo 2): lenguaje/framework y código documentado | Cumplido: cuatro servicios en Java 21 / Spring Boot 4.1.1 | §2 (stack técnico al inicio; código comentado en §2.1 a §2.3) |
| Pruebas de servicios: Postman con petición/respuesta y JSON real | Cumplido; capturas por anexar | §3.2 (18 casos con JSON real), colección `docs/postman_collection/techretail-avance3.json`; capturas en `docs/capturas/postman/` |
| Orquestación parcial: cómo un servicio invoca a otro y diagrama de integración | Cumplido | §4 (dos flujos reales, diagrama de componentes y diagrama de secuencia) |
| Registro de servicios: publicación UDDI/REST | Cumplido: publicación REST mediante la tabla de rutas del BUS y el contrato OpenAPI; UDDI como evolución | §5 |
| Lecciones aprendidas / dificultades encontradas | Cumplido | §7 |
| Informe del escáner de vulnerabilidades OWASP ZAP | Cumplido: 0 alertas altas, 0 medias, 2 bajas y 2 informativas | §6 e informes en `docs/zap/reports/zap-report.{pdf,html,md,json}` |

**Fuera del alcance de este avance, planificado para el entregable final:** ClienteService, mensajería asíncrona con RabbitMQ, FacturacionSunatService SOAP contract-first y un catálogo UDDI funcional. Sus módulos existen como andamiaje (configuración, base de datos y migración inicial). La ausencia de `DELETE` en el catálogo es una decisión de diseño, no un faltante (§3.2.11).

---

# 4. Orquestación Parcial

## 4.1 Enfoque: orquestación mediada por el BUS

En este avance la composición de servicios ocurre **a través del BUS**. Ningún servicio de negocio llama directamente a otro servicio de negocio: el cliente invoca un único punto de entrada (`:8090`) y el BUS decide —por ruta y por rol— a qué servicio delega, reenvía la petición y registra el resultado. Es el patrón de mediación de un ESB: el BUS controla el flujo (autenticación previa, autorización, enrutamiento y auditoría) y los servicios permanecen desacoplados entre sí, porque ninguno conoce la URL de otro.

Se documentan dos flujos que ya existen en el código y están cubiertos por pruebas:

1. **BUS → Autenticación → Producto**: una operación de negocio protegida (alta de producto) que exige un token emitido por un servicio y consumido por otro.
2. **Monitoreo → resto de servicios**: invocación periódica servicio a servicio para verificar la salud del sistema.

**Alcance declarado:** la orquestación de negocio entre servicios de dominio —por ejemplo, Pedido consultando a Producto e Inventario mediante OpenFeign, dependencia ya declarada en `techretail-pedido`— corresponde al entregable final; hoy Pedido es andamiaje.

## 4.2 Diagrama de integración

![Diagrama de integración de TechRetail SOA](diagramas/orquestacion-componentes.png)

*Figura 1. Diagrama de componentes de la integración actual. Las líneas sólidas son invocaciones síncronas en la ruta de la petición; las punteadas, invocaciones asíncronas o periódicas (auditoría, sondeo de salud y exportación de trazas). Fuente editable: `docs/diagramas/orquestacion-componentes.mmd`.*

Toda petición externa atraviesa dos filtros del BUS en este orden: `AuditFilter`, que envuelve la cadena para registrar también los rechazos, y `JwtAuthenticationFilter`, que valida el token antes de enrutar. Solo entonces la tabla de rutas de Spring Cloud Gateway WebMVC entrega la petición al servicio destino, o la atiende localmente si es `/api/auditoria/**`.

## 4.3 Flujo 1 — Alta de producto orquestada por el BUS

![Diagrama de secuencia: login y alta de producto a través del BUS](diagramas/orquestacion-secuencia.png)

*Figura 2. Secuencia del login y del alta de un producto a través del BUS, con la rama de rechazo. Fuente editable: `docs/diagramas/orquestacion-secuencia.mmd`.*

Los números corresponden a los pasos del diagrama:

- **Pasos 1 a 6 — obtención del token.** `POST /api/auth/login` es una ruta pública. El BUS la reenvía a Autenticación (ruta `autenticacion`, `Path=/api/auth/**`), que verifica la contraseña con BCrypt y firma un JWT HS256 con `sub`, `rol`, `iss`, `iat`, `exp` e `idUsuario`. El BUS entrega la fila de auditoría a su ejecutor asíncrono y devuelve el token sin esperar la escritura (caso P1).
- **Pasos 7 y 8 — autorización centralizada.** Con el token en `Authorization: Bearer`, el BUS valida firma, expiración y rol `ADMIN` antes de enrutar (§2.4).
- **Pasos 9 y 10 — rechazo temprano.** Sin token, con token alterado o con rol insuficiente, el BUS responde `401` o `403` en JSON y audita el rechazo; **Producto nunca recibe la petición** (casos P7, P8 y P17).
- **Pasos 11 a 16 — delegación.** El BUS reenvía la petición a Producto conservando `Authorization` y la cabecera W3C `traceparent`. Producto revalida el token (segunda capa, §1), aplica Bean Validation, persiste en `db_producto` y responde `201` con `Location`; el BUS audita el `201` y devuelve la respuesta tal como la recibe (caso P9).

La relación entre Autenticación y Producto merece una aclaración: **Producto no llama a Autenticación en cada petición**. Confía en la firma HS256 del token, verificada con el secreto compartido `JWT_SECRET`. El acoplamiento es de contrato (formato y firma del token), no de ejecución: una caída de Autenticación impide emitir tokens nuevos, pero no bloquea las operaciones con tokens vigentes (una hora).

**Evidencia de la invocación entre servicios.** Las trazas de Jaeger de §2.6.2 lo demuestran: la traza `3f1965538e01548974d341321f94ccb8` reúne spans de `techretail-gateway` y `techretail-autenticacion` en una sola petición, y la traza `c13d6a3fb34de090f1ad52f773d5bb59` hace lo mismo con `techretail-producto`, con relación padre-hijo entre el span del BUS y el del servicio destino. La auditoría del BUS (caso P15) registra cada delegación con su `servicioDestino` y su estado real.

> **[COMPLETAR: captura de Jaeger]** Insertar aquí la captura de la vista de traza de Jaeger (`http://localhost:16686`) de un `POST /api/productos` atravesando `techretail-gateway` y `techretail-producto`, guardada como `docs/capturas/jaeger-traza-gateway-producto.png`.

## 4.4 Flujo 2 — Monitoreo invoca a los demás servicios

Monitoreo es el único servicio que inicia por su cuenta llamadas a otros servicios. `HttpHealthProbe` usa `java.net.http.HttpClient` para enviar `GET /actuator/health` a cada uno de los nueve servicios declarados en `techretail.monitoreo.servicios[*]`, cada 30 s (`@Scheduled` con `fixedDelay`) y con un timeout de 2 s. Un servicio se considera `UP` solo si el cuerpo declara `"status":"UP"` (§2.3). Cada resultado se persiste en `db_monitoreo.monitoreo_logs`.

Las sondas van **directo a cada servicio, no a través del BUS**: el objetivo es medir la salud de cada instancia, y pasar por el BUS mediría al BUS. En cambio, la **consulta** del estado sí es una operación de negocio y pasa por el BUS con rol `ADMIN` o `EMPLEADO` (`GET /api/monitoreo/estado`, caso P13). Así, el flujo encadena Cliente → BUS → Monitoreo para mostrar el resultado de Monitoreo → servicios: en la evidencia de P13, los tres servicios levantados aparecen `UP` y los seis de andamiaje, `DOWN`.

## 4.5 Resumen de invocaciones entre componentes

| Origen | Destino | Mecanismo | Modo | Evidencia |
|---|---|---|---|---|
| Cliente | BUS `:8090` | HTTP REST + Bearer JWT | Síncrono | §3.2 (P1 a P17) |
| BUS | Autenticación `:8088` | Proxy HTTP, ruta `/api/auth/**` | Síncrono | P1 a P3; traza `3f19…` |
| BUS | Producto `:8082` | Proxy HTTP, ruta `/api/productos/**` | Síncrono | P4 a P12 y P17; traza `c13d…` |
| BUS | Monitoreo `:8089` | Proxy HTTP, ruta `/api/monitoreo/**` | Síncrono | P13, P14 |
| BUS | `db_esb` | `JdbcTemplate` en ejecutor acotado | Asíncrono | P15, P16 |
| Monitoreo | Nueve servicios | `HttpClient`, `GET /actuator/health` | Periódico (30 s), timeout 2 s | P13 |
| Autenticación | BUS, Producto, Monitoreo | Contrato JWT HS256 con secreto compartido, sin llamada | Sin acoplamiento en ejecución | P17, P18 |
| Los cuatro servicios | Jaeger `:4318` | Exportación OTLP sobre HTTP | Asíncrono | §2.6.2 |

---

# 5. Registro de Servicios (Publicación REST y Catálogo UDDI)

## 5.1 Publicación actual: registro estático en el BUS

Hoy los servicios se publican como **recursos REST detrás del BUS**. El registro es estático y declarativo: la tabla de rutas del gateway asocia cada prefijo lógico con la URL física del servicio, y cubre de forma simplificada las tres operaciones de UDDI:

- **Publish:** un servicio se publica al declarar su ruta en `application.properties` del gateway. La URL física se inyecta por variable de entorno (`SERVICIO_<NOMBRE>_URL`), de modo que mover un servicio de host no exige recompilar.
- **Find:** el consumidor solo necesita conocer el BUS (`http://localhost:8090`) y el contrato publicado en `docs/zap/openapi-gateway.yaml` (OpenAPI 3.0, 12 operaciones), que es el equivalente REST del WSDL de un servicio SOAP.
- **Bind:** en cada petición el BUS resuelve el prefijo a la URL física y reenvía la llamada (Spring Cloud Gateway Server WebMVC).

Fragmento real de la tabla de rutas (`techretail-gateway/src/main/resources/application.properties`):

```properties
# Nombre lógico -> prefijo publicado -> URL física (sobrescribible por entorno)
spring.cloud.gateway.server.webmvc.routes[1].id=productos
spring.cloud.gateway.server.webmvc.routes[1].uri=${SERVICIO_PRODUCTOS_URL:http://localhost:8082}
spring.cloud.gateway.server.webmvc.routes[1].predicates[0]=Path=/api/productos/**
spring.cloud.gateway.server.webmvc.routes[7].id=autenticacion
spring.cloud.gateway.server.webmvc.routes[7].uri=${SERVICIO_AUTENTICACION_URL:http://localhost:8088}
spring.cloud.gateway.server.webmvc.routes[7].predicates[0]=Path=/api/auth/**
```

Servicios publicados en el BUS:

| Ruta (`id`) | Prefijo publicado | Destino por defecto | Acceso exigido por el BUS | Estado |
|---|---|---|---|---|
| `clientes` | `/api/clientes/**` | `:8081` | Autenticado | Andamiaje |
| `productos` | `/api/productos/**` | `:8082` | `GET` público; escritura `ADMIN` | **Implementado** |
| `pedidos` | `/api/pedidos/**` | `:8083` | Autenticado | Andamiaje |
| `pagos` | `/api/pagos/**` | `:8084` | `ADMIN`, `CLIENTE` | Andamiaje |
| `inventario` | `/api/inventario/**` | `:8085` | `ADMIN`, `EMPLEADO` | Andamiaje |
| `notificaciones` | `/api/notificaciones/**` | `:8086` | Autenticado | Andamiaje |
| `facturacion`, `facturacion-endpoint` | `/ws/facturacion`, `/ws/facturacion/**` | `:8087` | `ADMIN`, `EMPLEADO` | Andamiaje (SOAP) |
| `facturacion-wsdl` | `/ws/facturacion.wsdl` | `:8087` | Autenticado | Andamiaje (SOAP) |
| `autenticacion` | `/api/auth/**` | `:8088` | `POST` público | **Implementado** |
| `monitoreo` | `/api/monitoreo/**` | `:8089` | `ADMIN`, `EMPLEADO` | **Implementado** |
| *(local)* | `/api/auditoria/**` | BUS (`db_esb`) | `ADMIN`, `EMPLEADO` | **Implementado** |

Existe un segundo registro, orientado a la salud: la lista `techretail.monitoreo.servicios[*]` del servicio de monitoreo, con URLs sobrescribibles por `SERVICIO_<NOMBRE>_URL`, define a quién se sondea (§4.4).

**Limitación conocida:** son dos listas mantenidas a mano. No hay autorregistro, ni versionado de servicios, ni alta de instancias sin reiniciar el BUS. Esa limitación motiva la evolución hacia un catálogo.

## 5.2 Evolución prevista: catálogo UDDI

El catálogo de servicios se mantiene como **propuesta de diseño**, no como funcionalidad implementada. La decisión es explícita: se priorizó que los servicios existentes funcionen y estén auditados antes de añadir un componente de descubrimiento que nadie consumiría todavía. Con el catálogo, la tabla de rutas del BUS y la lista de monitoreo se derivarían de una única fuente de verdad.

El diseño previsto, coherente con el Avance 2 §5.3, es el siguiente:

| Operación UDDI | Endpoint propuesto | Acceso previsto | Comportamiento |
|---|---|---|---|
| **Publish** | `POST /api/registro/servicios` | Autenticado (mecanismo por definir) | Cada servicio se registra al arrancar con nombre, URL, versión y, si aplica, URL del WSDL |
| **Find** | `GET /api/registro/servicios` | Público | Devuelve el catálogo de servicios disponibles |
| **Bind** | Resolución interna del BUS | Interno | El BUS traduce el nombre lógico del servicio a su URL real |

El BUS ya contempla la ruta pública de descubrimiento: `SecurityConfig` permite `GET /api/registro/servicios` sin token desde el Avance 2, aunque **el controlador que la implementa aún no existe**.

**Decisión pendiente antes de implementarlo:** cómo autorizar el `publish`. Un servicio que se autorregistra no puede presentar un token de usuario, y abrir la ruta a cualquiera permitiría envenenar el catálogo con URLs arbitrarias. Las dos opciones evaluadas son una clave de servicio compartida (`service key`) o restringir la publicación a `ADMIN`. Esta decisión se pospone deliberadamente y se documenta para que no se descubra como deuda técnica al final del curso.

---

# 6. Escáner de Vulnerabilidades — OWASP ZAP

## 6.1 Objetivo y alcance

El escaneo busca vulnerabilidades en la superficie que el sistema expone a sus consumidores antes del entregable final.

| Aspecto | Definición |
|---|---|
| Objetivo | BUS / Gateway en `http://localhost:8090`, el punto único de entrada |
| Superficie | Las 12 operaciones de negocio de §2, descritas en `docs/zap/openapi-gateway.yaml` |
| Autenticación | Token `ADMIN` inyectado en todas las peticiones, para alcanzar también los endpoints protegidos |
| Fuera de alcance | Puertos directos de los servicios (`8082`, `8088`, `8089`), servicios de andamiaje, endpoints de Actuator e infraestructura (PostgreSQL, RabbitMQ, Jaeger) |

## 6.2 Herramienta y procedimiento

| Elemento | Valor |
|---|---|
| Herramienta | OWASP ZAP, imagen Docker oficial `ghcr.io/zaproxy/zaproxy:stable` (versión `2.17.0`) |
| Modo de escaneo | `zap-api-scan.py`: importa el contrato OpenAPI, ejecuta el escaneo pasivo y un escaneo activo ajustado para APIs |
| Autenticación | Variable `ZAP_AUTH_HEADER_VALUE=Bearer <token>`: ZAP añade la cabecera `Authorization` a cada petición |
| Red | El contenedor alcanza el BUS del host mediante `host.docker.internal:8090` |
| Automatización | `docs/zap/run-zap-scan.ps1` |
| Fecha de ejecución | 16/11/2026 (`05:06:33 UTC`) |

El script ejecuta cinco pasos: comprueba que Docker y el BUS estén en `UP`; obtiene un token `ADMIN` con `POST /api/auth/login` a través del BUS; genera una copia del contrato con la URL alcanzable desde el contenedor; lanza el escaneo; y convierte el informe HTML a PDF con Microsoft Edge en modo headless. El núcleo del escaneo es:

```powershell
docker run --rm -v "${reportsDir}:/zap/wrk/:rw" `
  -e "ZAP_AUTH_HEADER_VALUE=Bearer $token" `
  ghcr.io/zaproxy/zaproxy:stable zap-api-scan.py `
  -t /zap/wrk/openapi-scan.yaml -f openapi `
  -r zap-report.html -w zap-report.md -J zap-report.json -I
```

El escaneo activo envía cargas de ataque (inyección SQL, XSS, *path traversal*, entre otras) a los endpoints `POST` y `PUT`, por lo que crea usuarios, productos y categorías de prueba. Se ejecutó únicamente contra el entorno local de desarrollo.

## 6.3 Resultados

El escaneo activo exploró **50 endpoints/variantes** (100 % con `Content-Type: application/json`; 66 % respuestas `4xx` ante cargas de ataque, 32 % `2xx`) y finalizó con **cero vulnerabilidades de riesgo alto o medio**:

| Nivel de riesgo | Alertas | Instancias |
|---|---|---|
| Alto (`High`) | **0** | 0 |
| Medio (`Medium`) | **0** | 0 |
| Bajo (`Low`) | **2** | 3 + Sistemático |
| Informativo (`Informational`) | **2** | 48 + Sistemático |

Detalle y clasificación técnica de las cuatro alertas reportadas en `docs/zap/reports/zap-report.md`:

| # | Alerta (ID ZAP / CWE) | Riesgo | Endpoints afectados | Análisis (verdadero o falso positivo) | Acción / Mitigación |
|---|---|---|---|---|---|
| 1 | **A Server Error response code was returned by the server** (`100000` / CWE-388) | Bajo (`Low`, confianza alta) | 3 instancias en `techretail-producto`: `POST /api/productos`, `POST /api/productos/categorias`, `PUT /api/productos/1` | **Verdadero positivo (manejo de excepción de BD).** Durante el *fuzzing*, ZAP reenvía el mismo nombre de categoría (violando la restricción `UNIQUE` de `categorias.nombre`) o inyecta bytes nulos (`0x00`) que PostgreSQL UTF-8 rechaza con `DataIntegrityViolationException`. A diferencia de Autenticación (que traduce duplicados a `409 Conflict`), el `ApiExceptionHandler` de Producto aún no captura `DataIntegrityViolationException`, por lo que retorna `500` genérico sin filtrar *stack trace* ni SQL. | Mapear `DataIntegrityViolationException` a `409 Conflict` / `400 Bad Request` en el `ApiExceptionHandler` de `techretail-producto` en el entregable final. |
| 2 | **Cross-Origin-Resource-Policy Header Missing or Invalid** (`90004` / CWE-693) | Bajo (`Low`, confianza media) | Sistemático (`GET`/`POST`/`PUT` `/api/productos/**`) | **Configuración por defecto en API REST.** La cabecera `Cross-Origin-Resource-Policy` (CORP) es un control opt-in de navegadores contra ataques de canal lateral (*Spectre*) sobre recursos embebidos. Spring Security no la incluye por defecto en respuestas JSON (`application/json`). | Si se incorpora un frontend web en otro origen, añadir `Cross-Origin-Resource-Policy: same-origin` en el `SecurityConfig` del BUS (`headers`). |
| 3 | **A Client Error response code was returned by the server** (`100000` / CWE-388) | Informativo (`Informational`) | 48 instancias (`400`, `401`, `404`, `409`) en `/api/auth/**`, `/api/productos/**`, `/api/monitoreo/**`, `/api/auditoria/**` y rutas de metadatos cloud (`/latest/meta-data/`, `/computeMetadata/v1/`) | **Falso positivo / comportamiento esperado.** Refleja que las defensas de entrada funcionan: Bean Validation rechaza parámetros maliciosos (`limite=http://www.google.com/` → `400`), Autenticación bloquea credenciales inválidas (`401`) y duplicados (`409`), y el BUS responde `404` ante rutas inexistentes o intentos de SSRF hacia metadatos cloud. | Ninguna: es la evidencia de que la validación de entrada y el control de acceso están activos. |
| 4 | **Non-Storable Content** (`10049` / CWE-524) | Informativo (`Informational`) | Sistemático (`Cache-Control: no-store`) | **Falso positivo / medida de seguridad intencional.** Spring Security inyecta `Cache-Control: no-store, no-cache, max-age=0, must-revalidate` por defecto para impedir que proxies compartidos o navegadores almacenen respuestas que contienen tokens JWT o datos de negocio. | Ninguna: mantener `no-store` en endpoints autenticados y de emisión de tokens. |

## 6.4 Observaciones conocidas antes del escaneo

Se registran además tres puntos de arquitectura revisados manualmente para contrastarlos con el informe automatizado:

- **`Location` con el host interno.** El `201` de P9 devuelve `Location: http://localhost:8082/...`: el BUS no reescribe la cabecera y revela el puerto interno de Producto. La mitigación candidata es reescribir `Location` en el BUS.
- **Puertos directos alcanzables.** Los servicios responden en su propio puerto (P18). La segunda capa de validación JWT impide la escritura sin token, y en un despliegue real esos puertos se restringirían a la red interna.
- **Valores por defecto de desarrollo.** `JWT_SECRET` tiene un valor por defecto en los `application.properties` y los usuarios de demostración se aprovisionan al arrancar. Ambos son aceptables solo en local: en despliegue se inyecta `JWT_SECRET` por entorno y se fija `AUTH_DEMO_USERS=false`.

## 6.5 Conclusión

El escaneo activo de OWASP ZAP (`v2.17.0`) sobre las 12 operaciones expuestas en el BUS confirma una **postura de seguridad sólida para el alcance del Avance 3**:

1. **Sin vulnerabilidades críticas, altas ni medias (0 High / 0 Medium).** Las pruebas de inyección SQL, XSS, *path traversal* y SSRF contra metadatos cloud fueron contenidas por el uso de consultas parametrizadas (JPA y `JdbcTemplate`), Bean Validation y el filtrado estricto de rutas en el Gateway.
2. **Defensa efectiva en la capa de entrada.** El 66 % de las respuestas del escáner fueron errores `4xx` controlados (`400`, `401`, `404`, `409`) en formato JSON uniforme, sin exponer trazas de pila (*stack traces*), versiones de servidor ni consultas SQL.
3. **Acciones acotadas para la entrega final.** El único hallazgo funcional de riesgo bajo —las 3 respuestas `500` por violación de restricción en `techretail-producto` ante payloads repetidos de *fuzzing*— se corrige añadiendo un `@ExceptionHandler(DataIntegrityViolationException.class)` que devuelva `409 Conflict`, homologándolo con `techretail-autenticacion`.

El informe completo se entrega junto con este documento en `docs/zap/reports/zap-report.pdf` (además de las versiones `zap-report.html`, `zap-report.md` y `zap-report.json`).

---

# 7. Lecciones Aprendidas / Dificultades Encontradas

**1. La configuración silenciosa cuesta más que cualquier bug de lógica.** Durante la Fase 1 los diez módulos arrancaban, pero los tests fallaban con `Failed to determine a suitable driver class`. La causa no era el driver: los `application.properties` contenían bloques YAML que Spring simplemente ignoraba. `server.port`, `spring.datasource.*` y `spring.flyway.*` no existían para el contenedor, y el fallo aparecía lejos del origen. La lección fue convertir la configuración en una aserción verificable: las pruebas ahora leen `server.port`, `spring.application.name` y `spring.flyway.locations` desde el `Environment` y fallan si vuelven a ser `null`. Un valor de configuración que nadie verifica es un valor que puede desaparecer sin que nadie lo note. En la misma línea, el módulo de facturación no compilaba porque `jaxb2-maven-plugin` 2.5.0 generaba clases `javax.xml.bind` mientras Spring Boot 4 usa Jakarta; la corrección fue subir a 3.1.0 (XJC Jakarta), no adaptar el código generado a mano.

**2. Spring Boot 4 convive con dos generaciones de Jackson, y eso rompe supuestos.** El proyecto arrastra `jjwt-jackson` 0.11.5, que trae Jackson 2, mientras que Spring Boot 4.1.1 inyecta en el contexto un `ObjectMapper` de **Jackson 3** (`tools.jackson.databind`). Un test escrito contra `com.fasterxml.jackson` fallaba al pedir el bean, porque el del contexto era otro. La solución no fue cambiar dependencias de producción —habría roto el parsing del JWT— sino aceptar la convivencia: escribir la prueba contra `tools.jackson` y asumir que `jjwt` resuelve su propio mapper en runtime. La lección es que actualizar un framework mayor no solo cambia lo que uno escribe, también cambia lo que el contenedor inyecta, y conviene comprobar el tipo real del bean antes de culpar al test.

**3. Un bug de auditoría que solo se ve cuando algo falla antes de responder.** La primera versión de `AuditFilter` leía el `status` de la respuesta y, si la cadena de filtros moría antes de fijarlo —timeout, excepción de transporte, error de conexión con el servicio destino—, la respuesta quedaba en el valor por defecto y la transacción se auditaba como `200`. El efecto era grave para un sistema cuyo valor es precisamente la trazabilidad: los fallos de comunicación aparecían en `auditoria_logs` como éxitos. La verificación independiente lo detectó y la corrección marca `500` y relanza la excepción. Al mismo tiempo se descubrió que el patrón de ruta `/ws/facturacion/**` no coincidía con `/ws/facturacion.wsdl`, porque `**` cubre segmentos hijos y no un sufijo en el último segmento; el test existente comparaba cadenas de texto de la configuración y por eso pasaba en verde. Ambos casos enseñan lo mismo: **una prueba que compara texto de configuración no prueba que la configuración funcione**. La versión corregida parsea cada patrón con `PathPatternParser` y verifica que realmente resuelva las rutas reales.

**4. Un `400` que llegaba al cliente como `401`, y un test que no podía verlo.** Durante las pruebas manuales de §3.2, `GET /api/monitoreo/historial?limite=0` respondía `401 Unauthorized` en lugar del `400` que exige la validación. El controlador lanzaba `ResponseStatusException(400)` y el test `unLimiteInvalidoDevuelve400` pasaba en verde, así que el problema no estaba en la lógica: Tomcat convierte un `sendError` en un *error dispatch* interno hacia `/error`, y ese dispatch no atraviesa el `JwtAuthenticationFilter` porque un `OncePerRequestFilter` no filtra el dispatcher de tipo `ERROR`. La cadena de seguridad veía la petición interna como anónima, `anyRequest().authenticated()` la rechazaba y el entry point respondía `401`. El mismo defecto afectaba al BUS (`/api/auditoria?limite=0`) y a las rutas inexistentes del catálogo (`GET /api/productos/1/algo` devolvía `401` en lugar de `404`). La corrección fue permitir `/error` en el `SecurityConfig` de gateway, monitoreo y producto. Pero la lección de método importa más que el arreglo: **MockMvc no ejecuta el error dispatch del contenedor**, así que una prueba con MockMvc puede certificar un comportamiento que el servidor real no tiene. Las tres pruebas nuevas (`*ErrorDispatchTests`) levantan un Tomcat real con `webEnvironment = RANDOM_PORT` y verifican el status sobre HTTP; se comprobó que fallan con el código anterior (`expected: 400`, real `401`) y pasan con la corrección. Es la lección 3 un nivel más abajo: comparar el texto de la configuración no prueba la configuración, y probar el controlador no prueba el servidor.

---

*Documento finalizado el 16/11/2026. Todas las secciones reflejan resultados reales de ejecución: 115 pruebas automatizadas en verde en los cuatro módulos implementados (127 en el repositorio completo), 18 casos numerados ejecutados contra el BUS en Postman y escaneo activo de vulnerabilidades con OWASP ZAP 2.17.0 (0 alertas altas, 0 medias, 2 bajas y 2 informativas).*
