package com.techretail.techretail_autenticacion.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.techretail.techretail_autenticacion.security.JwtProperties;
import com.techretail.techretail_autenticacion.security.JwtService;
import com.techretail.techretail_autenticacion.security.Rol;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Contrato de {@code POST /api/auth/registro} y {@code POST /api/auth/login}.
 *
 * <p>Los casos usan el parser real ({@link JwtService#validar(String)}) y el
 * parser crudo de jjwt: el token que devuelve el login tiene que validarse con
 * la misma clave que usa el BUS y llevar los claims del Avance2 8.2.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTests {

    private static final String PASSWORD = "Clave123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void registroCreaUnClienteConPasswordHasheado() throws Exception {
        MvcResult respuesta = registrar("nuevo.cliente@techretail.pe", PASSWORD, "Nuevo Cliente");

        assertThat(respuesta.getResponse().getStatus()).isEqualTo(201);
        JsonNode cuerpo = leer(respuesta);
        assertThat(cuerpo.get("usuario").asText()).isEqualTo("nuevo.cliente@techretail.pe");
        assertThat(cuerpo.get("rol").asText()).isEqualTo("CLIENTE");
        assertThat(cuerpo.get("estado").asText()).isEqualTo("ACTIVO");
        assertThat(cuerpo.get("idUsuario").asLong()).isPositive();

        String passwordGuardado = jdbcTemplate.queryForObject(
                "SELECT password FROM usuarios WHERE usuario = ?", String.class, "nuevo.cliente@techretail.pe");
        assertThat(passwordGuardado)
                .as("el password jamas se guarda en claro")
                .startsWith("$2")
                .isNotEqualTo(PASSWORD);
    }

    @Test
    void elRegistroPublicoIgnoraElRolPedidoYAsignaCliente() throws Exception {
        MvcResult respuesta = mockMvc.perform(post("/api/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"usuario":"escalada@techretail.pe","password":"%s","nombre":"Intruso","rol":"ADMIN"}
                        """.formatted(PASSWORD)))
                .andReturn();

        assertThat(respuesta.getResponse().getStatus()).isEqualTo(201);
        assertThat(rolDe("escalada@techretail.pe")).isEqualTo("CLIENTE");
    }

    @Test
    void registroRechazaUnUsuarioDuplicado() throws Exception {
        registrar("duplicado@techretail.pe", PASSWORD, "Primero");

        MvcResult respuesta = registrar("duplicado@techretail.pe", "OtraClave123!", "Segundo");

        assertThat(respuesta.getResponse().getStatus()).isEqualTo(409);
        JsonNode cuerpo = leer(respuesta);
        assertThat(cuerpo.get("status").asInt()).isEqualTo(409);
        assertThat(cuerpo.get("error").asText()).isEqualTo("Conflict");
    }

    @Test
    void registroRechazaUnPasswordDemasiadoCorto() throws Exception {
        MvcResult respuesta = registrar("corta@techretail.pe", "corta", "Clave Corta");

        assertThat(respuesta.getResponse().getStatus()).isEqualTo(400);
        assertThat(leer(respuesta).get("status").asInt()).isEqualTo(400);
    }

    @Test
    void registroRechazaUnPasswordQueBcryptNoPuedeProcesar() throws Exception {
        MvcResult respuesta = registrar("larga@techretail.pe", "a".repeat(73), "Clave Larga");

        assertThat(respuesta.getResponse().getStatus())
                .as("BCrypt ignora los bytes posteriores a 72: se rechaza con 400, nunca con 500")
                .isEqualTo(400);
    }

    @Test
    void loginEmiteUnTokenValidoConLosClaimsDelContrato() throws Exception {
        registrar("login.contrato@techretail.pe", PASSWORD, "Maria Fernandez");
        long idUsuario = idDe("login.contrato@techretail.pe");

        MvcResult respuesta = login("login.contrato@techretail.pe", PASSWORD);

        assertThat(respuesta.getResponse().getStatus()).isEqualTo(200);
        JsonNode cuerpo = leer(respuesta);
        assertThat(cuerpo.get("tipo").asText()).isEqualTo("Bearer");
        assertThat(cuerpo.get("expiraEn").asLong()).isEqualTo(3600L);
        assertThat(cuerpo.get("rol").asText()).isEqualTo("CLIENTE");
        assertThat(cuerpo.get("idUsuario").asLong()).isEqualTo(idUsuario);
        assertThat(cuerpo.get("nombre").asText()).isEqualTo("Maria Fernandez");
        assertThat(cuerpo.has("password")).isFalse();

        String token = cuerpo.get("token").asText();
        assertThat(jwtService.validar(token)).hasValueSatisfying(validado -> {
            assertThat(validado.usuario()).isEqualTo("login.contrato@techretail.pe");
            assertThat(validado.rol()).isEqualTo(Rol.CLIENTE);
            assertThat(validado.idUsuario()).isEqualTo(idUsuario);
            assertThat(validado.expiraEn()).isAfter(Instant.now());
        });

        Claims claims = claims(token);
        assertThat(claims.getSubject()).isEqualTo("login.contrato@techretail.pe");
        assertThat(claims.getIssuer()).isEqualTo(jwtProperties.getIssuer());
        assertThat(claims.get("rol")).isEqualTo("CLIENTE");
        assertThat(claims.get("idUsuario", Number.class).longValue()).isEqualTo(idUsuario);
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isAfter(new Date());
    }

    @Test
    void emiteElRolDeCadaUsuarioDelCatalogo() throws Exception {
        record Caso(Rol rol, String usuario) {
        }
        List<Caso> casos = List.of(
                new Caso(Rol.CLIENTE, "rol.cliente@techretail.pe"),
                new Caso(Rol.EMPLEADO, "rol.empleado@techretail.pe"),
                new Caso(Rol.ADMIN, "rol.admin@techretail.pe"));

        for (Caso caso : casos) {
            registrar(caso.usuario(), PASSWORD, "Usuario " + caso.rol());
            asignarRol(caso.usuario(), caso.rol());

            JsonNode cuerpo = leer(login(caso.usuario(), PASSWORD));

            assertThat(cuerpo.get("rol").asText()).isEqualTo(caso.rol().name());
            assertThat(jwtService.validar(cuerpo.get("token").asText()))
                    .hasValueSatisfying(validado -> assertThat(validado.rol()).isEqualTo(caso.rol()));
        }
    }

    @Test
    void elRechazoDeCredencialesNoRevelaSiElUsuarioExiste() throws Exception {
        registrar("existente@techretail.pe", PASSWORD, "Existente");

        MvcResult usuarioInexistente = login("no.existe@techretail.pe", PASSWORD);
        MvcResult passwordIncorrecta = login("existente@techretail.pe", "ClaveIncorrecta1!");

        assertThat(usuarioInexistente.getResponse().getStatus()).isEqualTo(401);
        assertThat(passwordIncorrecta.getResponse().getStatus()).isEqualTo(401);
        assertThat(usuarioInexistente.getResponse().getContentAsString())
                .as("mismo cuerpo para no enumerar usuarios")
                .isEqualTo(passwordIncorrecta.getResponse().getContentAsString())
                .contains("\"status\":401");
    }

    @Test
    void loginRechazaUnUsuarioInactivo() throws Exception {
        registrar("inactivo@techretail.pe", PASSWORD, "Inactivo");
        jdbcTemplate.update("UPDATE usuarios SET estado = 'INACTIVO' WHERE usuario = ?", "inactivo@techretail.pe");

        MvcResult respuesta = login("inactivo@techretail.pe", PASSWORD);

        assertThat(respuesta.getResponse().getStatus()).isEqualTo(401);
        assertThat(respuesta.getResponse().getContentAsString())
                .isEqualTo(login("fantasma@techretail.pe", PASSWORD).getResponse().getContentAsString());
    }

    @Test
    void elErrorDeLoginEsJsonEstandarSinCacheNiSniff() throws Exception {
        MvcResult respuesta = login("fantasma@techretail.pe", PASSWORD);

        assertThat(respuesta.getResponse().getStatus()).isEqualTo(401);
        assertThat(respuesta.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(respuesta.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        assertThat(respuesta.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(respuesta.getResponse().getHeader("X-Frame-Options")).isEqualTo("DENY");
        JsonNode cuerpo = leer(respuesta);
        assertThat(cuerpo.get("status").asInt()).isEqualTo(401);
        assertThat(cuerpo.get("error").asText()).isEqualTo("Unauthorized");
        assertThat(cuerpo.get("message").asText()).isNotBlank();
    }

    @Test
    void loginRechazaUnPasswordEnormeSinReventar() throws Exception {
        registrar("enorme@techretail.pe", PASSWORD, "Enorme");

        MvcResult respuesta = login("enorme@techretail.pe", "a".repeat(200));

        assertThat(respuesta.getResponse().getStatus())
                .as("BCrypt no puede procesar mas de 72 bytes: 401, nunca 500")
                .isEqualTo(401);
    }

    @Test
    void unCuerpoIlegibleDevuelve400YNoUnStackTrace() throws Exception {
        MvcResult respuesta = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"usuario\":"))
                .andReturn();

        assertThat(respuesta.getResponse().getStatus()).isEqualTo(400);
        assertThat(leer(respuesta).get("message").asText()).doesNotContain("Exception");
    }

    @Test
    void elUsuarioSeNormalizaAminisculas() throws Exception {
        registrar("Mixto@TechRetail.pe", PASSWORD, "Mixto");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT usuario FROM usuarios WHERE usuario = ?", String.class, "mixto@techretail.pe"))
                .isEqualTo("mixto@techretail.pe");
        assertThat(login("MIXTO@TECHRETAIL.PE", PASSWORD).getResponse().getStatus())
                .as("el login acepta el usuario con otra capitalizacion")
                .isEqualTo(200);
    }

    private MvcResult registrar(String usuario, String password, String nombre) throws Exception {
        return mockMvc.perform(post("/api/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"usuario":"%s","password":"%s","nombre":"%s"}
                        """.formatted(usuario, password, nombre)))
                .andReturn();
    }

    private MvcResult login(String usuario, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"usuario":"%s","password":"%s"}
                        """.formatted(usuario, password)))
                .andReturn();
    }

    private JsonNode leer(MvcResult resultado) throws Exception {
        return objectMapper.readTree(resultado.getResponse().getContentAsString());
    }

    private long idDe(String usuario) {
        return jdbcTemplate.queryForObject("SELECT id FROM usuarios WHERE usuario = ?", Long.class, usuario);
    }

    private String rolDe(String usuario) {
        return jdbcTemplate.queryForObject("""
                SELECT r.nombre FROM usuarios u JOIN roles r ON r.id = u.id_rol WHERE u.usuario = ?
                """, String.class, usuario);
    }

    private void asignarRol(String usuario, Rol rol) {
        jdbcTemplate.update("""
                UPDATE usuarios SET id_rol = (SELECT id FROM roles WHERE nombre = ?) WHERE usuario = ?
                """, rol.name(), usuario);
    }

    private Claims claims(String token) {
        SecretKey clave = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
        return Jwts.parserBuilder()
                .setSigningKey(clave)
                .requireIssuer(jwtProperties.getIssuer())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
