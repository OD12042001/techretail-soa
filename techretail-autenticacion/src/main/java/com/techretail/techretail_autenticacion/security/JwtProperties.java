package com.techretail.techretail_autenticacion.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracion de emision y validacion de JWT.
 *
 * <p>El secreto es compartido con {@code techretail-gateway}: este servicio
 * firma, el BUS verifica. Ambos leen la misma variable de entorno
 * {@code JWT_SECRET}.</p>
 */
@ConfigurationProperties(prefix = "techretail.security.jwt")
public class JwtProperties {

    /** Secreto HMAC-SHA256. Debe tener al menos 32 bytes. */
    private String secret;

    /** Emisor que se incluye en el claim {@code iss}. */
    private String issuer = "techretail-auth";

    /** Vigencia del token en minutos. */
    private long expirationMinutes = 60;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public long getExpirationMinutes() {
        return expirationMinutes;
    }

    public void setExpirationMinutes(long expirationMinutes) {
        this.expirationMinutes = expirationMinutes;
    }
}
