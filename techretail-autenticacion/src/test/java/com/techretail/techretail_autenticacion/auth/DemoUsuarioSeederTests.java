package com.techretail.techretail_autenticacion.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.techretail.techretail_autenticacion.security.JwtService;
import com.techretail.techretail_autenticacion.security.Rol;
import com.techretail.techretail_autenticacion.usuario.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Aprovisionamiento de los usuarios de demostracion: es la unica via de tener
 * un {@code ADMIN} y un {@code EMPLEADO}, porque el registro publico siempre
 * crea {@code CLIENTE}. Base de datos propia para no compartir estado con el
 * resto de la suite.
 */
@SpringBootTest(properties = {
        "techretail.auth.demo-users.enabled=true",
        "spring.datasource.url=jdbc:h2:mem:techretail-auth-demo;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DemoUsuarioSeederTests {

    @Autowired
    private DemoUsuarioSeeder seeder;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Test
    void aprovisionaUnUsuarioPorCadaRolDelCatalogo() {
        assertThat(rolDe("admin@techretail.pe")).isEqualTo(Rol.ADMIN.name());
        assertThat(rolDe("empleado@techretail.pe")).isEqualTo(Rol.EMPLEADO.name());
        assertThat(rolDe("cliente@techretail.pe")).isEqualTo(Rol.CLIENTE.name());

        String passwordGuardado = jdbcTemplate.queryForObject(
                "SELECT password FROM usuarios WHERE usuario = ?", String.class, "admin@techretail.pe");
        assertThat(passwordGuardado).startsWith("$2").isNotEqualTo("Admin123!");
    }

    @Test
    void esIdempotente() {
        long antes = usuarios.count();

        seeder.run(null);

        assertThat(usuarios.count()).isEqualTo(antes);
    }

    @Test
    void elAdministradorDeDemoPuedeIniciarSesionYRecibeSuRol() throws Exception {
        String cuerpo = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"usuario":"admin@techretail.pe","password":"Admin123!"}
                        """))
                .andReturn().getResponse().getContentAsString();

        JsonNode respuesta = objectMapper.readTree(cuerpo);
        assertThat(respuesta.get("rol").asText()).isEqualTo(Rol.ADMIN.name());
        assertThat(jwtService.validar(respuesta.get("token").asText()))
                .hasValueSatisfying(validado -> assertThat(validado.rol()).isEqualTo(Rol.ADMIN));
    }

    private String rolDe(String usuario) {
        return jdbcTemplate.queryForObject("""
                SELECT r.nombre FROM usuarios u JOIN roles r ON r.id = u.id_rol WHERE u.usuario = ?
                """, String.class, usuario);
    }
}
