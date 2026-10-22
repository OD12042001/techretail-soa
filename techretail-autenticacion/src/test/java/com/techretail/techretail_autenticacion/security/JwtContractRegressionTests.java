package com.techretail.techretail_autenticacion.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Regresiones del contrato JWT y de las respuestas de error del
 * AutenticacionService: un token sin {@code exp} jamas se acepta y los rechazos
 * salen con cabeceras que impiden cachear o interpretar el cuerpo como otro
 * tipo de contenido.
 */
class JwtContractRegressionTests {

    private static final String SECRETO = "secreto-de-pruebas-techretail-con-mas-de-32-bytes";
    private static final String EMISOR = "techretail-auth";

    @Test
    void rechazaUnTokenSinExpiracion() {
        JwtService servicio = new JwtService(propiedades(SECRETO));
        String token = Jwts.builder()
                .setSubject("maria.fernandez@email.com")
                .claim(JwtClaims.ROL, Rol.CLIENTE.name())
                .claim(JwtClaims.ID_USUARIO, 8L)
                .setIssuer(EMISOR)
                .setIssuedAt(new Date())
                .signWith(clave(SECRETO), SignatureAlgorithm.HS256)
                .compact();

        assertThat(servicio.validar(token)).isEmpty();
    }

    @Test
    void elTokenEmitidoSiempreDeclaraSuExpiracion() {
        JwtService servicio = new JwtService(propiedades(SECRETO));

        String token = servicio.emitir("maria.fernandez@email.com", Rol.CLIENTE, 8L);

        Claims claims = Jwts.parserBuilder()
                .setSigningKey(clave(SECRETO))
                .build()
                .parseClaimsJws(token)
                .getBody();
        assertThat(claims.getExpiration()).isNotNull();
        assertThat(claims.getExpiration()).isAfter(new Date());
        assertThat(claims.getIssuer()).isEqualTo(EMISOR);
    }

    @Test
    void lasRespuestasDeErrorNoSonCacheablesNiSniffeables() throws IOException {
        MockHttpServletResponse respuesta = new MockHttpServletResponse();

        ApiErrorWriter.escribir(respuesta, HttpStatus.UNAUTHORIZED, "token invalido o expirado");

        assertThat(respuesta.getStatus()).isEqualTo(401);
        assertThat(respuesta.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(respuesta.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(respuesta.getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        assertThat(respuesta.getContentAsString()).contains("\"status\":401");
    }

    private static JwtProperties propiedades(String secreto) {
        JwtProperties propiedades = new JwtProperties();
        propiedades.setSecret(secreto);
        propiedades.setIssuer(EMISOR);
        propiedades.setExpirationMinutes(60);
        return propiedades;
    }

    private static SecretKey clave(String secreto) {
        return Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
    }
}
