package com.techretail.techretail_producto.producto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo de POST /api/productos/categorias. */
public record CategoriaRequest(
        @NotBlank @Size(max = 100) String nombre,
        @Size(max = 255) String descripcion) {
}
