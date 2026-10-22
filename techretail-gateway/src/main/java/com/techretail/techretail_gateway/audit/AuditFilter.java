package com.techretail.techretail_gateway.audit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Registra en {@code db_esb.auditoria_logs} cada transaccion que cruza el BUS,
 * incluidas las rechazadas por seguridad (401/403), para que un intento de
 * bypass quede trazado.
 *
 * <p>Se ejecuta antes de la cadena de Spring Security para poder observar el
 * codigo de respuesta final. Un fallo al escribir la auditoria nunca
 * interrumpe la peticion del cliente: se registra en el log de la aplicacion.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AuditFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuditFilter.class);

    private static final String SERVICIO_ORIGEN = "GATEWAY";
    private static final String USUARIO_ANONIMO = "anonimo";
    private static final int MAX_ENDPOINT = 200;
    private static final int MAX_USUARIO = 100;
    private static final int MAX_DETALLE = 500;

    private final AuditLogRepository repositorio;
    private final ServiceDestinationResolver resolver;

    public AuditFilter(AuditLogRepository repositorio, ServiceDestinationResolver resolver) {
        this.repositorio = repositorio;
        this.resolver = resolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long inicio = System.nanoTime();
        boolean fallo = false;
        try {
            chain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException ex) {
            // La cadena no llego a fijar un estado: la transaccion termino en 500.
            fallo = true;
            throw ex;
        } finally {
            long duracionMs = (System.nanoTime() - inicio) / 1_000_000L;
            registrar(request, response, duracionMs, fallo);
        }
    }

    private void registrar(HttpServletRequest request, HttpServletResponse response, long duracionMs, boolean fallo) {
        String endpoint = truncar(request.getRequestURI(), MAX_ENDPOINT);
        String usuario = truncar(texto(request.getAttribute(AuditContext.USUARIO), USUARIO_ANONIMO), MAX_USUARIO);

        StringBuilder detalle = new StringBuilder()
                .append("rol=").append(texto(request.getAttribute(AuditContext.ROL), "-"))
                .append("; duracion_ms=").append(duracionMs)
                .append("; remitente=").append(request.getRemoteAddr());
        Object motivo = request.getAttribute(AuditContext.MOTIVO_RECHAZO);
        if (motivo != null) {
            detalle.append("; motivo=").append(motivo);
        }

        AuditEntry entrada = new AuditEntry(
                SERVICIO_ORIGEN,
                resolver.resolver(endpoint),
                truncar(request.getMethod(), 10),
                endpoint,
                fallo ? 500 : response.getStatus(),
                usuario,
                truncar(detalle.toString(), MAX_DETALLE));

        try {
            repositorio.registrar(entrada);
        } catch (RuntimeException ex) {
            log.warn("No se pudo registrar la auditoria de {} {}: {}",
                    entrada.operacion(), entrada.endpoint(), ex.getMessage());
        }
    }

    private static String texto(Object valor, String porDefecto) {
        return valor == null ? porDefecto : valor.toString();
    }

    private static String truncar(String valor, int maximo) {
        if (valor == null) {
            return null;
        }
        return valor.length() <= maximo ? valor : valor.substring(0, maximo);
    }
}
