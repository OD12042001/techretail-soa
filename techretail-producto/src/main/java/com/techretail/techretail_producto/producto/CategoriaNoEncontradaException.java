package com.techretail.techretail_producto.producto;

public class CategoriaNoEncontradaException extends RuntimeException {

    public CategoriaNoEncontradaException(Long id) {
        super("No existe la categoria " + id);
    }
}
