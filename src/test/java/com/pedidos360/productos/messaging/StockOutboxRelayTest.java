package com.pedidos360.productos.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pedidos360.productos.model.ProductoOutbox;
import com.pedidos360.productos.repository.ProductoOutboxRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/** Confirmación correlacionada: el eventoId de la fila es el de cada reenvío. */
@ExtendWith(MockitoExtension.class)
class StockOutboxRelayTest {

  @Mock RabbitTemplate rabbitTemplate;
  @Mock ProductoOutboxRepository outboxRepository;
  @InjectMocks StockOutboxRelay relay;

  private ProductoOutbox outbox(String routingKey) {
    return new ProductoOutbox(UUID.randomUUID(), routingKey, UUID.randomUUID(), "u1",
        "SIN_STOCK", Instant.now());
  }

  private void pendientes(ProductoOutbox outbox) {
    when(outboxRepository.findByEstadoOrderByCreadaEnAsc(eq("PENDIENTE"), any()))
        .thenReturn(List.of(outbox));
  }

  @Test
  void confirmaYMarcaEnviadoConElMismoEventoId() {
    ProductoOutbox outbox = outbox(StockTopologia.STOCK_RESERVADO_ROUTING_KEY);
    pendientes(outbox);
    doAnswer(invocation -> {
      CorrelationData datos = invocation.getArgument(3);
      datos.getFuture().complete(new CorrelationData.Confirm(true, null));
      return null;
    }).when(rabbitTemplate).convertAndSend(eq(StockTopologia.EVENTOS_EXCHANGE),
        eq(StockTopologia.STOCK_RESERVADO_ROUTING_KEY), any(Object.class), any(CorrelationData.class));

    relay.reenviarPendientes();

    ArgumentCaptor<CorrelationData> captor = ArgumentCaptor.forClass(CorrelationData.class);
    verify(rabbitTemplate).convertAndSend(eq(StockTopologia.EVENTOS_EXCHANGE),
        eq(StockTopologia.STOCK_RESERVADO_ROUTING_KEY), any(Object.class), captor.capture());
    assertThat(captor.getValue().getId()).isEqualTo(outbox.getId().toString());
    verify(outboxRepository).marcarEnviado(eq(outbox.getId()), any(Instant.class));
  }

  @Test
  void confirmacionNegativaNoMarcaEnviadoYReintenta() {
    ProductoOutbox outbox = outbox(StockTopologia.STOCK_RECHAZADO_ROUTING_KEY);
    pendientes(outbox);
    doAnswer(invocation -> {
      CorrelationData datos = invocation.getArgument(3);
      datos.getFuture().complete(new CorrelationData.Confirm(false, "nack"));
      return null;
    }).when(rabbitTemplate).convertAndSend(eq(StockTopologia.EVENTOS_EXCHANGE),
        eq(StockTopologia.STOCK_RECHAZADO_ROUTING_KEY), any(Object.class), any(CorrelationData.class));

    relay.reenviarPendientes();

    verify(outboxRepository, never()).marcarEnviado(any(), any());
    verify(outboxRepository).incrementarIntentos(outbox.getId());
  }

  @Test
  void brokerCaidoReintentaSinCambiarElEventoId() {
    ProductoOutbox outbox = outbox(StockTopologia.STOCK_RESERVADO_ROUTING_KEY);
    pendientes(outbox);
    doThrow(new AmqpException("broker caído")).when(rabbitTemplate).convertAndSend(
        eq(StockTopologia.EVENTOS_EXCHANGE), eq(StockTopologia.STOCK_RESERVADO_ROUTING_KEY),
        any(Object.class), any(CorrelationData.class));

    relay.reenviarPendientes();

    verify(outboxRepository).incrementarIntentos(outbox.getId());
    verify(outboxRepository, never()).marcarEnviado(any(), any());
  }
}
