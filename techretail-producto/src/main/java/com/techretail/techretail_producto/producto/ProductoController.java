package com.techretail.techretail_producto.producto;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Catalogo de productos. Lectura publica; POST/PUT solo ADMIN (SecurityConfig).
 * No hay DELETE: un producto se retira con {@code disponible=false}.
 */
@RestController
@RequestMapping("/api/productos")
public class ProductoController {

    private final ProductoService servicio;

    public ProductoController(ProductoService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public List<ProductoResponse> listar(
            @RequestParam(name = "categoria", required = false) Long categoria,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "soloDisponibles", defaultValue = "false") boolean soloDisponibles) {
        return servicio.listar(categoria, q, soloDisponibles);
    }

    @GetMapping("/categorias")
    public List<CategoriaResponse> categorias() {
        return servicio.categorias();
    }

    @GetMapping("/{id}")
    public ProductoResponse obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }

    @PostMapping
    public ResponseEntity<ProductoResponse> crear(@Valid @RequestBody ProductoRequest peticion) {
        ProductoResponse creado = servicio.crear(peticion);
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(creado.id()).toUri();
        return ResponseEntity.created(ubicacion).body(creado);
    }

    @PutMapping("/{id}")
    public ProductoResponse actualizar(@PathVariable Long id, @Valid @RequestBody ProductoRequest peticion) {
        return servicio.actualizar(id, peticion);
    }
}
