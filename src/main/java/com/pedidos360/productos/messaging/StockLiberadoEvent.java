package com.pedidos360.productos.messaging;

import java.time.Instant;
import java.util.UUID;

/** C3: compensación; motivo cerrado EXPIRADA | CANCELADA. */
public record StockLiberadoEvent(
    UUID eventoId,
    UUID ordenId,
    String usuarioId,
    String motivo,
    Instant ocurrioEn) {}
