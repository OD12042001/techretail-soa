package com.techretail.techretail_autenticacion.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /api/auth/registro} (Avance2 3.1: {@code {usuario, password}}).
 *
 * <p>No existe un campo {@code rol} a proposito: el rol de un alta publica lo
 * decide el servicio, nunca el cliente. Un {@code rol} enviado en el JSON se
 * ignora.</p>
 */
public record RegistroRequest(

        @NotBlank(message = "es obligatorio")
        @Size(max = 120, message = "no puede superar los 120 caracteres")
        String usuario,

        @NotBlank(message = "es obligatorio")
        @Size(min = 8, max = 72, message = "debe tener entre 8 y 72 caracteres")
        String password,

        @Size(max = 120, message = "no puede superar los 120 caracteres")
        String nombre) {
}
