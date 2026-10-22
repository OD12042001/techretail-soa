package com.techretail.techretail_monitoreo.seguridad;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Valida el token JWT de cada peticion y publica la identidad en el
 * {@code SecurityContext}.
 *
 * <p>Politica fail-closed: si el cliente envia una cabecera
 * {@code Authorization} que no es un Bearer valido, la peticion termina en 401
 * aunque el recurso sea publico. Asi un intento de bypass con un token
 * falsificado nunca continua hacia los controladores.</p>
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIJO_BEARER = "Bearer ";
    private static final String MOTIVO_FORMATO = "esquema de Authorization no soportado";
    private static final String MOTIVO_INVALIDO = "token invalido o expirado";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        List<String> cabeceras = new ArrayList<>();
        request.getHeaders(HttpHeaders.AUTHORIZATION).asIterator().forEachRemaining(cabeceras::add);

        if (cabeceras.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        if (cabeceras.size() > 1) {
            rechazar(request, response, MOTIVO_FORMATO);
            return;
        }

        String cabecera = cabeceras.get(0);
        String token = cabecera.startsWith(PREFIJO_BEARER) ? cabecera.substring(PREFIJO_BEARER.length()).trim() : "";
        if (token.isEmpty()) {
            rechazar(request, response, MOTIVO_FORMATO);
            return;
        }

        Optional<JwtService.TokenValidado> validado = jwtService.validar(token);
        if (validado.isEmpty()) {
            rechazar(request, response, MOTIVO_INVALIDO);
            return;
        }

        JwtService.TokenValidado portador = validado.get();
        List<SimpleGrantedAuthority> autoridades = List.of(new SimpleGrantedAuthority("ROLE_" + portador.rol().name()));
        UsernamePasswordAuthenticationToken autenticacion =
                new UsernamePasswordAuthenticationToken(portador.usuario(), null, autoridades);
        autenticacion.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(autenticacion);


        chain.doFilter(request, response);
    }

    private void rechazar(HttpServletRequest request, HttpServletResponse response, String motivo) throws IOException {
        SecurityContextHolder.clearContext();
        ApiErrorWriter.escribir(response, HttpStatus.UNAUTHORIZED, motivo);
    }
}
