package com.techretail.techretail_producto.producto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Reglas de negocio puras: sin Spring ni base de datos. */
class ProductoServiceTests {

    private ProductoRepository productos;
    private CategoriaRepository categorias;
    private ProductoService servicio;

    @BeforeEach
    void preparar() {
        productos = mock(ProductoRepository.class);
        categorias = mock(CategoriaRepository.class);
        servicio = new ProductoService(productos, categorias);
    }

    private ProductoRequest peticion(Long idCategoria, Boolean disponible) {
        return new ProductoRequest("Mouse", "d", new BigDecimal("10.50"), idCategoria, 3, disponible);
    }

    @Test
    void crearConCategoriaInexistenteLanzaExcepcionYNoGuarda() {
        when(categorias.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.crear(peticion(9L, null)))
                .isInstanceOf(CategoriaNoEncontradaException.class);
        verify(productos, never()).save(any());
    }

    @Test
    void crearSinIndicarDisponibleLoDejaDisponible() {
        Categoria categoria = new Categoria("Audio", "d");
        when(categorias.findById(1L)).thenReturn(Optional.of(categoria));
        when(productos.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductoResponse respuesta = servicio.crear(peticion(1L, null));

        assertThat(respuesta.disponible()).isTrue();
        assertThat(respuesta.categoria()).isEqualTo("Audio");
        assertThat(respuesta.precio()).isEqualByComparingTo("10.50");
    }

    @Test
    void obtenerInexistenteLanzaExcepcion() {
        when(productos.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.obtener(5L)).isInstanceOf(ProductoNoEncontradoException.class);
    }

    @Test
    void actualizarInexistenteLanzaExcepcion() {
        when(productos.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.actualizar(5L, peticion(1L, true)))
                .isInstanceOf(ProductoNoEncontradoException.class);
    }
}
