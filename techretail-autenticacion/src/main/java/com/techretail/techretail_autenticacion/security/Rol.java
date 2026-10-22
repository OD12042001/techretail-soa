package com.techretail.techretail_autenticacion.security;

import java.util.Locale;
import java.util.Optional;

/**
 * Roles de negocio de TechRetail (tabla {@code roles} de db_autenticacion).
 */
public enum Rol {

    ADMIN,
    EMPLEADO,
    CLIENTE;

    /**
     * Normaliza un rol almacenado en base de datos. Acepta el valor con o sin
     * el prefijo {@code ROLE_} y sin distinguir mayusculas.
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
