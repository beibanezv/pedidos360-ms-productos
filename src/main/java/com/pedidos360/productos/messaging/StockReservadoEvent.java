package com.pedidos360.productos.messaging;

import java.time.Instant;
import java.util.UUID;

/** C3: resultado durable de una reserva de stock. Sin datos sensibles. */
public record StockReservadoEvent(
    UUID eventoId,
    UUID ordenId,
    String usuarioId,
    Instant ocurrioEn) {}
