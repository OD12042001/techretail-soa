package com.techretail.techretail_producto.seguridad;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Verificacion de JWT en el BUS. Nunca emite tokens: el unico emisor es
 * {@code techretail-autenticacion}.
 *
 * <p>jjwt valida firma y expiracion; ademas se exige el emisor esperado y un
 * rol del catalogo {@link Rol}. Un token con firma invalida, algoritmo
 * distinto de HS256, {@code iss} ajeno, expirado o con un rol desconocido se
 * descarta: el portador no queda autenticado.</p>
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
     * @return los datos del portador si el token es valido; vacio en cualquier
     *         otro caso (firma, expiracion, emisor, rol o formato).
     */
    public Optional<TokenValidado> validar(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .requireIssuer(properties.getIssuer())
                    .setAllowedClockSkewSeconds(properties.getClockSkewSeconds())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            String usuario = claims.getSubject();
            if (usuario == null || usuario.isBlank()) {
                return Optional.empty();
            }

            // Un JWT sin exp vivio para siempre: se rechaza aunque la firma sea valida.
            if (claims.getExpiration() == null) {
                return Optional.empty();
            }

            Object rolClaim = claims.get(JwtClaims.ROL);
            Optional<Rol> rol = Rol.desde(rolClaim instanceof String valor ? valor : null);
            if (rol.isEmpty()) {
                return Optional.empty();
            }

            Long idUsuario = null;
            Object idClaim = claims.get(JwtClaims.ID_USUARIO);
            if (idClaim instanceof Number numero) {
                idUsuario = numero.longValue();
            }

            return Optional.of(new TokenValidado(usuario, rol.get(), idUsuario));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public record TokenValidado(String usuario, Rol rol, Long idUsuario) {
    }
}
