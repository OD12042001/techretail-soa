package com.techretail.techretail_gateway.security;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/**
 * Respuestas de error de seguridad en JSON.
 *
 * <p>Los mensajes son constantes del codigo: nunca se reenvia al cliente el
 * motivo tecnico de un fallo de seguridad ni datos del token.</p>
 */
final class ApiErrorWriter {

    private ApiErrorWriter() {
    }

    static void escribir(HttpServletResponse response, HttpStatus status, String mensaje) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.getWriter().write("{\"status\":%d,\"error\":\"%s\",\"message\":\"%s\"}"
                .formatted(status.value(), status.getReasonPhrase(), mensaje));
    }
}
