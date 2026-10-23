package com.techretail.techretail_producto.producto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** Las entidades deben coincidir con la migracion Flyway (ddl-auto=validate). */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProductoPersistenciaTests {

    @Autowired
    private ProductoRepository productos;

    @Autowired
    private CategoriaRepository categorias;

    @Test
    void guardaYRecuperaUnProductoConSuCategoriaYPrecioDeDosDecimales() {
        Categoria categoria = categorias.save(new Categoria("Perifericos", "teclados y mouse"));
        Producto guardado = productos.saveAndFlush(
                new Producto(categoria, "Teclado", "mecanico", new BigDecimal("19.90"), 7, true));

        Producto leido = productos.findById(guardado.getId()).orElseThrow();

        assertThat(leido.getCategoria().getNombre()).isEqualTo("Perifericos");
        assertThat(leido.getPrecio()).isEqualByComparingTo("19.90");
        assertThat(leido.getPrecio().scale()).isEqualTo(2);
        assertThat(leido.getFechaCreacion()).isNotNull();
        assertThat(leido.isDisponible()).isTrue();
    }
}
