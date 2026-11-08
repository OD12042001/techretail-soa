package com.techretail.techretail_monitoreo.salud;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
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
class MonitoreoErrorDispatchTests {

    @Value("${local.server.port}")
    private int puerto;

    @Value("${techretail.security.jwt.secret}")
    private String secreto;

    @Value("${techretail.security.jwt.issuer}")
    private String emisor;

    @Test
    void unLimiteInvalidoConservaEl400ATravesDelErrorDispatch() throws Exception {
        HttpResponse<String> respuesta = get("/api/monitoreo/historial?limite=0",
                TokensDePrueba.bearer("ADMIN", secreto, emisor, 1));

        assertThat(respuesta.statusCode()).isEqualTo(400);
    }

    @Test
    void unLimiteSobreElMaximoConservaEl400ATravesDelErrorDispatch() throws Exception {
        HttpResponse<String> respuesta = get("/api/monitoreo/historial?limite=100000",
                TokensDePrueba.bearer("ADMIN", secreto, emisor, 1));

        assertThat(respuesta.statusCode()).isEqualTo(400);
    }

    private HttpResponse<String> get(String ruta, String autorizacion) throws Exception {
        HttpRequest peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .header("Authorization", autorizacion)
                .GET()
                .build();
        return HttpClient.newHttpClient().send(peticion, HttpResponse.BodyHandlers.ofString());
    }
}
