package com.techretail.techretail_autenticacion.usuario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Fila de la tabla {@code usuarios} de {@code db_autenticacion}.
 *
 * <p>El esquema lo crea Flyway ({@code V1__create_tables.sql}) y Hibernate solo
 * lo valida ({@code ddl-auto=validate}), asi que cada columna mapeada tiene que
 * existir tal cual. {@code fecha_registro} no se mapea: la pone el valor por
 * defecto de la base de datos.</p>
 */
@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario", nullable = false, unique = true, length = 120)
    private String usuario;

    /** Hash BCrypt; nunca la contrasena en claro. */
    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_rol")
    private RolEntidad rol;

    @Column(name = "nombre", length = 120)
    private String nombre;

    @Column(name = "estado", length = 10)
    private String estado = "ACTIVO";

    public Long getId() {
        return id;
    }

    public String getUsuario() {
        return usuario;
    }

    public void setUsuario(String usuario) {
        this.usuario = usuario;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public RolEntidad getRol() {
        return rol;
    }

    public void setRol(RolEntidad rol) {
        this.rol = rol;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }
}
