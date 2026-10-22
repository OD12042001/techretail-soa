package com.techretail.techretail_autenticacion.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Contrato del JWT emitido por el AutenticacionService: claims, firma,
 * expiracion y emisor. Es la base que el BUS replica al validar.
 */
class JwtServiceTests {

    private static final String SECRETO = "secreto-de-pruebas-techretail-con-mas-de-32-bytes";
    private static final String EMISOR = "techretail-auth";

    @Test
    void emiteUnTokenQueSePuedeValidar() {
        JwtService servicio = new JwtService(propiedades(SECRETO, 60));

        String token = servicio.emitir("maria.fernandez@email.com", Rol.CLIENTE, 8L);

        assertThat(servicio.validar(token)).hasValueSatisfying(validado -> {
            assertThat(validado.usuario()).isEqualTo("maria.fernandez@email.com");
            assertThat(validado.rol()).isEqualTo(Rol.CLIENTE);
            assertThat(validado.idUsuario()).isEqualTo(8L);
            assertThat(validado.expiraEn()).isAfter(java.time.Instant.now());
        });
    }

    @Test
    void rechazaUnTokenFirmadoConOtroSecreto() {
        JwtService emisorAjeno = new JwtService(propiedades(
                "otro-secreto-de-pruebas-con-mas-de-32-bytes-para-hs256", 60));
        JwtService validador = new JwtService(propiedades(SECRETO, 60));

        String token = emisorAjeno.emitir("intruso", Rol.ADMIN, 1L);

        assertThat(validador.validar(token)).isEmpty();
    }

    @Test
    void rechazaUnTokenExpirado() {
        JwtService servicio = new JwtService(propiedades(SECRETO, -5));

        String token = servicio.emitir("maria.fernandez@email.com", Rol.CLIENTE, 8L);

        assertThat(servicio.validar(token)).isEmpty();
    }

    @Test
    void rechazaUnEmisorDesconocido() {
        JwtProperties propiedades = propiedades(SECRETO, 60);
        propiedades.setIssuer("otro-emisor");
        String token = new JwtService(propiedades).emitir("maria.fernandez@email.com", Rol.CLIENTE, 8L);

        assertThat(new JwtService(propiedades(SECRETO, 60)).validar(token)).isEmpty();
    }

    @Test
    void rechazaUnRolFueraDelCatalogo() {
        JwtService servicio = new JwtService(propiedades(SECRETO, 60));

        assertThat(Rol.desde("SUPERUSER")).isEmpty();
        assertThat(Rol.desde("role_admin")).contains(Rol.ADMIN);
    }

    private static JwtProperties propiedades(String secreto, long minutos) {
        JwtProperties propiedades = new JwtProperties();
        propiedades.setSecret(secreto);
        propiedades.setIssuer(EMISOR);
        propiedades.setExpirationMinutes(minutos);
        return propiedades;
    }
}
