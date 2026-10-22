package com.techretail.techretail_monitoreo.salud;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Zero Trust: el servicio valida el JWT aunque se acceda sin pasar por el gateway. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MonitoreoSeguridadTests {

    private static final String[] RUTAS = {"/api/monitoreo/estado", "/api/monitoreo/historial"};

    @Autowired
    private MockMvc mockMvc;

    @Value("${techretail.security.jwt.secret}")
    private String secreto;

    @Value("${techretail.security.jwt.issuer}")
    private String emisor;

    private String token(String rol) {
        return TokensDePrueba.bearer(rol, secreto, emisor, 1);
    }

    @Test
    void sinTokenDevuelve401ConCuerpoJson() throws Exception {
        for (String ruta : RUTAS) {
            String cuerpo = mockMvc.perform(get(ruta)).andExpect(status().isUnauthorized())
                    .andReturn().getResponse().getContentAsString();
            assertThat(cuerpo).contains("\"status\":401");
        }
    }

    @Test
    void rolClienteDevuelve403() throws Exception {
        for (String ruta : RUTAS) {
            String cuerpo = mockMvc.perform(get(ruta).header(HttpHeaders.AUTHORIZATION, token("CLIENTE")))
                    .andExpect(status().isForbidden())
                    .andReturn().getResponse().getContentAsString();
            assertThat(cuerpo).contains("\"status\":403");
        }
    }

    @Test
    void adminYEmpleadoTienenAcceso() throws Exception {
        for (String rol : new String[] {"ADMIN", "EMPLEADO"}) {
            for (String ruta : RUTAS) {
                mockMvc.perform(get(ruta).header(HttpHeaders.AUTHORIZATION, token(rol)))
                        .andExpect(status().isOk());
            }
        }
    }

    @Test
    void tokenFirmadoConOtroSecretoDevuelve401() throws Exception {
        String ajeno = TokensDePrueba.bearer("ADMIN", TokensDePrueba.SECRETO_AJENO, emisor, 1);

        mockMvc.perform(get(RUTAS[0]).header(HttpHeaders.AUTHORIZATION, ajeno))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenExpiradoDevuelve401() throws Exception {
        String expirado = TokensDePrueba.bearer("ADMIN", secreto, emisor, -5);

        mockMvc.perform(get(RUTAS[0]).header(HttpHeaders.AUTHORIZATION, expirado))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenConOtroEmisorDevuelve401() throws Exception {
        String otro = TokensDePrueba.bearer("ADMIN", secreto, "otro-emisor", 1);

        mockMvc.perform(get(RUTAS[0]).header(HttpHeaders.AUTHORIZATION, otro))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void esquemaDistintoDeBearerDevuelve401() throws Exception {
        mockMvc.perform(get(RUTAS[0]).header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rolDesconocidoDevuelve401() throws Exception {
        mockMvc.perform(get(RUTAS[0]).header(HttpHeaders.AUTHORIZATION, token("SUPERUSUARIO")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void laSaludDelServicioSigueSiendoPublica() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void cualquierOtraRutaExigeAutenticacion() throws Exception {
        mockMvc.perform(get("/api/otra")).andExpect(status().isUnauthorized());
    }
}
