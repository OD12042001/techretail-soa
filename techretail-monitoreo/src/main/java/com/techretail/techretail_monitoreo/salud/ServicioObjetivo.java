package com.techretail.techretail_monitoreo.salud;

/** Microservicio a vigilar: nombre logico y URL de su endpoint de salud. */
public record ServicioObjetivo(String nombre, String url) {
}
