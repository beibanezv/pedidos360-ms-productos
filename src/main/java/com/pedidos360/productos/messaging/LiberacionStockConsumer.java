package com.pedidos360.productos.messaging;

import com.pedidos360.productos.config.RabbitMQConfig;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/** Consumidor del dominio stock: compensación publicada por orders (C4). */
@Component
public class LiberacionStockConsumer extends BaseConsumer {

  private final ReservaStockService servicio;

  public LiberacionStockConsumer(ReservaStockService servicio) {
    this.servicio = servicio;
  }

  @Override
  protected int maxReintentos() {
    return RabbitMQConfig.MAX_REINTENTOS;
  }

  @RabbitListener(queues = StockTopologia.LIBERACIONES_STOCK_QUEUE)
  public void liberarStock(StockLiberadoEvent event, Channel channel,
      @Header(AmqpHeaders.DELIVERY_TAG) long tag, Message raw) throws IOException {
    consumir(channel, tag, raw, () -> servicio.liberar(event));
  }
}
