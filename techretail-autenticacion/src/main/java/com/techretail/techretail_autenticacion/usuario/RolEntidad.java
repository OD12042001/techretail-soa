package com.techretail.techretail_autenticacion.usuario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Fila de la tabla {@code roles} de {@code db_autenticacion}.
 *
 * <p>El catalogo vive en la base de datos (Flyway {@code V1}); el enum
 * {@link com.techretail.techretail_autenticacion.security.Rol} es el catalogo
 * que viaja en el JWT. Ambos comparten los mismos nombres.</p>
 */
@Entity
@Table(name = "roles")
public class RolEntidad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre", nullable = false, unique = true, length = 50)
    private String nombre;

    protected RolEntidad() {
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }
}
