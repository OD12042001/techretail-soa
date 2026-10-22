package com.techretail.techretail_monitoreo.salud;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Dispara los ciclos de health check de forma periodica. */
@Component
@ConditionalOnProperty(name = "techretail.monitoreo.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class HealthCheckScheduler {

    private final HealthCheckService servicio;

    public HealthCheckScheduler(HealthCheckService servicio) {
        this.servicio = servicio;
    }

    @Scheduled(
            fixedDelayString = "${techretail.monitoreo.intervalo-ms:30000}",
            initialDelayString = "${techretail.monitoreo.intervalo-ms:30000}")
    public void ciclo() {
        servicio.ejecutarCiclo();
    }
}
