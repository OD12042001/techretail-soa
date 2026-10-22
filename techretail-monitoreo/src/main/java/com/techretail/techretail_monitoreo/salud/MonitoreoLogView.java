package com.techretail.techretail_monitoreo.salud;

import java.time.LocalDateTime;

/** Fila de {@code monitoreo_logs} expuesta por la API. */
public record MonitoreoLogView(
        long id,
        String servicio,
        String estado,
        int tiempoRespuestaMs,
        LocalDateTime fechaConsulta) {
}
