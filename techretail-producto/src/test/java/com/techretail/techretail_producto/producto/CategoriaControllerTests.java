package com.techretail.techretail_producto.producto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Contrato web de POST /api/productos/categorias: alta de categorias solo para ADMIN. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CategoriaControllerTests {

    private static final String CUERPO = """
            {"nombre":"Audio","descripcion":"Parlantes y audifonos"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Value("${techretail.security.jwt.secret}")
    private String secreto;

    @Value("${techretail.security.jwt.issuer}")
    private String emisor;

    @BeforeEach
    void limpiar() {
        jdbc.update("DELETE FROM productos");
        jdbc.update("DELETE FROM categorias");
    }

    private String token(String rol) {
        return TokensDePrueba.bearer(rol, secreto, emisor, 1);
    }

    private int categorias() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM categorias", Integer.class);
    }

    @Test
    void adminCreaCategoriaYDevuelve201() throws Exception {
        mockMvc.perform(post("/api/productos/categorias")
                .header(HttpHeaders.AUTHORIZATION, token("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(CUERPO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.nombre").value("Audio"))
                .andExpect(jsonPath("$.descripcion").value("Parlantes y audifonos"));

        assertThat(categorias()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT descripcion FROM categorias WHERE nombre = 'Audio'", String.class))
                .isEqualTo("Parlantes y audifonos");
    }

    @Test
    void crearCategoriaSinTokenDevuelve401() throws Exception {
        mockMvc.perform(post("/api/productos/categorias")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CUERPO))
                .andExpect(status().isUnauthorized());

        assertThat(categorias()).isZero();
    }

    @Test
    void crearCategoriaConRolNoAdminDevuelve403() throws Exception {
        for (String rol : new String[] {"CLIENTE", "EMPLEADO"}) {
            mockMvc.perform(post("/api/productos/categorias")
                    .header(HttpHeaders.AUTHORIZATION, token(rol))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(CUERPO))
                    .andExpect(status().isForbidden());
        }

        assertThat(categorias()).isZero();
    }

    @Test
    void crearCategoriaConNombreVacioDevuelve400() throws Exception {
        mockMvc.perform(post("/api/productos/categorias")
                .header(HttpHeaders.AUTHORIZATION, token("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nombre":"","descripcion":"sin nombre"}
                        """))
                .andExpect(status().isBadRequest());

        assertThat(categorias()).isZero();
    }
}
