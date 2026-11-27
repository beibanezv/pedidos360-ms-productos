package com.pedidos360.productos.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.rabbitmq.client.Channel;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

/** ACK/NACK del listener; la lógica transaccional se prueba en ReservaStockServiceTest. */
@ExtendWith(MockitoExtension.class)
class StockConsumerTest {

  @Mock ReservaStockService servicio;
  @Mock Channel channel;
  @InjectMocks StockConsumer consumer;

  private OrdenRegistradaEvent evento(UUID ordenId) {
    return new OrdenRegistradaEvent(ordenId, "u1", "comprador@mail.com",
        List.of(new OrdenRegistradaEvent.Item(UUID.randomUUID(), 3)), null);
  }

  private Message mensajeSinMuertes() {
    return new Message("x".getBytes(), new MessageProperties());
  }

  private Message mensajeConMuertes(long muertes) {
    MessageProperties props = new MessageProperties();
    props.setHeader("x-death", List.of(Map.of("count", muertes)));
    return new Message("x".getBytes(), props);
  }

  @Test
  void ackSoloTrasCommit() throws Exception {
    OrdenRegistradaEvent event = evento(UUID.randomUUID());

    consumer.descontarStock(event, channel, 1L, mensajeSinMuertes());

    InOrder orden = inOrder(servicio, channel);
    orden.verify(servicio).reservar(event);
    orden.verify(channel).basicAck(1L, false);
    verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
  }

  @Test
  void rechazoDurableRetornaNormalmenteYHaceAck() throws Exception {
    OrdenRegistradaEvent event = evento(UUID.randomUUID());

    consumer.descontarStock(event, channel, 2L, mensajeSinMuertes());

    verify(servicio).reservar(event);
    verify(channel).basicAck(2L, false);
    verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
  }

  @Test
  void errorPermanenteDelServicioNackSinRequeue() throws Exception {
    OrdenRegistradaEvent event = evento(UUID.randomUUID());
    doThrow(new IllegalArgumentException("mensaje inválido")).when(servicio).reservar(event);

    consumer.descontarStock(event, channel, 3L, mensajeSinMuertes());

    verify(channel).basicNack(3L, false, false);
    verify(channel, never()).basicAck(anyLong(), anyBoolean());
  }

  @Test
  void falloTransitorioPrimerIntentoNackConRequeue() throws Exception {
    OrdenRegistradaEvent event = evento(UUID.randomUUID());
    doThrow(new IllegalStateException("BD caída")).when(servicio).reservar(event);

    consumer.descontarStock(event, channel, 4L, mensajeSinMuertes());

    verify(channel).basicNack(4L, false, true);
  }

  @Test
  void nackSinRequeueTrasTresMuertes() throws Exception {
    OrdenRegistradaEvent event = evento(UUID.randomUUID());
    doThrow(new IllegalStateException("BD caída")).when(servicio).reservar(event);

    consumer.descontarStock(event, channel, 5L, mensajeConMuertes(3));

    verify(channel).basicNack(5L, false, false);
  }
}
