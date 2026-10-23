package com.techretail.techretail_producto.producto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "productos")
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "id_categoria")
    private Categoria categoria;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precio;

    @Column(name = "stock_referencial", nullable = false)
    private int stockReferencial;

    private boolean disponible;

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion;

    protected Producto() {
    }

    public Producto(Categoria categoria, String nombre, String descripcion, BigDecimal precio,
            int stockReferencial, boolean disponible) {
        this.categoria = categoria;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.precio = precio;
        this.stockReferencial = stockReferencial;
        this.disponible = disponible;
        this.fechaCreacion = LocalDateTime.now();
    }

    public void actualizar(Categoria categoria, String nombre, String descripcion, BigDecimal precio,
            int stockReferencial, boolean disponible) {
        this.categoria = categoria;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.precio = precio;
        this.stockReferencial = stockReferencial;
        this.disponible = disponible;
    }

    public Long getId() {
        return id;
    }

    public Categoria getCategoria() {
        return categoria;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public BigDecimal getPrecio() {
        return precio;
    }

    public int getStockReferencial() {
        return stockReferencial;
    }

    public boolean isDisponible() {
        return disponible;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }
}
