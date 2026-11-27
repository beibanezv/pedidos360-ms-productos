package com.pedidos360.productos.repository;

import com.pedidos360.productos.model.ProductoOutbox;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ProductoOutboxRepository extends JpaRepository<ProductoOutbox, UUID> {

  List<ProductoOutbox> findByEstadoOrderByCreadaEnAsc(String estado, Pageable pageable);

  @Modifying
  @Transactional
  @Query("update ProductoOutbox o set o.estado = 'ENVIADO', o.enviadaEn = :enviadaEn "
      + "where o.id = :id")
  void marcarEnviado(@Param("id") UUID id, @Param("enviadaEn") Instant enviadaEn);

  /** Reenvío tras dedup o reinicio: mismo eventoId, sin nueva fila. */
  @Modifying
  @Transactional
  @Query("update ProductoOutbox o set o.estado = 'PENDIENTE', o.enviadaEn = null where o.id = :id")
  void marcarPendiente(@Param("id") UUID id);

  @Modifying
  @Transactional
  @Query("update ProductoOutbox o set o.intentos = o.intentos + 1 where o.id = :id")
  void incrementarIntentos(@Param("id") UUID id);
}
