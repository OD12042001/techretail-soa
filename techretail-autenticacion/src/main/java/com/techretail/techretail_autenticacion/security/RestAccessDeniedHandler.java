package com.techretail.techretail_autenticacion.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Respuesta 403 en JSON cuando el rol del portador no alcanza para la
 * operacion solicitada.
 */
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        ApiErrorWriter.escribir(response, HttpStatus.FORBIDDEN,
                "Acceso denegado: el rol del token no autoriza esta operacion");
    }
}
