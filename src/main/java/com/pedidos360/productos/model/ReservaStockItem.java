package com.pedidos360.productos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.UUID;

/** Ítem cubierto por una reserva; permite devolver el stock al liberar. */
@Embeddable
public class ReservaStockItem {

  @Column(name = "producto_id", nullable = false)
  private UUID productoId;

  @Column(nullable = false)
  private int cantidad;

  public ReservaStockItem() {}

  public ReservaStockItem(UUID productoId, int cantidad) {
    this.productoId = productoId;
    this.cantidad = cantidad;
  }

  public UUID getProductoId() { return productoId; }
  public void setProductoId(UUID productoId) { this.productoId = productoId; }

  public int getCantidad() { return cantidad; }
  public void setCantidad(int cantidad) { this.cantidad = cantidad; }
}
