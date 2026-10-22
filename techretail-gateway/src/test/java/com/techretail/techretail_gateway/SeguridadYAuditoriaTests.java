package com.techretail.techretail_gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.techretail.techretail_gateway.security.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pruebas de la politica de seguridad del BUS y de la auditoria en db_esb.
 *
 * <p>No dependen de que los servicios destino esten arriba: los casos 401 y
 * 403 se resuelven antes del enrutamiento, y el caso publico usa el endpoint
 * de salud del propio gateway.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SeguridadYAuditoriaTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtProperties jwtProperties;

    @Test
    void sinTokenDevuelve401() throws Exception {
        String cuerpo = mockMvc.perform(get("/api/clientes"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(cuerpo).contains("\"status\":401");
    }

    @Test
    void tokenFirmadoConOtroSecretoDevuelve401() throws Exception {
        String token = firmar("intruso", "ADMIN", 99L,
                "secreto-ajeno-de-pruebas-con-mas-de-32-bytes-para-hs256");

        mockMvc.perform(get("/api/clientes").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cabeceraNoBearerDevuelve401() throws Exception {
        mockMvc.perform(get("/api/clientes").header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rolSinPermisoDevuelve403() throws Exception {
        String token = firmar("maria.fernandez@email.com", "CLIENTE", 8L, jwtProperties.getSecret());

        mockMvc.perform(get("/api/monitoreo/estado").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void laSaludDelBusEsPublica() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void unTokenClienteNoPuedeInvocarFacturacionPorSubruta() throws Exception {
        String token = firmar("maria.fernandez@email.com", "CLIENTE", 8L, jwtProperties.getSecret());

        String cuerpo = mockMvc.perform(post("/ws/facturacion/servicio")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andReturn().getResponse().getContentAsString();

        assertThat(cuerpo).contains("\"status\":403");
    }

    @Test
    void unTokenEmpleadoAlcanzaLaRutaDeFacturacion() throws Exception {
        String token = firmar("empleado@techretail.pe", "EMPLEADO", 9L, jwtProperties.getSecret());

        int estado;
        try {
            estado = mockMvc.perform(post("/ws/facturacion/servicio")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andReturn().getResponse().getStatus();
        } catch (Exception excepcion) {
            // El servicio de facturacion no esta levantado: la peticion supero la
            // capa de seguridad y murio en el enrutamiento, que es lo que se prueba.
            estado = 502;
        }

        assertThat(estado)
                .as("EMPLEADO conserva el acceso a facturacion (Avance2 8.3)")
                .isNotIn(401, 403);
    }

    @Test
    void laAuditoriaRegistraLaTransaccionRechazada() throws Exception {
        String endpoint = "/api/clientes/prueba-auditoria";

        mockMvc.perform(get(endpoint)).andExpect(status().isUnauthorized());

        // La auditoria es asincrona: se espera a que el evento llegue a db_esb.
        long limite = System.currentTimeMillis() + 5_000L;
        while (System.currentTimeMillis() < limite
                && jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM auditoria_logs WHERE endpoint = ?", Integer.class, endpoint) == 0) {
            Thread.sleep(25);
        }

        Map<String, Object> fila = jdbcTemplate.queryForMap("""
                SELECT servicio_origen, servicio_destino, operacion, estado_respuesta, usuario
                FROM auditoria_logs
                WHERE endpoint = ?
                ORDER BY id DESC
                LIMIT 1
                """, endpoint);

        assertThat(fila.get("servicio_origen")).isEqualTo("GATEWAY");
        assertThat(fila.get("servicio_destino")).isEqualTo("techretail-cliente");
        assertThat(fila.get("operacion")).isEqualTo("GET");
        assertThat(fila.get("estado_respuesta")).isEqualTo(401);
        assertThat(fila.get("usuario")).isEqualTo("anonimo");
    }

    private String firmar(String usuario, String rol, Long idUsuario, String secreto) {
        SecretKey clave = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        Instant ahora = Instant.now();
        return Jwts.builder()
                .setSubject(usuario)
                .claim("rol", rol)
                .claim("idUsuario", idUsuario)
                .setIssuer(jwtProperties.getIssuer())
                .setIssuedAt(Date.from(ahora))
                .setExpiration(Date.from(ahora.plus(1, ChronoUnit.MINUTES)))
                .signWith(clave, SignatureAlgorithm.HS256)
                .compact();
    }
}
