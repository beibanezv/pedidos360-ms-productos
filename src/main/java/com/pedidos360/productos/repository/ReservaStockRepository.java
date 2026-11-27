package com.pedidos360.productos.repository;

import com.pedidos360.productos.model.ReservaStock;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ReservaStockRepository extends JpaRepository<ReservaStock, UUID> {

  /** CAS de compensación: solo la primera liberación modifica la fila. */
  @Modifying
  @Transactional
  @Query("update ReservaStock r set r.estado = 'LIBERADA' "
      + "where r.ordenId = :ordenId and r.estado = 'RESERVADA'")
  int marcarLiberada(@Param("ordenId") UUID ordenId);
}
