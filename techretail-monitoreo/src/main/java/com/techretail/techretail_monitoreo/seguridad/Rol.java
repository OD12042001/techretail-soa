package com.techretail.techretail_monitoreo.seguridad;

import java.util.Locale;
import java.util.Optional;

/**
 * Roles de negocio de TechRetail. Un token solo es valido si su claim
 * {@code rol} pertenece a este conjunto: cualquier otro valor se rechaza para
 * que un fallo del emisor no se traduzca en un rol arbitrario dentro del BUS.
 */
public enum Rol {

    ADMIN,
    EMPLEADO,
    CLIENTE;

    /**
     * Normaliza el claim {@code rol}. Acepta el valor con o sin el prefijo
     * {@code ROLE_} y sin distinguir mayusculas.
     */
    public static Optional<Rol> desde(String valor) {
        if (valor == null || valor.isBlank()) {
            return Optional.empty();
        }
        String normalizado = valor.trim().toUpperCase(Locale.ROOT);
        if (normalizado.startsWith("ROLE_")) {
            normalizado = normalizado.substring("ROLE_".length());
        }
        for (Rol rol : values()) {
            if (rol.name().equals(normalizado)) {
                return Optional.of(rol);
            }
        }
        return Optional.empty();
    }
}
