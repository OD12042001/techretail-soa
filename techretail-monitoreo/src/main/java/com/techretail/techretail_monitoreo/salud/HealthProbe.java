package com.techretail.techretail_monitoreo.salud;

/** Estrategia de sondeo de un servicio. Nunca debe lanzar por caidas de red. */
public interface HealthProbe {

    ResultadoSonda sondear(ServicioObjetivo servicio);
}
