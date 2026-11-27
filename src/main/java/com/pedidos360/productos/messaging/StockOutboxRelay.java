package com.pedidos360.productos.messaging;

import com.pedidos360.productos.model.ProductoOutbox;
import com.pedidos360.productos.repository.ProductoOutboxRepository;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.connection.CorrelationData.Confirm;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Publica la salida persistida tras el commit y marca ENVIADO solo con confirmación Rabbit. */
@Component
public class StockOutboxRelay {

  private final RabbitTemplate rabbitTemplate;
  private final ProductoOutboxRepository outboxRepository;

  public StockOutboxRelay(RabbitTemplate rabbitTemplate, ProductoOutboxRepository outboxRepository) {
    this.rabbitTemplate = rabbitTemplate;
    this.outboxRepository = outboxRepository;
  }

  @Scheduled(fixedDelayString = "${p360.stock.outbox.delay-ms:2000}",
      initialDelayString = "${p360.stock.outbox.delay-ms:2000}")
  public void reenviarPendientes() {
    List<ProductoOutbox> pendientes = outboxRepository.findByEstadoOrderByCreadaEnAsc(
        "PENDIENTE", PageRequest.of(0, 20));
    for (ProductoOutbox outbox : pendientes) {
      enviar(outbox);
    }
  }

  private void enviar(ProductoOutbox outbox) {
    try {
      Object evento = switch (outbox.getRoutingKey()) {
        case StockTopologia.STOCK_RESERVADO_ROUTING_KEY -> new StockReservadoEvent(
            outbox.getId(), outbox.getOrdenId(), outbox.getUsuarioId(), outbox.getOcurrioEn());
        case StockTopologia.STOCK_RECHAZADO_ROUTING_KEY -> new StockRechazadoEvent(
            outbox.getId(), outbox.getOrdenId(), outbox.getUsuarioId(), outbox.getMotivo(),
            outbox.getOcurrioEn());
        default -> throw new IllegalStateException("Routing key desconocida: " + outbox.getRoutingKey());
      };
      // eventoId estable: el reenvío tras reinicio no crea un resultado nuevo.
      CorrelationData correlacion = new CorrelationData(outbox.getId().toString());
      rabbitTemplate.convertAndSend(StockTopologia.EVENTOS_EXCHANGE, outbox.getRoutingKey(),
          evento, correlacion);
      Confirm confirm = correlacion.getFuture().get(5, TimeUnit.SECONDS);
      if (confirm != null && confirm.isAck()) {
        outboxRepository.marcarEnviado(outbox.getId(), Instant.now());
      } else {
        outboxRepository.incrementarIntentos(outbox.getId());
      }
    } catch (AmqpException | InterruptedException | ExecutionException | TimeoutException ex) {
      if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
      outboxRepository.incrementarIntentos(outbox.getId());
    }
  }
}
