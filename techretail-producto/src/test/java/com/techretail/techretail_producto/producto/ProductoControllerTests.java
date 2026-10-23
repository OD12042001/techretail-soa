package com.techretail.techretail_producto.producto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

/** Contrato web de /api/productos (Avance2, seccion 4). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductoControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Value("${techretail.security.jwt.secret}")
    private String secreto;

    @Value("${techretail.security.jwt.issuer}")
    private String emisor;

    private long idLaptops;
    private long idAudio;

    @BeforeEach
    void datos() {
        jdbc.update("DELETE FROM productos");
        jdbc.update("DELETE FROM categorias");
        idLaptops = categoria("Laptops");
        idAudio = categoria("Audio");
    }

    private long categoria(String nombre) {
        jdbc.update("INSERT INTO categorias (nombre, descripcion) VALUES (?, 'desc')", nombre);
        return jdbc.queryForObject("SELECT id FROM categorias WHERE nombre = ?", Long.class, nombre);
    }

    private long producto(long idCategoria, String nombre, String precio, boolean disponible) {
        jdbc.update("""
                INSERT INTO productos (id_categoria, nombre, descripcion, precio, stock_referencial, disponible)
                VALUES (?, ?, 'd', ?, 10, ?)
                """, idCategoria, nombre, new java.math.BigDecimal(precio), disponible);
        return jdbc.queryForObject("SELECT id FROM productos WHERE nombre = ?", Long.class, nombre);
    }

    private String admin() {
        return TokensDePrueba.bearer("ADMIN", secreto, emisor, 1);
    }

    private String cuerpo(String nombre, String precio, Long idCategoria, String stock) {
        return """
                {"nombre":%s,"descripcion":"Equipo","precio":%s,"idCategoria":%s,"stockReferencial":%s}
                """.formatted(nombre == null ? "\"\"" : "\"" + nombre + "\"", precio, idCategoria, stock);
    }

    // ---- listar -----------------------------------------------------------------

    @Test
    void listarSinDatosDevuelveListaVacia() throws Exception {
        mockMvc.perform(get("/api/productos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void listarDevuelveLosProductosConSuCategoria() throws Exception {
        producto(idLaptops, "Laptop Pro", "3500.00", true);
        producto(idAudio, "Audifonos X", "199.90", true);

        mockMvc.perform(get("/api/productos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.nombre=='Laptop Pro')].categoria").value("Laptops"))
                .andExpect(jsonPath("$[?(@.nombre=='Audifonos X')].precio").value(199.90));
    }

    @Test
    void listarFiltraPorCategoria() throws Exception {
        producto(idLaptops, "Laptop Pro", "3500.00", true);
        producto(idAudio, "Audifonos X", "199.90", true);

        mockMvc.perform(get("/api/productos?categoria=" + idAudio))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Audifonos X"));
    }

    @Test
    void listarBuscaPorNombreSinDistinguirMayusculas() throws Exception {
        producto(idLaptops, "Laptop Pro", "3500.00", true);
        producto(idAudio, "Audifonos X", "199.90", true);

        mockMvc.perform(get("/api/productos?q=LAPTOP"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Laptop Pro"));
    }

    @Test
    void listarSoloDisponiblesOcultaLosRetirados() throws Exception {
        producto(idLaptops, "Laptop Pro", "3500.00", true);
        producto(idLaptops, "Laptop Vieja", "900.00", false);

        mockMvc.perform(get("/api/productos?soloDisponibles=true"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Laptop Pro"));
        mockMvc.perform(get("/api/productos"))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void listarCategorias() throws Exception {
        mockMvc.perform(get("/api/productos/categorias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].nombre").exists());
    }

    // ---- obtener ----------------------------------------------------------------

    @Test
    void obtenerPorIdDevuelveElProducto() throws Exception {
        long id = producto(idLaptops, "Laptop Pro", "3500.00", true);

        mockMvc.perform(get("/api/productos/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.idCategoria").value(idLaptops))
                .andExpect(jsonPath("$.stockReferencial").value(10))
                .andExpect(jsonPath("$.disponible").value(true))
                .andExpect(jsonPath("$.fechaCreacion").exists());
    }

    @Test
    void obtenerInexistenteDevuelve404ConCuerpoDeError() throws Exception {
        mockMvc.perform(get("/api/productos/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").exists());
    }

    // ---- crear ------------------------------------------------------------------

    @Test
    void crearDevuelve201ConLocationYPersiste() throws Exception {
        String respuesta = mockMvc.perform(post("/api/productos")
                .header(HttpHeaders.AUTHORIZATION, admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("Mouse Gamer", "89.90", idAudio, "25")))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.nombre").value("Mouse Gamer"))
                .andExpect(jsonPath("$.disponible").value(true))
                .andReturn().getResponse().getContentAsString();

        assertThat(respuesta).contains("\"id\"");
        Integer filas = jdbc.queryForObject(
                "SELECT COUNT(*) FROM productos WHERE nombre = 'Mouse Gamer' AND stock_referencial = 25", Integer.class);
        assertThat(filas).isEqualTo(1);
    }

    @Test
    void crearConNombreVacioDevuelve400() throws Exception {
        crearInvalido(cuerpo(null, "10.00", idAudio, "1"));
    }

    @Test
    void crearConPrecioCeroDevuelve400() throws Exception {
        crearInvalido(cuerpo("Cable", "0", idAudio, "1"));
    }

    @Test
    void crearConPrecioConTresDecimalesDevuelve400() throws Exception {
        crearInvalido(cuerpo("Cable", "10.999", idAudio, "1"));
    }

    @Test
    void crearConStockNegativoDevuelve400() throws Exception {
        crearInvalido(cuerpo("Cable", "10.00", idAudio, "-1"));
    }

    @Test
    void crearSinCategoriaDevuelve400() throws Exception {
        crearInvalido(cuerpo("Cable", "10.00", null, "1"));
    }

    private void crearInvalido(String json) throws Exception {
        mockMvc.perform(post("/api/productos")
                .header(HttpHeaders.AUTHORIZATION, admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM productos", Integer.class)).isZero();
    }

    @Test
    void crearConCategoriaInexistenteDevuelve404() throws Exception {
        mockMvc.perform(post("/api/productos")
                .header(HttpHeaders.AUTHORIZATION, admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("Cable", "10.00", 999999L, "1")))
                .andExpect(status().isNotFound());
    }

    // ---- actualizar -------------------------------------------------------------

    @Test
    void actualizarCambiaLosDatosYPersiste() throws Exception {
        long id = producto(idLaptops, "Laptop Pro", "3500.00", true);

        mockMvc.perform(put("/api/productos/" + id)
                .header(HttpHeaders.AUTHORIZATION, admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nombre":"Laptop Pro 2","descripcion":"nueva","precio":3300.50,"idCategoria":%d,
                         "stockReferencial":4,"disponible":false}
                        """.formatted(idAudio)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Laptop Pro 2"))
                .andExpect(jsonPath("$.disponible").value(false));

        assertThat(jdbc.queryForObject("SELECT precio FROM productos WHERE id = ?",
                java.math.BigDecimal.class, id)).isEqualByComparingTo("3300.50");
        assertThat(jdbc.queryForObject("SELECT id_categoria FROM productos WHERE id = ?", Long.class, id))
                .isEqualTo(idAudio);
    }

    @Test
    void actualizarInexistenteDevuelve404() throws Exception {
        mockMvc.perform(put("/api/productos/999999")
                .header(HttpHeaders.AUTHORIZATION, admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("Cable", "10.00", idAudio, "1")))
                .andExpect(status().isNotFound());
    }

    @Test
    void actualizarConDatosInvalidosDevuelve400() throws Exception {
        long id = producto(idLaptops, "Laptop Pro", "3500.00", true);

        mockMvc.perform(put("/api/productos/" + id)
                .header(HttpHeaders.AUTHORIZATION, admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("Laptop Pro", "-5", idLaptops, "1")))
                .andExpect(status().isBadRequest());
    }
}
