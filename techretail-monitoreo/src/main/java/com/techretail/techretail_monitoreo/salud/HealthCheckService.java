package com.techretail.techretail_monitoreo.salud;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Un ciclo sondea cada servicio y persiste su estado; un fallo no corta el ciclo. */
@Service
public class HealthCheckService {

    private static final Logger log = LoggerFactory.getLogger(HealthCheckService.class);

    private final MonitoreoProperties propiedades;
    private final HealthProbe sonda;
    private final MonitoreoLogRepository repositorio;

    public HealthCheckService(MonitoreoProperties propiedades, HealthProbe sonda, MonitoreoLogRepository repositorio) {
        this.propiedades = propiedades;
        this.sonda = sonda;
        this.repositorio = repositorio;
    }

    public void ejecutarCiclo() {
        for (ServicioObjetivo servicio : propiedades.servicios()) {
            ResultadoSonda resultado;
            try {
                resultado = sonda.sondear(servicio);
            } catch (RuntimeException ex) {
                log.warn("Sonda fallida para {}: {}", servicio.nombre(), ex.getMessage());
                resultado = new ResultadoSonda("DOWN", 0);
            }
            try {
                repositorio.registrar(servicio.nombre(), resultado.estado(), resultado.tiempoMs());
            } catch (RuntimeException ex) {
                log.warn("No se pudo registrar el estado de {}: {}", servicio.nombre(), ex.getMessage());
            }
        }
    }
}
