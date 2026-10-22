package com.techretail.techretail_gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.techretail.techretail_gateway.security.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
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
 * Fase 2.2: endpoints de monitoreo de la auditoria, solo para ADMIN y EMPLEADO.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MonitoreoAuditoriaEndpointTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtProperties jwtProperties;

    @Test
    void sinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/auditoria")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auditoria/resumen")).andExpect(status().isUnauthorized());
    }

    @Test
    void clienteRecibe403() throws Exception {
        mockMvc.perform(get("/api/auditoria").header(HttpHeaders.AUTHORIZATION, bearer("CLIENTE")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/auditoria/resumen").header(HttpHeaders.AUTHORIZATION, bearer("CLIENTE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminYEmpleadoConsultanLosEventosRecientes() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO auditoria_logs (servicio_origen, servicio_destino, operacion, endpoint, estado_respuesta, usuario, detalle)
                VALUES ('GATEWAY', 'techretail-pedido', 'POST', '/api/pedidos/monitoreo-test', 201, 'ana@techretail.pe', 'd')
                """);

        for (String rol : new String[] {"ADMIN", "EMPLEADO"}) {
            mockMvc.perform(get("/api/auditoria?limite=5").header(HttpHeaders.AUTHORIZATION, bearer(rol)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].endpoint").exists())
                    .andExpect(jsonPath("$[0].estadoRespuesta").exists())
                    .andExpect(jsonPath("$[0].timestamp").exists());
        }
    }

    @Test
    void elLimiteSeAcotaYSeValida() throws Exception {
        for (int i = 0; i < 3; i++) {
            jdbcTemplate.update("""
                    INSERT INTO auditoria_logs (servicio_origen, servicio_destino, operacion, endpoint, estado_respuesta, usuario)
                    VALUES ('GATEWAY', 'techretail-pedido', 'GET', '/api/pedidos/limite', 200, 'u')
                    """);
        }

        mockMvc.perform(get("/api/auditoria?limite=2").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/api/auditoria?limite=0").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/auditoria?limite=100000").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void elResumenAgrupaPorEstadoYServicio() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO auditoria_logs (servicio_origen, servicio_destino, operacion, endpoint, estado_respuesta, usuario)
                VALUES ('GATEWAY', 'techretail-pago', 'GET', '/api/pagos/resumen', 500, 'u')
                """);

        String cuerpo = mockMvc.perform(get("/api/auditoria/resumen")
                .header(HttpHeaders.AUTHORIZATION, bearer("EMPLEADO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").isNumber())
                .andExpect(jsonPath("$.errores").isNumber())
                .andExpect(jsonPath("$.porServicio").isMap())
                .andReturn().getResponse().getContentAsString();

        assertThat(cuerpo).contains("techretail-pago");
    }

    private String bearer(String rol) {
        SecretKey clave = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
        Instant ahora = Instant.now();
        return "Bearer " + Jwts.builder()
                .setSubject("usuario@techretail.pe")
                .claim("rol", rol)
                .claim("idUsuario", 1L)
                .setIssuer(jwtProperties.getIssuer())
                .setIssuedAt(Date.from(ahora))
                .setExpiration(Date.from(ahora.plus(1, ChronoUnit.MINUTES)))
                .signWith(clave, SignatureAlgorithm.HS256)
                .compact();
    }
}
