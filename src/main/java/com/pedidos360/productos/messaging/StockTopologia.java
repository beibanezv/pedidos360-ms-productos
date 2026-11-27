package com.pedidos360.productos.messaging;

/**
 * Literales C3 necesarios en ms-productos mientras M06a no se mergea en main.
 * RabbitMQConfig pertenece a Matías y no se edita aquí.
 */
public final class StockTopologia {

  private StockTopologia() {}

  // SIMPLIFICADO: literales de M06a, migrar a constantes centralizadas tras merge
  public static final String EVENTOS_EXCHANGE = "p360.eventos";
  public static final String DLX_EXCHANGE = "p360.dlx";
  public static final String STOCK_RESERVADO_ROUTING_KEY = "stock.reservado";
  public static final String STOCK_RECHAZADO_ROUTING_KEY = "stock.rechazado";
  public static final String STOCK_LIBERADO_ROUTING_KEY = "stock.liberado";
  public static final String RESERVAS_ORDENES_QUEUE = "p360.reservas.ordenes.queue";
  public static final String RESERVAS_ORDENES_DLQ = "p360.reservas.ordenes.dlq";
  public static final String LIBERACIONES_STOCK_QUEUE = "p360.liberaciones.stock.queue";
  public static final String LIBERACIONES_STOCK_DLQ = "p360.liberaciones.stock.dlq";
  // Reservadas para B09: pagos todavía no publica nada en este lote.
  public static final String PAGO_RESUELTO_ROUTING_KEY = "pago.resuelto";
  public static final String PAGOS_ORDENES_QUEUE = "p360.pagos.ordenes.queue";
  public static final String PAGOS_ORDENES_DLQ = "p360.pagos.ordenes.dlq";
}
