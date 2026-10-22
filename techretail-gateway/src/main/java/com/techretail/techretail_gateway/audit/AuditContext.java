package com.techretail.techretail_gateway.audit;

/**
 * Atributos que viajan en la peticion desde la capa de seguridad hacia la
 * auditoria. Se usan atributos (y no {@code SecurityContextHolder}) porque el
 * filtro de auditoria envuelve a la cadena de Spring Security y el contexto ya
 * fue limpiado cuando la respuesta regresa.
 */
public final class AuditContext {

    public static final String USUARIO = "techretail.audit.usuario";
    public static final String ROL = "techretail.audit.rol";
    public static final String MOTIVO_RECHAZO = "techretail.audit.motivo";

    private AuditContext() {
    }
}
