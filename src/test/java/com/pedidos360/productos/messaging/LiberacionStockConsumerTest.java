package com.pedidos360.productos.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.rabbitmq.client.Channel;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

@ExtendWith(MockitoExtension.class)
class LiberacionStockConsumerTest {

  // Reintento real en el mismo delivery (M06b): acortar la espera en tests.
  static { System.setProperty("p360.retry.espera-ms", "0"); }

  @Mock ReservaStockService servicio;
  @Mock Channel channel;
  @InjectMocks LiberacionStockConsumer consumer;

  private StockLiberadoEvent evento() {
    return new StockLiberadoEvent(UUID.randomUUID(), UUID.randomUUID(), "u1", "CANCELADA",
        Instant.now());
  }

  private Message mensaje(long muertes) {
    MessageProperties props = new MessageProperties();
    if (muertes > 0) props.setHeader("x-death", List.of(Map.of("count", muertes)));
    return new Message("x".getBytes(), props);
  }

  @Test
  void liberacionValidaHaceAck() throws Exception {
    StockLiberadoEvent event = evento();

    consumer.liberarStock(event, channel, 1L, mensaje(0));

    verify(servicio).liberar(event);
    verify(channel).basicAck(1L, false);
  }

  @Test
  void motivoInvalidoNackSinRequeue() throws Exception {
    StockLiberadoEvent event = evento();
    doThrow(new IllegalArgumentException("motivo inválido")).when(servicio).liberar(event);

    consumer.liberarStock(event, channel, 2L, mensaje(0));

    verify(channel).basicNack(2L, false, false);
    verify(channel, never()).basicAck(anyLong(), anyBoolean());
  }

  @Test
  void falloTransitorioNackSinRequeueTrasReintentos() throws Exception {
    StockLiberadoEvent event = evento();
    doThrow(new IllegalStateException("BD caída")).when(servicio).liberar(event);

    consumer.liberarStock(event, channel, 3L, mensaje(0));

    // M06b: reintento en el mismo delivery (sin requeue) y DLQ tras maxReintentos.
    verify(channel).basicNack(3L, false, false);
  }
}
