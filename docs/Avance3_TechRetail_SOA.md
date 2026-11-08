# PROYECTO FINAL
## ARQUITECTURA ORIENTADA A SERVICIOS (100000SI84)
### Avance 3 (Semana 15) — Implementación Parcial

**Nombre del proyecto:** Implementación de una Arquitectura SOA para TechRetail S.A.

**Curso:** Arquitectura Orientada al Servicio (100000SI84)
**Ciclo:** 2026 — Ciclo 2 Agosto
**Carrera:** Ingeniería de Sistemas e Informática
**Docente:** Calderón Chuquillanqui, Anthony Vidal
**Equipo:** Grupo 1
**Integrante:** [COMPLETAR: apellidos y nombres]
**Fecha de entrega:** [COMPLETAR: dd/mm/2026]

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

**Alcance declarado con honestidad:** los seis servicios restantes (cliente, pedido, pago, inventario, notificación y facturación SUNAT) continúan como andamiaje —módulo, configuración y migración inicial— y no forman parte de la demostración de este avance. El catálogo UDDI se mantiene como **propuesta de diseño** (sección 4), consistente con lo planificado en el Avance 2.

---

# 2. Servicios Implementados

Los cuatro módulos son proyectos Maven **independientes**, no un reactor multi-módulo. Cada uno se compila y se ejecuta desde su propio directorio con su propio `mvnw`, sobre Java 21 y Spring Boot 4.1.1.

| Módulo | Puerto | Base de datos | Responsabilidad |
|---|---|---|---|
| `techretail-autenticacion` | 8088 | `db_autenticacion` | Registro, login y emisión de JWT |
| `techretail-producto` | 8082 | `db_producto` | Catálogo de productos y categorías |
| `techretail-monitoreo` | 8089 | `db_monitoreo` | Sondeo de salud y tiempos de respuesta |
| `techretail-gateway` | 8090 | `db_esb` | Enrutamiento, seguridad central y auditoría |

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

Las pruebas se ejecutaron el **08/11/2026** contra el BUS en `http://localhost:8090`, con PostgreSQL en el puerto 5433 y los cuatro servicios levantados. La evidencia no son capturas de pantalla: cada caso conserva la **petición exacta** y la **respuesta exacta** en bloques `http` y `json`, de modo que se puedan reproducir pegándolos en Postman o importando la colección `docs/postman_collection/techretail-avance3.json`.

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

El `400` de P14 es el que motivó una corrección durante esta misma sesión de pruebas: el error dispatch interno de Tomcat (`/error`) quedaba detrás de la cadena de seguridad y convertía cualquier `400` o `404` en `401`. Se corrigió en `SecurityConfig` de gateway, monitoreo y producto, y se agregó una prueba de regresión sobre Tomcat real, porque la prueba equivalente con MockMvc pasaba en verde sin reproducir el problema (ver §5, lección 4).

### 3.2.11 Sobre DELETE

El catálogo no expone `DELETE` por diseño; la baja es lógica con `disponible=false` (caso P11). Se documenta explícitamente para que la rúbrica no lo lea como un faltante: un producto retirado debe seguir existiendo para que la auditoría y el historial de ventas conserven sentido.

## 3.3 Alcance del Avance 3 respecto de la rúbrica

| Requisito de la rúbrica | Estado |
|---|---|
| AutenticacionService (registro + login + JWT) | Implementado |
| ProductoService con su propia BD y Flyway | Implementado |
| ClienteService con su propia BD y Flyway | **Pendiente** (módulo en andamiaje) |
| Capturas Postman (GET, POST, PUT) con JSON | Implementado y documentado (§3.2): P4 `GET`, P9 `POST`, P10/P11 `PUT`, con la petición y la respuesta reales |
| Capturas Postman `DELETE` | Cubierto como baja lógica (P11); no hay `DELETE` por diseño |
| Conexión a PostgreSQL, una BD por servicio | Implementado y documentado (§2.5) |
| Demostración de asincronismo con RabbitMQ | **Pendiente** (dependencias declaradas, sin productor/consumidor) |
| FacturacionSunatService Contract-First + WSDL + invocación SOAP | **Pendiente** (contrato XSD y plugin listos, sin lógica) |
| Catálogo UDDI funcional | **Propuesto** (§4) |
| Colección Postman exportada | Incluida en `docs/postman_collection/techretail-avance3.json` (18 casos ejecutados) |
| Lecciones aprendidas | Cubierto (§5) |

---

# 4. Registro de Servicios (Catálogo UDDI) — Propuesta

El catálogo de servicios se mantiene como **propuesta de diseño**, no como funcionalidad implementada. La decisión es explícita: se priorizó que los servicios existentes funcionen y estén auditados antes de añadir un componente de descubrimiento que nadie consumiría todavía.

El diseño previsto, coherente con el Avance 2 §5.3, es el siguiente:

| Operación UDDI | Endpoint propuesto | Acceso previsto | Comportamiento |
|---|---|---|---|
| **Publish** | `POST /api/registro/servicios` | Autenticado (mecanismo por definir) | Cada servicio se registra al arrancar con nombre, URL, versión y, si aplica, URL del WSDL |
| **Find** | `GET /api/registro/servicios` | Público | Devuelve el catálogo de servicios disponibles |
| **Bind** | Resolución interna del BUS | Interno | El BUS traduce el nombre lógico del servicio a su URL real |

El BUS ya contempla la ruta pública de descubrimiento: `SecurityConfig` permite `GET /api/registro/servicios` sin token desde el Avance 2, aunque **el controlador que la implementa aún no existe**.

