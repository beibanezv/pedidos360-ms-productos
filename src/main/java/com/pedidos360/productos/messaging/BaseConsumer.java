package com.pedidos360.productos.messaging;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.amqp.core.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Plantilla de ACK manual para todos los consumidores del ecosistema
 * (rúbrica EP3/EP4 indicador 5: lógica de error documentada y consistente).
 * Cada repo de Fase 3 copia esta clase y escribe solo su lógica de negocio.
 *
 * Contrato: la subclase lanza IllegalArgumentException ante error PERMANENTE
 * (el mensaje nunca va a pasar → DLQ directo, sin gastar reintentos) y otra
 * RuntimeException ante error TRANSITORIO (requeue hasta maxReintentos()
 * muertes contadas en la cabecera x-death, después DLQ).
 */
public abstract class BaseConsumer {

  private final Logger log = LoggerFactory.getLogger(getClass());

  /** Reintentos de errores transitorios antes de mandar a la DLQ. */
  protected int maxReintentos() {
    return 3;
  }

  protected void consumir(Channel channel, long tag, Message raw, Accion accion)
      throws IOException {
    try {
      accion.ejecutar();
      channel.basicAck(tag, false);
      log.debug("Mensaje confirmado tag={}", tag);
    } catch (IllegalArgumentException e) {
      // Permanente: a la DLQ sin gastar reintentos.
      log.warn("Mensaje enviado a DLQ por error permanente tag={} causa={}", tag, e.getMessage());
      channel.basicNack(tag, false, false);
    } catch (RuntimeException e) {
      if (muertes(raw) >= maxReintentos()) {
        log.error("Mensaje enviado a DLQ tras {} reintentos tag={} causa={}",
            maxReintentos(), tag, e.getMessage(), e);
        channel.basicNack(tag, false, false);
      } else {
        log.warn("Reintentando mensaje tag={} intento={} de {} causa={}",
            tag, muertes(raw) + 1, maxReintentos(), e.getMessage());
        channel.basicNack(tag, false, true);
      }
    }
  }

  /** Cuántas veces ya murió este mensaje (cabecera x-death que pone el DLX). */
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
