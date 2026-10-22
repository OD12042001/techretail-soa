package com.techretail.techretail_autenticacion.usuario;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso a {@code usuarios}. Las consultas se hacen por el usuario ya
 * normalizado (minusculas, sin espacios) para que dos altas que solo difieren
 * en capitalizacion no creen cuentas distintas.
 */
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByUsuario(String usuario);

    boolean existsByUsuario(String usuario);
}
