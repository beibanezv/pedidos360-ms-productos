package com.pedidos360.productos.messaging;

import com.pedidos360.productos.messaging.OrdenRegistradaEvent.Item;
import com.pedidos360.productos.model.Producto;
import com.pedidos360.productos.model.ProductoOutbox;
import com.pedidos360.productos.model.ReservaStock;
import com.pedidos360.productos.model.ReservaStockItem;
import com.pedidos360.productos.repository.ProductoOutboxRepository;
import com.pedidos360.productos.repository.ProductoRepository;
import com.pedidos360.productos.repository.ReservaStockRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reserva y compensación de stock en una transacción real (bean separado:
 * StockConsumer no puede auto-invocar @Transactional). Regla financiera:
 * una salida tardía NUNCA libera stock pagado; B07 solo produce stock.reservado
 * y stock.rechazado, la compensación llega como stock.liberado desde orders.
 */
@Service
public class ReservaStockService {

  private final ProductoRepository productoRepository;
  private final ReservaStockRepository reservaRepository;
  private final ProductoOutboxRepository outboxRepository;

  public ReservaStockService(ProductoRepository productoRepository,
      ReservaStockRepository reservaRepository, ProductoOutboxRepository outboxRepository) {
    this.productoRepository = productoRepository;
    this.reservaRepository = reservaRepository;
    this.outboxRepository = outboxRepository;
  }

  @Transactional
  public void reservar(OrdenRegistradaEvent event) {
    productoRepository.lockOrden(event.ordenId().toString());
    ReservaStock previo = reservaRepository.findById(event.ordenId()).orElse(null);
    if (previo != null) {
      outboxRepository.marcarPendiente(previo.getEventoId());
      return;
    }

    Instant ahora = Instant.now();
    Map<UUID, Integer> cantidades = new TreeMap<>();
    for (Item item : event.items()) {
      if (item == null || item.productoId() == null || item.cantidad() <= 0) {
        persistirRechazo(event, ahora, "SKU_NO_DISPONIBLE", List.of());
        return;
      }
      cantidades.merge(item.productoId(), item.cantidad(), Math::addExact);
    }
    if (cantidades.isEmpty()) {
      persistirRechazo(event, ahora, "SKU_NO_DISPONIBLE", List.of());
      return;
    }

    List<UUID> ids = new ArrayList<>(cantidades.keySet());
    Collections.sort(ids);
    List<Producto> productos = productoRepository.findByIdsForUpdate(ids);
    Map<UUID, Producto> porId = productos.stream()
        .collect(Collectors.toMap(Producto::getId, Function.identity()));

    String motivo = null;
    for (UUID id : ids) {
      Producto producto = porId.get(id);
      if (producto == null) {
        motivo = "SKU_NO_DISPONIBLE";
        break;
      }
      if (producto.getStock() == null || producto.getStock() < cantidades.get(id)) {
        motivo = "SIN_STOCK";
        break;
      }
    }
    List<ReservaStockItem> items = ids.stream()
        .map(id -> new ReservaStockItem(id, cantidades.get(id)))
        .collect(Collectors.toList());

    if (motivo != null) {
      // Sin ningún descuento: el rechazo es durable y se reproduce con su eventoId.
      persistirRechazo(event, ahora, motivo, items);
      return;
    }

    for (UUID id : ids) {
      if (productoRepository.descontarSiHayStock(id, cantidades.get(id)) != 1) {
        throw new IllegalStateException("El stock cambió al descontar " + id);
      }
    }
    UUID eventoId = UUID.randomUUID();
    reservaRepository.save(new ReservaStock(event.ordenId(), eventoId, event.usuarioId(),
        "RESERVADA", null, ahora, items));
    outboxRepository.save(new ProductoOutbox(eventoId, StockTopologia.STOCK_RESERVADO_ROUTING_KEY,
        event.ordenId(), event.usuarioId(), null, ahora));
  }

  private void persistirRechazo(OrdenRegistradaEvent event, Instant ahora, String motivo,
      List<ReservaStockItem> items) {
    UUID eventoId = UUID.randomUUID();
    reservaRepository.save(new ReservaStock(event.ordenId(), eventoId, event.usuarioId(),
        "RECHAZADA", motivo, ahora, items));
    outboxRepository.save(new ProductoOutbox(eventoId, StockTopologia.STOCK_RECHAZADO_ROUTING_KEY,
        event.ordenId(), event.usuarioId(), motivo, ahora));
  }

  @Transactional
  public void liberar(StockLiberadoEvent event) {
    if (!"EXPIRADA".equals(event.motivo()) && !"CANCELADA".equals(event.motivo())) {
      throw new IllegalArgumentException("Motivo de liberación inválido: " + event.motivo());
    }
    productoRepository.lockOrden(event.ordenId().toString());
    if (reservaRepository.marcarLiberada(event.ordenId()) != 1) {
      return; // doble liberación: no se devuelve stock dos veces
    }
    ReservaStock reserva = reservaRepository.findById(event.ordenId())
        .orElseThrow(() -> new IllegalStateException("Reserva desapareció al liberar"));
    for (ReservaStockItem item : reserva.getItems()) {
      productoRepository.devolverStock(item.getProductoId(), item.getCantidad());
    }
  }
}
