package com.techretail.techretail_producto.producto;

public class ProductoNoEncontradoException extends RuntimeException {

    public ProductoNoEncontradoException(Long id) {
        super("No existe el producto " + id);
    }
}
