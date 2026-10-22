package com.techretail.techretail_monitoreo.salud;

/** Resultado de un sondeo: {@code UP} o {@code DOWN} y la latencia observada. */
public record ResultadoSonda(String estado, int tiempoMs) {
}
