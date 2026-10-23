package com.techretail.techretail_producto.producto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Cuerpo de POST/PUT /api/productos. {@code disponible} omitido equivale a true. */
public record ProductoRequest(
        @NotBlank @Size(max = 150) String nombre,
        String descripcion,
        @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal precio,
        @NotNull Long idCategoria,
        @NotNull @Min(0) Integer stockReferencial,
        Boolean disponible) {
}
