package com.techretail.techretail_producto.producto;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Zero Trust: lectura publica del catalogo, escritura solo ADMIN. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductoSeguridadTests {

    private static final String JSON = """
            {"nombre":"X","descripcion":"d","precio":1.00,"idCategoria":1,"stockReferencial":1}
            """;

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
    void elCatalogoSeLeeSinToken() throws Exception {
        mockMvc.perform(get("/api/productos")).andExpect(status().isOk());
        mockMvc.perform(get("/api/productos/categorias")).andExpect(status().isOk());
    }

    @Test
    void escribirSinTokenDevuelve401() throws Exception {
        mockMvc.perform(post("/api/productos").contentType(MediaType.APPLICATION_JSON).content(JSON))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/productos/1").contentType(MediaType.APPLICATION_JSON).content(JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void escribirConRolNoAdminDevuelve403() throws Exception {
        for (String rol : new String[] {"CLIENTE", "EMPLEADO"}) {
            mockMvc.perform(post("/api/productos").header(HttpHeaders.AUTHORIZATION, token(rol))
                    .contentType(MediaType.APPLICATION_JSON).content(JSON))
                    .andExpect(status().isForbidden());
            mockMvc.perform(put("/api/productos/1").header(HttpHeaders.AUTHORIZATION, token(rol))
                    .contentType(MediaType.APPLICATION_JSON).content(JSON))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void adminSuperaLaSeguridad() throws Exception {
        // La categoria 1 puede no existir: basta con que no sea 401/403.
        int estado = mockMvc.perform(post("/api/productos").header(HttpHeaders.AUTHORIZATION, token("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(JSON))
                .andReturn().getResponse().getStatus();
        org.assertj.core.api.Assertions.assertThat(estado).isNotIn(401, 403);
    }

    @Test
    void tokenFirmadoConOtroSecretoDevuelve401() throws Exception {
        String ajeno = TokensDePrueba.bearer("ADMIN", TokensDePrueba.SECRETO_AJENO, emisor, 1);

        mockMvc.perform(post("/api/productos").header(HttpHeaders.AUTHORIZATION, ajeno)
                .contentType(MediaType.APPLICATION_JSON).content(JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenExpiradoDevuelve401() throws Exception {
        String expirado = TokensDePrueba.bearer("ADMIN", secreto, emisor, -5);

        mockMvc.perform(post("/api/productos").header(HttpHeaders.AUTHORIZATION, expirado)
                .contentType(MediaType.APPLICATION_JSON).content(JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unTokenFalsoEnUnaLecturaPublicaTambienDevuelve401() throws Exception {
        String ajeno = TokensDePrueba.bearer("ADMIN", TokensDePrueba.SECRETO_AJENO, emisor, 1);

        mockMvc.perform(get("/api/productos").header(HttpHeaders.AUTHORIZATION, ajeno))
                .andExpect(status().isUnauthorized());
    }
}
