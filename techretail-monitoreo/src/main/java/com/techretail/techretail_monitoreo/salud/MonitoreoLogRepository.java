package com.techretail.techretail_monitoreo.salud;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class MonitoreoLogRepository {

    private static final RowMapper<MonitoreoLogView> MAPPER = (rs, fila) -> new MonitoreoLogView(
            rs.getLong("id"),
            rs.getString("nombre_servicio"),
            rs.getString("estado"),
            rs.getInt("tiempo_respuesta_ms"),
            rs.getTimestamp("fecha_consulta").toLocalDateTime());

    private static final String COLUMNAS = "id, nombre_servicio, estado, tiempo_respuesta_ms, fecha_consulta";

    private final JdbcTemplate jdbc;

    public MonitoreoLogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void registrar(String servicio, String estado, int tiempoMs) {
        jdbc.update("INSERT INTO monitoreo_logs (nombre_servicio, estado, tiempo_respuesta_ms) VALUES (?, ?, ?)",
                servicio, estado, tiempoMs);
    }

    public List<MonitoreoLogView> recientes(int limite) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM monitoreo_logs ORDER BY fecha_consulta DESC, id DESC LIMIT ?",
                MAPPER, limite);
    }

    /** Ultima lectura de cada servicio. */
    public List<MonitoreoLogView> ultimaPorServicio() {
        return jdbc.query("""
                SELECT %s FROM monitoreo_logs m
                WHERE m.id = (SELECT MAX(x.id) FROM monitoreo_logs x WHERE x.nombre_servicio = m.nombre_servicio)
                ORDER BY nombre_servicio
                """.formatted(COLUMNAS), MAPPER);
    }
}
