package com.techretail.techretail_autenticacion.auth;

/**
 * Credenciales rechazadas. El mensaje es siempre el mismo para "usuario
 * inexistente", "password incorrecta" y "usuario inactivo": si variara, el
 * endpoint serviria para enumerar cuentas.
 */
public class CredencialesInvalidasException extends RuntimeException {

    public CredencialesInvalidasException() {
        super("Credenciales invalidas");
    }
}
