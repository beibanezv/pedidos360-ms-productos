package com.pedidos360.productos.repository;

import com.pedidos360.productos.model.Categoria;
import com.pedidos360.productos.model.Producto;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ProductoRepository extends JpaRepository<Producto, UUID> {
  List<Producto> findByCategoria(Categoria categoria);

  /** Locks de fila adquiridos en orden UUID creciente: evita interbloqueos. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Producto p where p.id in :ids order by p.id")
  List<Producto> findByIdsForUpdate(@Param("ids") Collection<UUID> ids);

  @Modifying
  @Transactional
  @Query("update Producto p set p.stock = p.stock - :cantidad "
      + "where p.id = :id and p.stock >= :cantidad")
  int descontarSiHayStock(@Param("id") UUID id, @Param("cantidad") int cantidad);

  @Modifying
  @Transactional
  @Query("update Producto p set p.stock = p.stock + :cantidad where p.id = :id")
  int devolverStock(@Param("id") UUID id, @Param("cantidad") int cantidad);

  /** Serializa el dedup por ordenId; se libera al hacer commit/rollback. */
  @Query(value = "select count(*) from (select pg_advisory_xact_lock(hashtextextended(:clave, 0))) l",
      nativeQuery = true)
  long lockOrden(@Param("clave") String clave);
}
