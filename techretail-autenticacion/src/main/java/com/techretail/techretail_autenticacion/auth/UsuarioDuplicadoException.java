package com.techretail.techretail_autenticacion.auth;

/**
 * El {@code usuario} ya existe en {@code db_autenticacion.usuarios}.
 */
public class UsuarioDuplicadoException extends RuntimeException {

    public UsuarioDuplicadoException() {
        super("El usuario ya esta registrado");
    }
}
