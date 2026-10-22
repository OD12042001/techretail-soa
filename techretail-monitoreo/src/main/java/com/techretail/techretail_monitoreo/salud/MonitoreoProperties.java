package com.techretail.techretail_monitoreo.salud;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuracion bajo {@code techretail.monitoreo.*}: servicios vigilados,
 * intervalo entre ciclos y timeout de cada sondeo.
 */
@ConfigurationProperties("techretail.monitoreo")
public record MonitoreoProperties(
        List<ServicioObjetivo> servicios,
        @DefaultValue("30000") long intervaloMs,
        @DefaultValue("2000") int timeoutMs) {

    public MonitoreoProperties {
        servicios = servicios == null ? List.of() : List.copyOf(servicios);
    }
}
