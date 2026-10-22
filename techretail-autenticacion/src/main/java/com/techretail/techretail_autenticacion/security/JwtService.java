package com.techretail.techretail_autenticacion.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Emisor de los JWT de TechRetail (HS256).
 *
 * <p>El payload sigue el contrato del Avance2 8.2: {@code sub} (usuario),
 * {@code rol}, {@code idUsuario}, {@code iat} y {@code exp}, mas el emisor
 * {@code iss} que el BUS exige. La clave nunca sale de este servicio: el
 * gateway solo la usa para verificar.</p>
 */
@Service
public class JwtService {

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = construirClave(properties.getSecret());
    }

    private static SecretKey construirClave(String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "techretail.security.jwt.secret debe tener al menos 32 bytes para HS256");
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Firma un token para el usuario autenticado.
     */
    public String emitir(String usuario, Rol rol, Long idUsuario) {
        Instant ahora = Instant.now();
        Instant expiracion = ahora.plus(properties.getExpirationMinutes(), ChronoUnit.MINUTES);

        var constructor = Jwts.builder()
                .setSubject(usuario)
                .claim(JwtClaims.ROL, rol.name())
                .setIssuer(properties.getIssuer())
                .setIssuedAt(Date.from(ahora))
                .setExpiration(Date.from(expiracion));
        if (idUsuario != null) {
            constructor.claim(JwtClaims.ID_USUARIO, idUsuario);
        }
        return constructor.signWith(signingKey, SignatureAlgorithm.HS256).compact();
    }

    /**
     * @return los datos del portador si el token es valido; vacio en cualquier
     *         otro caso (firma, expiracion, emisor, rol o formato).
     */
    public Optional<TokenValidado> validar(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .requireIssuer(properties.getIssuer())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            String usuario = claims.getSubject();
            Object rolClaim = claims.get(JwtClaims.ROL);
            Optional<Rol> rol = Rol.desde(rolClaim instanceof String valor ? valor : null);
            if (usuario == null || usuario.isBlank() || rol.isEmpty()) {
                return Optional.empty();
            }

            // Un JWT sin exp vivio para siempre: se rechaza aunque la firma sea valida.
            if (claims.getExpiration() == null) {
                return Optional.empty();
            }

            Long idUsuario = null;
            Object idClaim = claims.get(JwtClaims.ID_USUARIO);
            if (idClaim instanceof Number numero) {
                idUsuario = numero.longValue();
            }

            return Optional.of(new TokenValidado(usuario, rol.get(), idUsuario,
                    claims.getExpiration().toInstant()));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public record TokenValidado(String usuario, Rol rol, Long idUsuario, Instant expiraEn) {
    }
}
