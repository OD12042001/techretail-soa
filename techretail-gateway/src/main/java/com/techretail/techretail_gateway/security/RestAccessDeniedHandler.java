package com.techretail.techretail_gateway.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Respuesta 403 en JSON cuando el token es valido pero el rol no alcanza para
 * la ruta solicitada.
 */
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        ApiErrorWriter.escribir(response, HttpStatus.FORBIDDEN,
                "Acceso denegado: el rol del token no autoriza esta operacion");
    }
}
