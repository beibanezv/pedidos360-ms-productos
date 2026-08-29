package com.pedidos360.productos.repository;

import com.pedidos360.productos.model.Categoria;
import com.pedidos360.productos.model.Producto;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductoRepository extends JpaRepository<Producto, UUID> {
  List<Producto> findByCategoria(Categoria categoria);
}
