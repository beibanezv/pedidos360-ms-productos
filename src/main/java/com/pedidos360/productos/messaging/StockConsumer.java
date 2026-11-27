package com.pedidos360.productos.messaging;

import com.pedidos360.productos.config.RabbitMQConfig;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Consumidor del dominio "stock" (rúbrica EP3/EP4 indicador 4: agrupado por
 * dominio funcional). Hereda el ACK manual de BaseConsumer (indicador 5).
 * La lógica transactional vive en ReservaStockService: este listener solo
 * delega y deja que BaseConsumer confirme tras el commit.
 *
 * SIMPLIFICADO: reintento inmediato sin espera. Upgrade: retry queue con
 * x-message-ttl + DLX de vuelta a la cola principal (backoff real).
 */
@Component
public class StockConsumer extends BaseConsumer {

  private final ReservaStockService servicio;

  public StockConsumer(ReservaStockService servicio) {
    this.servicio = servicio;
  }

  @Override
  protected int maxReintentos() {
    return RabbitMQConfig.MAX_REINTENTOS;
  }

  @RabbitListener(queues = RabbitMQConfig.STOCK_QUEUE)
  public void descontarStock(OrdenRegistradaEvent event, Channel channel,
      @Header(AmqpHeaders.DELIVERY_TAG) long tag, Message raw) throws IOException {
    consumir(channel, tag, raw, () -> servicio.reservar(event));
  }
}
