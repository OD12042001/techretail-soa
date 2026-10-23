package com.techretail.techretail_producto.producto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductoResponse(
        Long id,
        String nombre,
        String descripcion,
        BigDecimal precio,
        Long idCategoria,
        String categoria,
        int stockReferencial,
        boolean disponible,
        LocalDateTime fechaCreacion) {

    static ProductoResponse desde(Producto p) {
        Categoria c = p.getCategoria();
        return new ProductoResponse(p.getId(), p.getNombre(), p.getDescripcion(), p.getPrecio(),
                c == null ? null : c.getId(), c == null ? null : c.getNombre(),
                p.getStockReferencial(), p.isDisponible(), p.getFechaCreacion());
    }
}
