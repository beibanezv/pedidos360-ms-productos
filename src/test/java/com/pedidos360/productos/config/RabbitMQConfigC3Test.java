package com.pedidos360.productos.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;

/** M06a: topología C3 declarada sin tocar las colas existentes ni purgar nada. */
class RabbitMQConfigC3Test {
  private final RabbitMQConfig config = new RabbitMQConfig();

  @Test
  void colaDeLiberacionesEsDurablesYTieneDlx() {
    Queue cola = config.liberacionesStockQueue();

    assertThat(cola.isDurable()).isTrue();
    assertThat(cola.getArguments())
        .containsEntry("x-dead-letter-exchange", RabbitMQConfig.DLX_EXCHANGE)
        .containsEntry("x-dead-letter-routing-key", RabbitMQConfig.LIBERACIONES_STOCK_QUEUE);
  }

  @Test
  void bindingDeLiberacionesUsaElRoutingKeyCongelado() {
    Binding binding = config.liberacionesStockBinding(config.liberacionesStockQueue(), config.eventosExchange());

    assertThat(binding.getExchange()).isEqualTo(RabbitMQConfig.EVENTOS_EXCHANGE);
    assertThat(binding.getRoutingKey()).isEqualTo("stock.liberado");
  }

  @Test
  void dlqSeEnlazaConElNombreDeLaColaOrigen() {
    Binding dlq = config.liberacionesStockDlqBinding(
        config.liberacionesStockDlq(), config.deadLetterExchange());

    assertThat(dlq.getRoutingKey()).isEqualTo(RabbitMQConfig.LIBERACIONES_STOCK_QUEUE);
    assertThat(dlq.getExchange()).isEqualTo(RabbitMQConfig.DLX_EXCHANGE);
  }

  @Test
  void constantesDelContratoCongelado() {
    assertThat(RabbitMQConfig.STOCK_RESERVADO_ROUTING_KEY).isEqualTo("stock.reservado");
    assertThat(RabbitMQConfig.STOCK_RECHAZADO_ROUTING_KEY).isEqualTo("stock.rechazado");
    assertThat(RabbitMQConfig.LIBERACIONES_STOCK_QUEUE).isEqualTo("p360.liberaciones.stock.queue");
  }

  @Test
  void topologiaAnteriorSigueIgual() {
    Queue stock = config.stockQueue();

    assertThat(stock.getName()).isEqualTo("p360.stock.queue");
    assertThat(stock.getArguments()).containsEntry("x-dead-letter-exchange", RabbitMQConfig.DLX_EXCHANGE);
    assertThat(config.stockBinding(stock, config.eventosExchange()).getRoutingKey()).isEqualTo("orden.registrada");
  }
}
