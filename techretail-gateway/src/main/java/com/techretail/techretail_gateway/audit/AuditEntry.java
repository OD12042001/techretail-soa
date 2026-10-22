package com.techretail.techretail_gateway.audit;

/**
 * Una transaccion registrada en {@code auditoria_logs} (db_esb).
 */
public record AuditEntry(
        String servicioOrigen,
        String servicioDestino,
        String operacion,
        String endpoint,
        int estadoRespuesta,
        String usuario,
        String detalle) {
}
