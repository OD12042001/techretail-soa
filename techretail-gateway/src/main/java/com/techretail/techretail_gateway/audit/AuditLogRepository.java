package com.techretail.techretail_gateway.audit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Persistencia de la auditoria en {@code db_esb} con JDBC clasico.
 */
@Repository
public class AuditLogRepository {

    private static final String INSERTAR = """
            INSERT INTO auditoria_logs
                (servicio_origen, servicio_destino, operacion, endpoint, estado_respuesta, usuario, detalle)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public AuditLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void registrar(AuditEntry entrada) {
        jdbcTemplate.update(INSERTAR,
                entrada.servicioOrigen(),
                entrada.servicioDestino(),
                entrada.operacion(),
                entrada.endpoint(),
                entrada.estadoRespuesta(),
                entrada.usuario(),
                entrada.detalle());
    }
}
