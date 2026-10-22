package com.techretail.techretail_gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.techretail.techretail_gateway.audit.AuditEntry;
import com.techretail.techretail_gateway.audit.AuditFilter;
import com.techretail.techretail_gateway.audit.AuditPublisher;
import com.techretail.techretail_gateway.audit.AuditLogRepository;
import com.techretail.techretail_gateway.audit.ServiceDestinationResolver;
import com.techretail.techretail_gateway.security.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.ServletException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.PathContainer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * Regresiones del endurecimiento posterior a la revision de Fase 1: tokens sin
 * {@code exp}, cabeceras {@code Authorization} duplicadas, cabeceras de
 * seguridad en los rechazos, auditoria de fallos de transporte y la ruta del
 * contrato SOAP.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SeguridadAuditoriaRegressionTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private Environment environment;

    @Test
    void rechazaUnTokenSinExpiracion() throws Exception {
        String token = Jwts.builder()
                .setSubject("maria.fernandez@email.com")
                .claim("rol", "CLIENTE")
                .claim("idUsuario", 8L)
                .setIssuer(jwtProperties.getIssuer())
                .setIssuedAt(new Date())
                .signWith(claveDeFirma(), SignatureAlgorithm.HS256)
                .compact();

        mockMvc.perform(get("/api/clientes").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rechazaDosCabecerasAuthorization() throws Exception {
        String token = firmar("maria.fernandez@email.com", "CLIENTE", 8L);

        mockMvc.perform(get("/api/clientes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void elRechazoTraeCabecerasDeSeguridadYNoSeCachea() throws Exception {
        mockMvc.perform(get("/api/clientes"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
    }

    @Test
    void unFalloDeTransporteSeAuditaComo500() {
        RepositorioEnMemoria repositorio = new RepositorioEnMemoria();
        AuditFilter filtro = new AuditFilter(new AuditPublisher(repositorio, Runnable::run), new ServiceDestinationResolver());
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/api/clientes/caido");
        MockHttpServletResponse respuesta = new MockHttpServletResponse();

        assertThatThrownBy(() -> filtro.doFilter(peticion, respuesta, (req, res) -> {
            throw new ServletException("fallo de transporte simulado");
        })).isInstanceOf(ServletException.class);

        assertThat(repositorio.entradas).hasSize(1);
        assertThat(repositorio.entradas.get(0).estadoRespuesta()).isEqualTo(500);
        assertThat(repositorio.entradas.get(0).servicioDestino()).isEqualTo("techretail-cliente");
    }

    @Test
    void unaTransaccionExitosaConservaSuEstadoReal() throws Exception {
        RepositorioEnMemoria repositorio = new RepositorioEnMemoria();
        AuditFilter filtro = new AuditFilter(new AuditPublisher(repositorio, Runnable::run), new ServiceDestinationResolver());
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/api/clientes/ok");
        MockHttpServletResponse respuesta = new MockHttpServletResponse();

        filtro.doFilter(peticion, respuesta, (req, res) -> ((MockHttpServletResponse) res).setStatus(204));

        assertThat(repositorio.entradas).hasSize(1);
        assertThat(repositorio.entradas.get(0).estadoRespuesta()).isEqualTo(204);
    }

    @Test
    void elContratoSoapYSuWsdlTienenRuta() {
        List<PathPattern> patrones = patronesDeRuta();

        assertThat(patrones)
                .anySatisfy(patron -> assertThat(patron.matches(ruta("/ws/facturacion"))).isTrue());
        assertThat(patrones)
                .anySatisfy(patron -> assertThat(patron.matches(ruta("/ws/facturacion.wsdl"))).isTrue());
    }

    /**
     * Patrones de los {@code Path=} configurados. Se comprueba el matching real
     * del patron y no solo el texto de la propiedad: {@code /ws/facturacion/**}
     * coincide con segmentos hijos, no con el sufijo {@code .wsdl}.
     */
    private List<PathPattern> patronesDeRuta() {
        PathPatternParser parser = new PathPatternParser();
        List<PathPattern> patrones = new ArrayList<>();
        for (int i = 0; ; i++) {
            String valor = environment.getProperty(
                    "spring.cloud.gateway.server.webmvc.routes[" + i + "].predicates[0]");
            if (valor == null) {
                return patrones;
            }
            if (valor.startsWith("Path=")) {
                patrones.add(parser.parse(valor.substring("Path=".length())));
            }
        }
    }

    private static PathContainer ruta(String ruta) {
        return PathContainer.parsePath(ruta);
    }

    private String firmar(String usuario, String rol, Long idUsuario) {
        Instant ahora = Instant.now();
        return Jwts.builder()
                .setSubject(usuario)
                .claim("rol", rol)
                .claim("idUsuario", idUsuario)
                .setIssuer(jwtProperties.getIssuer())
                .setIssuedAt(Date.from(ahora))
                .setExpiration(Date.from(ahora.plus(1, ChronoUnit.MINUTES)))
                .signWith(claveDeFirma(), SignatureAlgorithm.HS256)
                .compact();
    }

    private SecretKey claveDeFirma() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /** Repositorio de auditoria que guarda en memoria para inspeccionar la fila. */
    private static final class RepositorioEnMemoria extends AuditLogRepository {

        private final List<AuditEntry> entradas = new ArrayList<>();

        private RepositorioEnMemoria() {
            super(new JdbcTemplate());
        }

        @Override
        public void registrar(AuditEntry entrada) {
            entradas.add(entrada);
        }
    }
}
