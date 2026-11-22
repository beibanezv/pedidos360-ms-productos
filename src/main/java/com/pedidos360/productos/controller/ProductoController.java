package com.pedidos360.productos.controller;

import com.pedidos360.productos.model.Categoria;
import com.pedidos360.productos.messaging.InteraccionEvent;
import com.pedidos360.productos.model.Producto;
import com.pedidos360.productos.repository.ProductoRepository;
import jakarta.validation.Valid;
import java.util.List;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/productos")
public class ProductoController {

  private final ProductoRepository repository;
  private final ApplicationEventPublisher events;

  public ProductoController(ProductoRepository repository, ApplicationEventPublisher events) {
    this.repository = repository;
    this.events = events;
  }

  @GetMapping
  public List<Producto> listar(@RequestParam(required = false) Categoria categoria) {
    if (categoria != null) {
      return repository.findByCategoria(categoria);
    }
    return repository.findAll();
  }

  @GetMapping("/{id}")
  public Producto obtener(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    Producto producto = repository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
    if (jwt != null) {
      String usuario = jwt.getClaimAsString("oid");
      if (usuario == null || usuario.isBlank()) usuario = jwt.getSubject();
      if (usuario != null && !usuario.isBlank()) {
        events.publishEvent(new InteraccionEvent(UUID.randomUUID(), usuario,
            InteraccionEvent.Tipo.DETALLE_PRODUCTO, id, Instant.now()));
      }
    }
    return producto;
  }

  // SIMPLIFICADO: solo Admin escribe el catálogo (autorización por rol);
  // la lectura sigue pública. El Gateway ya validó firma/iss/aud en el borde.
  @PreAuthorize("hasRole('Admin')")
  @PostMapping
  public ResponseEntity<Producto> crear(@Valid @RequestBody Producto producto) {
    // asegurar que JPA genere el id
    producto.setId(null);
    Producto guardado = repository.save(producto);
    return ResponseEntity.status(HttpStatus.CREATED).body(guardado);
  }

  @PreAuthorize("hasRole('Admin')")
  @PutMapping("/{id}")
  public Producto actualizar(@PathVariable UUID id, @Valid @RequestBody Producto producto) {
    Producto existente = repository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
    existente.setNombre(producto.getNombre());
    existente.setArtistaOMarca(producto.getArtistaOMarca());
    existente.setCategoria(producto.getCategoria());
    existente.setPrecioClp(producto.getPrecioClp());
    existente.setStock(producto.getStock());
    existente.setDescripcion(producto.getDescripcion());
    return repository.save(existente);
  }

  @PreAuthorize("hasRole('Admin')")
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
    if (!repository.existsById(id)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado");
    }
    repository.deleteById(id);
    return ResponseEntity.noContent().build();
  }
}
