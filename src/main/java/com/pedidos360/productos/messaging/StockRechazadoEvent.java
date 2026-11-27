package com.pedidos360.productos.messaging;

import java.time.Instant;
import java.util.UUID;

/** C3: rechazo durable; motivo cerrado SIN_STOCK | SKU_NO_DISPONIBLE. */
public record StockRechazadoEvent(
    UUID eventoId,
    UUID ordenId,
    String usuarioId,
    String motivo,
    Instant ocurrioEn) {}
