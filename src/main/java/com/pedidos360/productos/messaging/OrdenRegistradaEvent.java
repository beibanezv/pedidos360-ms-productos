package com.pedidos360.productos.messaging;

import java.util.List;
import java.util.UUID;

/**
 * Evento que publica ms-orders cuando registra una orden.
 * ms-productos solo lee productoId/cantidad (contrato propio por servicio).
 * codigoCupon viaja para ms-cupones; aquí se ignora (pero el campo debe
 * existir: Jackson falla con propiedades desconocidas).
 */
public record OrdenRegistradaEvent(
    UUID ordenId,
    String usuarioId,
    List<Item> items,
    String codigoCupon) {

  public record Item(UUID productoId, int cantidad) {}
}
