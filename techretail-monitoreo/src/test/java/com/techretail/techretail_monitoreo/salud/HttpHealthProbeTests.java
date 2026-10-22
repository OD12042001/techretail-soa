package com.techretail.techretail_monitoreo.salud;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class HttpHealthProbeTests {

    private HttpServer servidor;

    @AfterEach
    void parar() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    private String levantar(int codigo, String cuerpo) throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/actuator/health", ex -> {
            byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(codigo, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        servidor.start();
        return "http://127.0.0.1:" + servidor.getAddress().getPort() + "/actuator/health";
    }

    @Test
    void respuesta200ConStatusUpEsUp() throws IOException {
        String url = levantar(200, "{\"status\":\"UP\"}");

        ResultadoSonda r = new HttpHealthProbe(2000).sondear(new ServicioObjetivo("x", url));

        assertThat(r.estado()).isEqualTo("UP");
        assertThat(r.tiempoMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void respuesta503EsDown() throws IOException {
        String url = levantar(503, "{\"status\":\"DOWN\"}");

        assertThat(new HttpHealthProbe(2000).sondear(new ServicioObjetivo("x", url)).estado()).isEqualTo("DOWN");
    }

    @Test
    void respuesta200ConStatusDownEsDown() throws IOException {
        String url = levantar(200, "{\"status\":\"DOWN\"}");

        assertThat(new HttpHealthProbe(2000).sondear(new ServicioObjetivo("x", url)).estado()).isEqualTo("DOWN");
    }

    @Test
    void unPuertoCerradoEsDownSinLanzarExcepcion() {
        ResultadoSonda r = new HttpHealthProbe(500)
                .sondear(new ServicioObjetivo("x", "http://127.0.0.1:1/actuator/health"));

        assertThat(r.estado()).isEqualTo("DOWN");
    }
}
