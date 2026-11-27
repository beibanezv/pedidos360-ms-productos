package com.pedidos360.productos.messaging;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.amqp.core.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ACK manual con reintento real en el mismo delivery: hasta maxReintentos() no se
 * hace requeue (que no incrementa x-death), se espera 1 s entre intentos.
 * IllegalArgumentException = permanente (DLQ directo); otra RuntimeException =
 * transitorio (3 intentos y DLQ). La espera se puede acortar en tests con
 * -Dp360.retry.espera-ms=0.
 */
public abstract class BaseConsumer {

  private final Logger log = LoggerFactory.getLogger(getClass());

  protected int maxReintentos() {
    return 3;
  }

  protected void consumir(Channel channel, long tag, Message raw, Accion accion)
      throws IOException {
    for (int intento = 0; ; intento++) {
      try {
        accion.ejecutar();
        channel.basicAck(tag, false);
        log.debug("Mensaje confirmado tag={}", tag);
        return;
      } catch (IllegalArgumentException e) {
        log.warn("Mensaje enviado a DLQ por error permanente tag={} causa={}", tag, e.getMessage());
        channel.basicNack(tag, false, false);
        return;
      } catch (RuntimeException e) {
        if (intento >= maxReintentos()) {
          log.error("Mensaje enviado a DLQ tras {} reintentos tag={} causa={}",
              maxReintentos(), tag, e.getMessage(), e);
          channel.basicNack(tag, false, false);
          return;
        }
        log.warn("Reintentando mensaje tag={} intento={} de {} causa={}",
            tag, intento + 1, maxReintentos(), e.getMessage());
        long espera = Long.getLong("p360.retry.espera-ms", 1000L);
        try {
          Thread.sleep(espera);
        } catch (InterruptedException interrumpido) {
          Thread.currentThread().interrupt();
          channel.basicNack(tag, false, false);
          return;
        }
      }
    }
  }

  /** Conservado por compatibilidad con tests antiguos; la decisión ya no depende de él. */
  static long muertes(Message raw) {
    List<? extends Map<String, ?>> xDeath = raw.getMessageProperties().getXDeathHeader();
    if (xDeath == null || xDeath.isEmpty()) return 0;
    Object count = xDeath.get(0).get("count");
    return count instanceof Number n ? n.longValue() : 0;
  }

  @FunctionalInterface
  protected interface Accion {
    void ejecutar();
  }
}
