package com.techretail.techretail_autenticacion.auth;

/**
 * Salida de {@code POST /api/auth/registro} ({@code 201 Created}).
 *
 * <p>No incluye token: el alta no autentica. Para obtener credenciales se pasa
 * despues por {@code POST /api/auth/login}.</p>
 */
public record RegistroResponse(

        Long idUsuario,

        String usuario,

        String nombre,

        String rol,

        String estado) {
}
