package com.techretail.techretail_autenticacion.auth;

/**
 * Cuerpo de error de negocio del AutenticacionService.
 *
 * <p>Misma forma que {@code com.techretail.techretail_autenticacion.security.ApiErrorWriter},
 * que cubre los rechazos de la cadena de seguridad:
 * {@code {"status":401,"error":"Unauthorized","message":"..."}}. Un solo
 * contrato de error para todo el modulo.</p>
 */
public record ApiError(int status, String error, String message) {
}
