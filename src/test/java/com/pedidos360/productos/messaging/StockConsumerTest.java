package com.pedidos360.productos.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pedidos360.productos.model.Producto;
import com.pedidos360.productos.repository.ProductoRepository;
import com.rabbitmq.client.Channel;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

@ExtendWith(MockitoExtension.class)
class StockConsumerTest {

  @Mock ProductoRepository productoRepository;
  @Mock Channel channel;
  @InjectMocks StockConsumer consumer;

  private Producto productoConStock(UUID id, int stock) {
    Producto p = new Producto();
    p.setId(id);
    p.setNombre("Vinilo de prueba");
    p.setArtistaOMarca("Test");
    p.setStock(stock);
    return p;
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
  void exito_descuentaStockYack() throws Exception {
    UUID sku = UUID.randomUUID();
    when(productoRepository.findById(sku)).thenReturn(Optional.of(productoConStock(sku, 10)));

    consumer.descontarStock(
        new OrdenRegistradaEvent(UUID.randomUUID(), "u1",
            List.of(new OrdenRegistradaEvent.Item(sku, 3)), null),
        channel, 1L, mensajeSinMuertes());

    verify(productoRepository).save(any(Producto.class));
    verify(channel).basicAck(1L, false);
    verify(channel, never()).basicNack(any(Long.class), any(Boolean.class), any(Boolean.class));
  }

  @Test
  void skuInexistente_nackSinRequeueDirectoADlq() throws Exception {
    UUID sku = UUID.randomUUID();
    when(productoRepository.findById(sku)).thenReturn(Optional.empty());

    consumer.descontarStock(
        new OrdenRegistradaEvent(UUID.randomUUID(), "u1",
            List.of(new OrdenRegistradaEvent.Item(sku, 1)), null),
        channel, 2L, mensajeSinMuertes());

    verify(channel).basicNack(2L, false, false);
    verify(channel, never()).basicAck(any(Long.class), any(Boolean.class));
  }

  @Test
  void falloTransitorioPrimerIntento_nackConRequeue() throws Exception {
    UUID sku = UUID.randomUUID();
    when(productoRepository.findById(sku)).thenThrow(new RuntimeException("BD caída"));

    consumer.descontarStock(
        new OrdenRegistradaEvent(UUID.randomUUID(), "u1",
            List.of(new OrdenRegistradaEvent.Item(sku, 1)), null),
        channel, 3L, mensajeSinMuertes());

    verify(channel).basicNack(3L, false, true);
  }

  @Test
  void falloTransitorioAgotadosReintentos_nackSinRequeueADlq() throws Exception {
    UUID sku = UUID.randomUUID();
    when(productoRepository.findById(sku)).thenThrow(new RuntimeException("BD caída"));

    consumer.descontarStock(
        new OrdenRegistradaEvent(UUID.randomUUID(), "u1",
            List.of(new OrdenRegistradaEvent.Item(sku, 1)), null),
        channel, 4L, mensajeConMuertes(3));

    verify(channel).basicNack(4L, false, false);
  }
}
