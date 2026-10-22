package com.techretail.techretail_monitoreo.salud;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MonitoreoEndpointsTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Value("${techretail.security.jwt.secret}")
    private String secreto;

    @Value("${techretail.security.jwt.issuer}")
    private String emisor;

    private String admin() {
        return TokensDePrueba.bearer("ADMIN", secreto, emisor, 1);
    }

    @BeforeEach
    void datos() {
        jdbc.update("DELETE FROM monitoreo_logs");
        jdbc.update("INSERT INTO monitoreo_logs (nombre_servicio, estado, tiempo_respuesta_ms, fecha_consulta) VALUES ('techretail-pago','UP',10,TIMESTAMP '2026-01-01 10:00:00')");
        jdbc.update("INSERT INTO monitoreo_logs (nombre_servicio, estado, tiempo_respuesta_ms, fecha_consulta) VALUES ('techretail-pago','DOWN',2000,TIMESTAMP '2026-01-01 10:05:00')");
        jdbc.update("INSERT INTO monitoreo_logs (nombre_servicio, estado, tiempo_respuesta_ms, fecha_consulta) VALUES ('techretail-cliente','UP',7,TIMESTAMP '2026-01-01 10:01:00')");
    }

    @Test
    void estadoDevuelveLaUltimaLecturaDeCadaServicio() throws Exception {
        mockMvc.perform(get("/api/monitoreo/estado").header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.servicio=='techretail-pago')].estado").value("DOWN"))
                .andExpect(jsonPath("$[?(@.servicio=='techretail-cliente')].estado").value("UP"));
    }

    @Test
    void historialRespetaElLimiteYElOrdenReciente() throws Exception {
        mockMvc.perform(get("/api/monitoreo/historial?limite=2").header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].estado").value("DOWN"));
    }

    @Test
    void unLimiteInvalidoDevuelve400() throws Exception {
        mockMvc.perform(get("/api/monitoreo/historial?limite=0").header(HttpHeaders.AUTHORIZATION, admin())).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/monitoreo/historial?limite=100000").header(HttpHeaders.AUTHORIZATION, admin())).andExpect(status().isBadRequest());
    }
}
