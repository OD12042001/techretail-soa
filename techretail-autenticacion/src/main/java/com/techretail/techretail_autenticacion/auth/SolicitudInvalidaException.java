package com.techretail.techretail_autenticacion.auth;

/**
 * La peticion no se puede procesar como viene: password que BCrypt no puede
 * tratar, cuerpo ilegible, etc. Siempre {@code 400}, nunca un {@code 500} con
 * stack trace.
 */
public class SolicitudInvalidaException extends RuntimeException {

    public SolicitudInvalidaException(String message) {
        super(message);
    }
}
