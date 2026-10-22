package com.techretail.techretail_autenticacion.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * Cuerpo de {@code POST /api/auth/login} (Avance2 3.1: {@code {usuario, password}}).
 *
 * <p>Sin {@code @Size}: el login no debe revelar la politica de contrasenas ni
 * distinguir un password corto de uno incorrecto. Todo lo que no coincida es un
 * {@code 401} generico.</p>
 */
public record LoginRequest(

        @NotBlank(message = "es obligatorio")
        String usuario,

        @NotBlank(message = "es obligatorio")
        String password) {
}
