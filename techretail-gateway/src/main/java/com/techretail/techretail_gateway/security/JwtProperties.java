package com.techretail.techretail_gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracion de la validacion JWT centralizada en el BUS.
 *
 * <p>El secreto es compartido con {@code techretail-autenticacion}: ese servicio
 * firma, el BUS solo verifica. Ambos leen la misma variable de entorno
 * {@code JWT_SECRET}.</p>
 */
@ConfigurationProperties(prefix = "techretail.security.jwt")
public class JwtProperties {

    /** Secreto HMAC-SHA256. Debe tener al menos 32 bytes. */
    private String secret;

    /** Emisor esperado en el claim {@code iss}. */
    private String issuer = "techretail-auth";

    /** Tolerancia de reloj, en segundos, al validar {@code exp} e {@code iat}. */
    private long clockSkewSeconds = 0;

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

    public long getClockSkewSeconds() {
        return clockSkewSeconds;
    }

    public void setClockSkewSeconds(long clockSkewSeconds) {
        this.clockSkewSeconds = clockSkewSeconds;
    }
}
