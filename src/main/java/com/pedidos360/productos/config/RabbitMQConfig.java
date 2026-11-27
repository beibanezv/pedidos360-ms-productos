package com.pedidos360.productos.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topología RabbitMQ que usa ms-productos (lado consumidor).
 * Nombres centralizados aquí (rúbrica EP3/EP4 indicador 1): ningún otro
 * archivo del servicio repite estos literales.
 *
 * Ruta documentada:
 *   ms-orders --topic--> p360.eventos / orden.registrada
 *                            └─> p360.stock.queue (la consume StockConsumer)
 *   Si el mensaje muere (rechazo/NACK sin requeue) va a p360.dlx
 *   con routing key = nombre de la cola origen, y de ahí a su DLQ.
 */
@Configuration
public class RabbitMQConfig {

  // Exchange topic + cola + routing key del caso de uso "descontar stock".
  public static final String EVENTOS_EXCHANGE = "p360.eventos";
  public static final String STOCK_QUEUE = "p360.stock.queue";
  public static final String ORDEN_REGISTRADA_ROUTING_KEY = "orden.registrada";

  // C3/C4 congelado (G0 2026-11-27): eventos de reserva sobre los exchanges existentes.
  public static final String STOCK_RESERVADO_ROUTING_KEY = "stock.reservado";
  public static final String STOCK_RECHAZADO_ROUTING_KEY = "stock.rechazado";
  public static final String STOCK_LIBERADO_ROUTING_KEY = "stock.liberado";
  public static final String LIBERACIONES_STOCK_QUEUE = "p360.liberaciones.stock.queue";
  public static final String LIBERACIONES_STOCK_DLQ = "p360.liberaciones.stock.dlq";

  // Límite de reintentos de errores transitorios (ver StockConsumer).
  public static final int MAX_REINTENTOS = 3;

  // Dead-letter exchange compartido (direct: cada DLQ se enlaza con su propia key).
  public static final String DLX_EXCHANGE = "p360.dlx";
  public static final String STOCK_DLQ = "p360.stock.dlq";

  @Bean
  TopicExchange eventosExchange() {
    return new TopicExchange(EVENTOS_EXCHANGE, true, false);
  }

  @Bean
  Queue stockQueue() {
    return QueueBuilder.durable(STOCK_QUEUE)
        .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
        // Los mensajes muertos se re-enrutan con el nombre de la cola origen.
        .withArgument("x-dead-letter-routing-key", STOCK_QUEUE)
        .build();
  }

  @Bean
  Binding stockBinding(Queue stockQueue, TopicExchange eventosExchange) {
    return new Binding(STOCK_QUEUE, Binding.DestinationType.QUEUE,
        EVENTOS_EXCHANGE, ORDEN_REGISTRADA_ROUTING_KEY, null);
  }

  @Bean
  DirectExchange deadLetterExchange() {
    return new DirectExchange(DLX_EXCHANGE, true, false);
  }

  @Bean
  Queue stockDlq() {
    return QueueBuilder.durable(STOCK_DLQ).build();
  }

  @Bean
  Binding stockDlqBinding(Queue stockDlq, DirectExchange deadLetterExchange) {
    return new Binding(STOCK_DLQ, Binding.DestinationType.QUEUE,
        DLX_EXCHANGE, STOCK_QUEUE, null);
  }

  // Matías declara solo la topología C3; el consumidor lo escribe Benjamín.
  @Bean
  Queue liberacionesStockQueue() {
    return QueueBuilder.durable(LIBERACIONES_STOCK_QUEUE)
        .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
        .withArgument("x-dead-letter-routing-key", LIBERACIONES_STOCK_QUEUE)
        .build();
  }

  @Bean
  Binding liberacionesStockBinding(Queue liberacionesStockQueue, TopicExchange eventosExchange) {
    return new Binding(LIBERACIONES_STOCK_QUEUE, Binding.DestinationType.QUEUE,
        EVENTOS_EXCHANGE, STOCK_LIBERADO_ROUTING_KEY, null);
  }

  @Bean
  Queue liberacionesStockDlq() {
    return QueueBuilder.durable(LIBERACIONES_STOCK_DLQ).build();
  }

  @Bean
  Binding liberacionesStockDlqBinding(Queue liberacionesStockDlq, DirectExchange deadLetterExchange) {
    return new Binding(LIBERACIONES_STOCK_DLQ, Binding.DestinationType.QUEUE,
        DLX_EXCHANGE, LIBERACIONES_STOCK_QUEUE, null);
  }

  // Los eventos viajan como JSON (records de messaging.*).
  @Bean
  MessageConverter jsonMessageConverter() {
    return new Jackson2JsonMessageConverter();
  }
}
