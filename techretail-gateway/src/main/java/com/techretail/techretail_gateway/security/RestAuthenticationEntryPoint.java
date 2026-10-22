package com.techretail.techretail_gateway.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * Punto de entrada para peticiones sin autenticacion valida. Devuelve 401 en
 * JSON en lugar de la pagina HTML o del desafio HTTP Basic por defecto.
 */
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        ApiErrorWriter.escribir(response, HttpStatus.UNAUTHORIZED,
                "No autenticado: se requiere un token JWT valido");
    }
}
