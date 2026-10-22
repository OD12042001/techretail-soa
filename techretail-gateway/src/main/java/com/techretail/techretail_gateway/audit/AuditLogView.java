package com.techretail.techretail_gateway.audit;

import java.time.LocalDateTime;

/**
 * Proyeccion de solo lectura de una fila de {@code auditoria_logs}.
 */
public record AuditLogView(
        long id,
        String servicioOrigen,
        String servicioDestino,
        String operacion,
        String endpoint,
        int estadoRespuesta,
        String usuario,
        LocalDateTime timestamp) {
}
