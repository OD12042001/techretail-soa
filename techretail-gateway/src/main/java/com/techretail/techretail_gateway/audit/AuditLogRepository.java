package com.techretail.techretail_gateway.audit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    /** Ultimos eventos, del mas reciente al mas antiguo. */
    public List<AuditLogView> recientes(int limite) {
        return jdbcTemplate.query("""
                SELECT id, servicio_origen, servicio_destino, operacion, endpoint,
                       estado_respuesta, usuario, timestamp
                FROM auditoria_logs
                ORDER BY id DESC
                LIMIT ?
                """,
                (rs, fila) -> new AuditLogView(
                        rs.getLong("id"),
                        rs.getString("servicio_origen"),
                        rs.getString("servicio_destino"),
                        rs.getString("operacion"),
                        rs.getString("endpoint"),
                        rs.getInt("estado_respuesta"),
                        rs.getString("usuario"),
                        rs.getTimestamp("timestamp") == null ? null : rs.getTimestamp("timestamp").toLocalDateTime()),
                limite);
    }

    public long contar() {
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM auditoria_logs", Long.class);
        return total == null ? 0L : total;
    }

    /** Transacciones con estado HTTP 4xx o 5xx. */
    public long contarErrores() {
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auditoria_logs WHERE estado_respuesta >= 400", Long.class);
        return total == null ? 0L : total;
    }

    public Map<String, Long> contarPorServicio() {
        Map<String, Long> resultado = new LinkedHashMap<>();
        jdbcTemplate.query("""
                SELECT servicio_destino, COUNT(*) AS total
                FROM auditoria_logs
                GROUP BY servicio_destino
                ORDER BY total DESC, servicio_destino
                """,
                rs -> {
                    resultado.put(rs.getString("servicio_destino"), rs.getLong("total"));
                });
        return resultado;
    }
}
