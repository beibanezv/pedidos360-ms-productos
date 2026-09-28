package com.pedidos360.productos.messaging;

import com.pedidos360.productos.config.RabbitMQConfig;
import com.pedidos360.productos.model.Producto;
import com.pedidos360.productos.repository.ProductoRepository;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Consumidor del dominio "stock" (rúbrica EP3/EP4 indicador 4: agrupado por
 * dominio funcional, no un listener genérico). Hereda el ACK manual de
 * BaseConsumer (indicador 5); aquí solo vive la lógica de negocio.
 *
 * SIMPLIFICADO: reintento inmediato sin espera. Upgrade: retry queue con
 * x-message-ttl + DLX de vuelta a la cola principal (backoff real).
 * SIMPLIFICADO: sin idempotencia; si el proceso muere tras guardar y antes
 * del ack, el redelivery descuenta dos veces. Upgrade: tabla
 * ordenes_procesadas consultada antes de descontar.
 */
@Component
public class StockConsumer extends BaseConsumer {

  private final ProductoRepository productoRepository;

  public StockConsumer(ProductoRepository productoRepository) {
    this.productoRepository = productoRepository;
  }

  @Override
  protected int maxReintentos() {
    return RabbitMQConfig.MAX_REINTENTOS;
  }

  @RabbitListener(queues = RabbitMQConfig.STOCK_QUEUE)
  public void descontarStock(OrdenRegistradaEvent event, Channel channel,
      @Header(AmqpHeaders.DELIVERY_TAG) long tag, Message raw) throws IOException {
    consumir(channel, tag, raw, () -> {
      for (OrdenRegistradaEvent.Item item : event.items()) {
        descontarItem(item);
      }
    });
  }

  private void descontarItem(OrdenRegistradaEvent.Item item) {
    if (item.cantidad() <= 0) {
      throw new IllegalArgumentException("Cantidad inválida: " + item.cantidad());
    }
    Producto producto = productoRepository.findById(item.productoId())
        .orElseThrow(() -> new IllegalArgumentException("SKU inexistente: " + item.productoId()));
    int stock = producto.getStock() != null ? producto.getStock() : 0;
    if (stock < item.cantidad()) {
      throw new IllegalArgumentException(
          "Stock insuficiente para " + producto.getId() + ": hay " + stock);
    }
    producto.setStock(stock - item.cantidad());
    productoRepository.save(producto);
  }
}
