package com.techretail.techretail_producto.producto;

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
 * Regresion del error dispatch: cuando no hay controlador para la ruta, Tomcat
 * reenvia la peticion a {@code /error} en un dispatch de tipo ERROR. Ese
 * dispatch no atraviesa el filtro JWT (OncePerRequestFilter no filtra ERROR),
 * asi que la cadena de seguridad lo veia como anonimo y respondia 401 en lugar
 * del 404 real.
 *
 * <p>Se levanta un Tomcat real porque MockMvc no ejecuta el error dispatch.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ProductoErrorDispatchTests {

    @Value("${local.server.port}")
    private int puerto;

    @Test
    void unaRutaInexistenteBajoElCatalogoConservaEl404() throws Exception {
        HttpResponse<String> respuesta = get("/api/productos/1/algo");

        assertThat(respuesta.statusCode()).isEqualTo(404);
    }

    private HttpResponse<String> get(String ruta) throws Exception {
        HttpRequest peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .GET()
                .build();
        return HttpClient.newHttpClient().send(peticion, HttpResponse.BodyHandlers.ofString());
    }
}
