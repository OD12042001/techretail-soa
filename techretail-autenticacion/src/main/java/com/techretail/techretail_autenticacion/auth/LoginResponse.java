package com.techretail.techretail_autenticacion.auth;

/**
 * Salida de {@code POST /api/auth/login}: el contrato del Avance2 4.1.8.
 *
 * <pre>
 * {"token": "...", "tipo": "Bearer", "expiraEn": 3600, "rol": "CLIENTE",
 *  "idUsuario": 8, "nombre": "Maria Fernandez"}
 * </pre>
 *
 * <p>{@code expiraEn} son segundos de vigencia, nunca la fecha absoluta: la
 * fecha exacta viaja dentro del claim {@code exp} del token.</p>
 */
public record LoginResponse(

        String token,

        String tipo,

        long expiraEn,

        String rol,

        Long idUsuario,

        String nombre) {
}
