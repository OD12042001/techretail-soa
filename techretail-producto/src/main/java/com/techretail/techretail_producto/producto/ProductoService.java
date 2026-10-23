package com.techretail.techretail_producto.producto;

import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProductoService {

    private final ProductoRepository productos;
    private final CategoriaRepository categorias;

    public ProductoService(ProductoRepository productos, CategoriaRepository categorias) {
        this.productos = productos;
        this.categorias = categorias;
    }

    @Transactional(readOnly = true)
    public List<ProductoResponse> listar(Long idCategoria, String q, boolean soloDisponibles) {
        Specification<Producto> filtro = (raiz, consulta, cb) -> cb.conjunction();
        if (idCategoria != null) {
            filtro = filtro.and((raiz, consulta, cb) -> cb.equal(raiz.get("categoria").get("id"), idCategoria));
        }
        if (q != null && !q.isBlank()) {
            String patron = "%" + escapar(q.trim().toLowerCase()) + "%";
            filtro = filtro.and((raiz, consulta, cb) -> cb.like(cb.lower(raiz.get("nombre")), patron, '\\'));
        }
        if (soloDisponibles) {
            filtro = filtro.and((raiz, consulta, cb) -> cb.isTrue(raiz.get("disponible")));
        }
        return productos.findAll(filtro, Sort.by("id")).stream().map(ProductoResponse::desde).toList();
    }

    @Transactional(readOnly = true)
    public ProductoResponse obtener(Long id) {
        return ProductoResponse.desde(productos.findById(id).orElseThrow(() -> new ProductoNoEncontradoException(id)));
    }

    @Transactional(readOnly = true)
    public List<CategoriaResponse> categorias() {
        return categorias.findAll(Sort.by("nombre")).stream()
                .map(c -> new CategoriaResponse(c.getId(), c.getNombre(), c.getDescripcion())).toList();
    }

    public ProductoResponse crear(ProductoRequest peticion) {
        Categoria categoria = categoria(peticion.idCategoria());
        Producto producto = new Producto(categoria, peticion.nombre(), peticion.descripcion(), peticion.precio(),
                peticion.stockReferencial(), disponible(peticion));
        return ProductoResponse.desde(productos.save(producto));
    }

    public ProductoResponse actualizar(Long id, ProductoRequest peticion) {
        Producto producto = productos.findById(id).orElseThrow(() -> new ProductoNoEncontradoException(id));
        producto.actualizar(categoria(peticion.idCategoria()), peticion.nombre(), peticion.descripcion(),
                peticion.precio(), peticion.stockReferencial(), disponible(peticion));
        return ProductoResponse.desde(productos.save(producto));
    }

    private Categoria categoria(Long idCategoria) {
        return categorias.findById(idCategoria).orElseThrow(() -> new CategoriaNoEncontradaException(idCategoria));
    }

    private static boolean disponible(ProductoRequest peticion) {
        return peticion.disponible() == null || peticion.disponible();
    }

    private static String escapar(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
