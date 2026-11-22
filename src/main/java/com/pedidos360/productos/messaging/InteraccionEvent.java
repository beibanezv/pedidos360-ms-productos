package com.pedidos360.productos.messaging;

import java.time.Instant;
import java.util.UUID;

public record InteraccionEvent(UUID eventoId, String usuarioId, Tipo tipo, UUID productoId, Instant ocurrioEn) {
  public enum Tipo { DETALLE_PRODUCTO }
}
