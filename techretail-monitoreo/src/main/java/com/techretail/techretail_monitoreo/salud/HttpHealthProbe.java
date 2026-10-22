package com.techretail.techretail_monitoreo.salud;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Sondea {@code GET <url>} y considera UP solo una respuesta 200 cuyo cuerpo
 * declare {@code "status":"UP"}. Cualquier otro resultado (codigo, timeout,
 * conexion rechazada) es DOWN.
 */
public class HttpHealthProbe implements HealthProbe {

    private final HttpClient cliente;
    private final Duration timeout;

    public HttpHealthProbe(int timeoutMs) {
        this.timeout = Duration.ofMillis(timeoutMs);
        this.cliente = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    @Override
    public ResultadoSonda sondear(ServicioObjetivo servicio) {
        long inicio = System.nanoTime();
        String estado = "DOWN";
        try {
            HttpRequest peticion = HttpRequest.newBuilder(URI.create(servicio.url()))
                    .timeout(timeout).GET().build();
            HttpResponse<String> respuesta = cliente.send(peticion, HttpResponse.BodyHandlers.ofString());
            if (respuesta.statusCode() == 200 && respuesta.body().replace(" ", "").contains("\"status\":\"UP\"")) {
                estado = "UP";
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            // Cualquier fallo de red o URL invalida es DOWN.
        }
        return new ResultadoSonda(estado, (int) ((System.nanoTime() - inicio) / 1_000_000L));
    }
}
