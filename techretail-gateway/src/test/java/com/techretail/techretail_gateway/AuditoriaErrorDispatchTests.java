package com.techretail.techretail_gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.techretail.techretail_gateway.security.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Regresion del error dispatch: cuando el controlador responde con
 * {@code sendError}, Tomcat reenvia la peticion a {@code /error} en un dispatch
 * de tipo ERROR. Ese dispatch no atraviesa el filtro JWT (OncePerRequestFilter
 * no filtra ERROR), asi que la cadena de seguridad lo veia como anonimo y lo
 * convertia en 401. Un 400 de validacion debe llegar al cliente como 400.
 *
 * <p>Se levanta un Tomcat real porque MockMvc no ejecuta el error dispatch y,
 * por eso, el test equivalente con MockMvc pasaba en verde.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuditoriaErrorDispatchTests {

    @Value("${local.server.port}")
    private int puerto;

    @Autowired
    private JwtProperties jwtProperties;

    @Test
    void unLimiteInvalidoConservaEl400ATravesDelErrorDispatch() throws Exception {
        HttpResponse<String> respuesta = get("/api/auditoria?limite=0", bearer("ADMIN"));

        assertThat(respuesta.statusCode()).isEqualTo(400);
    }

    @Test
    void unLimiteSobreElMaximoConservaEl400ATravesDelErrorDispatch() throws Exception {
        HttpResponse<String> respuesta = get("/api/auditoria?limite=100000", bearer("ADMIN"));

        assertThat(respuesta.statusCode()).isEqualTo(400);
    }

    private HttpResponse<String> get(String ruta, String autorizacion) throws Exception {
        HttpRequest peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .header("Authorization", autorizacion)
                .GET()
                .build();
        return HttpClient.newHttpClient().send(peticion, HttpResponse.BodyHandlers.ofString());
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
