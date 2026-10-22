package com.techretail.techretail_monitoreo.salud;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/** Fabrica de JWT firmados con HS256 para las pruebas de seguridad. */
final class TokensDePrueba {

    static final String SECRETO_AJENO = "secreto-ajeno-de-pruebas-con-mas-de-32-bytes-para-hs256";

    private TokensDePrueba() {
    }

    static String bearer(String rol, String secreto, String emisor, long minutos) {
        Instant ahora = Instant.now();
        return "Bearer " + Jwts.builder()
                .setSubject("usuario@techretail.pe")
                .claim("rol", rol)
                .claim("idUsuario", 1L)
                .setIssuer(emisor)
                .setIssuedAt(Date.from(ahora))
                .setExpiration(Date.from(ahora.plus(minutos, ChronoUnit.MINUTES)))
                .signWith(Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();
    }
}