**Decisión pendiente antes de implementarlo:** cómo autorizar el `publish`. Un servicio que se autorregistra no puede presentar un token de usuario, y abrir la ruta a cualquiera permitiría envenenar el catálogo con URLs arbitrarias. Las dos opciones evaluadas son una clave de servicio compartida (`service key`) o restringir la publicación a `ADMIN`. Esta decisión se pospone deliberadamente y se documenta para que no se descubra como deuda técnica al final del curso.

---

# 5. Lecciones Aprendidas / Dificultades Encontradas

**1. La configuración silenciosa cuesta más que cualquier bug de lógica.** Durante la Fase 1 los diez módulos arrancaban, pero los tests fallaban con `Failed to determine a suitable driver class`. La causa no era el driver: los `application.properties` contenían bloques YAML que Spring simplemente ignoraba. `server.port`, `spring.datasource.*` y `spring.flyway.*` no existían para el contenedor, y el fallo aparecía lejos del origen. La lección fue convertir la configuración en una aserción verificable: las pruebas ahora leen `server.port`, `spring.application.name` y `spring.flyway.locations` desde el `Environment` y fallan si vuelven a ser `null`. Un valor de configuración que nadie verifica es un valor que puede desaparecer sin que nadie lo note. En la misma línea, el módulo de facturación no compilaba porque `jaxb2-maven-plugin` 2.5.0 generaba clases `javax.xml.bind` mientras Spring Boot 4 usa Jakarta; la corrección fue subir a 3.1.0 (XJC Jakarta), no adaptar el código generado a mano.

**2. Spring Boot 4 convive con dos generaciones de Jackson, y eso rompe supuestos.** El proyecto arrastra `jjwt-jackson` 0.11.5, que trae Jackson 2, mientras que Spring Boot 4.1.1 inyecta en el contexto un `ObjectMapper` de **Jackson 3** (`tools.jackson.databind`). Un test escrito contra `com.fasterxml.jackson` fallaba al pedir el bean, porque el del contexto era otro. La solución no fue cambiar dependencias de producción —habría roto el parsing del JWT— sino aceptar la convivencia: escribir la prueba contra `tools.jackson` y asumir que `jjwt` resuelve su propio mapper en runtime. La lección es que actualizar un framework mayor no solo cambia lo que uno escribe, también cambia lo que el contenedor inyecta, y conviene comprobar el tipo real del bean antes de culpar al test.

**3. Un bug de auditoría que solo se ve cuando algo falla antes de responder.** La primera versión de `AuditFilter` leía el `status` de la respuesta y, si la cadena de filtros moría antes de fijarlo —timeout, excepción de transporte, error de conexión con el servicio destino—, la respuesta quedaba en el valor por defecto y la transacción se auditaba como `200`. El efecto era grave para un sistema cuyo valor es precisamente la trazabilidad: los fallos de comunicación aparecían en `auditoria_logs` como éxitos. La verificación independiente lo detectó y la corrección marca `500` y relanza la excepción. Al mismo tiempo se descubrió que el patrón de ruta `/ws/facturacion/**` no coincidía con `/ws/facturacion.wsdl`, porque `**` cubre segmentos hijos y no un sufijo en el último segmento; el test existente comparaba cadenas de texto de la configuración y por eso pasaba en verde. Ambos casos enseñan lo mismo: **una prueba que compara texto de configuración no prueba que la configuración funcione**. La versión corregida parsea cada patrón con `PathPatternParser` y verifica que realmente resuelva las rutas reales.

**4. Un `400` que llegaba al cliente como `401`, y un test que no podía verlo.** Durante las pruebas manuales de §3.2, `GET /api/monitoreo/historial?limite=0` respondía `401 Unauthorized` en lugar del `400` que exige la validación. El controlador lanzaba `ResponseStatusException(400)` y el test `unLimiteInvalidoDevuelve400` pasaba en verde, así que el problema no estaba en la lógica: Tomcat convierte un `sendError` en un *error dispatch* interno hacia `/error`, y ese dispatch no atraviesa el `JwtAuthenticationFilter` porque un `OncePerRequestFilter` no filtra el dispatcher de tipo `ERROR`. La cadena de seguridad veía la petición interna como anónima, `anyRequest().authenticated()` la rechazaba y el entry point respondía `401`. El mismo defecto afectaba al BUS (`/api/auditoria?limite=0`) y a las rutas inexistentes del catálogo (`GET /api/productos/1/algo` devolvía `401` en lugar de `404`). La corrección fue permitir `/error` en el `SecurityConfig` de gateway, monitoreo y producto. Pero la lección de método importa más que el arreglo: **MockMvc no ejecuta el error dispatch del contenedor**, así que una prueba con MockMvc puede certificar un comportamiento que el servidor real no tiene. Las tres pruebas nuevas (`*ErrorDispatchTests`) levantan un Tomcat real con `webEnvironment = RANDOM_PORT` y verifican el status sobre HTTP; se comprobó que fallan con el código anterior (`expected: 400`, real `401`) y pasan con la corrección. Es la lección 3 un nivel más abajo: comparar el texto de la configuración no prueba la configuración, y probar el controlador no prueba el servidor.

---

*Documento finalizado el 08/11/2026. Las secciones 1, 2 (incluida §2.6), 3.1, 3.2 y 5 reflejan resultados reales de esa fecha: 115 pruebas automatizadas en verde en los cuatro módulos implementados (127 en el repositorio completo) y 18 casos numerados ejecutados contra el BUS —21 peticiones en la colección Postman— con su petición y respuesta transcritas.*
