package com.techretail.techretail_autenticacion.usuario;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Catalogo de roles sembrado por la migracion Flyway {@code V1}.
 */
public interface RolRepository extends JpaRepository<RolEntidad, Long> {

    Optional<RolEntidad> findByNombre(String nombre);
}
