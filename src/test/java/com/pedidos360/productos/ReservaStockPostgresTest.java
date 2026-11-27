package com.pedidos360.productos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.pedidos360.productos.messaging.OrdenRegistradaEvent;
import com.pedidos360.productos.messaging.OrdenRegistradaEvent.Item;
import com.pedidos360.productos.messaging.ReservaStockService;
import com.pedidos360.productos.messaging.StockLiberadoEvent;
import com.pedidos360.productos.messaging.StockTopologia;
import com.pedidos360.productos.model.Categoria;
import com.pedidos360.productos.model.Producto;
import com.pedidos360.productos.model.ProductoOutbox;
import com.pedidos360.productos.model.ReservaStock;
import com.pedidos360.productos.repository.ProductoOutboxRepository;
import com.pedidos360.productos.repository.ProductoRepository;
import com.pedidos360.productos.repository.ReservaStockRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Concurrencia/rollback en PostgreSQL desechable. Nunca apunta al RDS de negocio:
 * requiere P360_TEST_POSTGRES_URL con una base creada para la prueba.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ReservaStockService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfEnvironmentVariable(named = "P360_TEST_POSTGRES_URL", matches = ".+")
@TestPropertySource(properties = {
    "spring.datasource.url=${P360_TEST_POSTGRES_URL}",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.sql.init.mode=never"
})
class ReservaStockPostgresTest {

  @Autowired ReservaStockService service;
  @Autowired ProductoRepository productos;
  @Autowired ReservaStockRepository reservas;
  @SpyBean ProductoOutboxRepository outbox;

  private void limpiar() {
    outbox.deleteAllInBatch();
    reservas.deleteAll(); // cascada la tabla de items
    productos.deleteAllInBatch();
  }

  private Producto producto(int stock) {
    Producto p = new Producto();
    p.setNombre("Vinilo de prueba");
    p.setArtistaOMarca("Test");
    p.setCategoria(Categoria.vinilo);
    p.setPrecioClp(1000L);
    p.setStock(stock);
    return productos.saveAndFlush(p);
  }

  private OrdenRegistradaEvent evento(UUID ordenId, Item... items) {
    return new OrdenRegistradaEvent(ordenId, "u1", "comprador@mail.com", List.of(items), null);
  }

  @Test
  void redeliveryDespuesDelCommitNoDescuentaDosVeces() {
    limpiar();
    Producto p = producto(10);
    UUID ordenId = UUID.randomUUID();
    service.reservar(evento(ordenId, new Item(p.getId(), 3)));

    service.reservar(evento(ordenId, new Item(p.getId(), 3)));

    assertThat(productos.findById(p.getId()).orElseThrow().getStock()).isEqualTo(7);
    assertThat(reservas.findAll()).hasSize(1);
    List<ProductoOutbox> salidas = outbox.findAll();
    assertThat(salidas).hasSize(1);
    assertThat(salidas.get(0).getId()).isEqualTo(reservas.findAll().get(0).getEventoId());
    assertThat(salidas.get(0).getEstado()).isEqualTo("PENDIENTE");
  }

  @Test
  void dosOrdenesPorLaUltimaUnidadUnaReservaOtraRechazoDuradero() throws Exception {
    limpiar();
    Producto p = producto(1);
    var start = new CountDownLatch(1);
    var tareas = new ArrayList<Future<?>>();
    var pool = Executors.newFixedThreadPool(2);
    try {
      for (int i = 0; i < 2; i++) {
        UUID ordenId = UUID.randomUUID();
        tareas.add(pool.submit(() -> {
          start.await();
          service.reservar(evento(ordenId, new Item(p.getId(), 1)));
          return null;
        }));
      }
      start.countDown();
      for (Future<?> tarea : tareas) tarea.get(15, TimeUnit.SECONDS);
    } finally {
      pool.shutdownNow();
    }

    assertThat(productos.findById(p.getId()).orElseThrow().getStock()).isZero();
    List<ReservaStock> marcadores = reservas.findAll();
    assertThat(marcadores).hasSize(2);
    assertThat(marcadores).extracting(ReservaStock::getEstado)
        .containsExactlyInAnyOrder("RESERVADA", "RECHAZADA");
    assertThat(outbox.findAll()).extracting(ProductoOutbox::getRoutingKey)
        .containsExactlyInAnyOrder(StockTopologia.STOCK_RESERVADO_ROUTING_KEY,
            StockTopologia.STOCK_RECHAZADO_ROUTING_KEY);
  }

  @Test
  void multiItemSegundoSkuSinStockNoDescuentaNadaYPersisteRechazo() {
    limpiar();
    Producto conStock = producto(10);
    Producto sinStock = producto(0);
    UUID ordenId = UUID.randomUUID();

    service.reservar(evento(ordenId, new Item(conStock.getId(), 2), new Item(sinStock.getId(), 1)));

    assertThat(productos.findById(conStock.getId()).orElseThrow().getStock()).isEqualTo(10);
    assertThat(productos.findById(sinStock.getId()).orElseThrow().getStock()).isZero();
    ReservaStock marcador = reservas.findById(ordenId).orElseThrow();
    assertThat(marcador.getEstado()).isEqualTo("RECHAZADA");
    assertThat(marcador.getMotivo()).isEqualTo("SIN_STOCK");
    assertThat(outbox.findAll()).hasSize(1);
    assertThat(outbox.findAll().get(0).getRoutingKey())
        .isEqualTo(StockTopologia.STOCK_RECHAZADO_ROUTING_KEY);
  }

  @Test
  void falloTransitorioRevierteMarcadorYTodoDescuento() {
    limpiar();
    Producto p = producto(10);
    doThrow(new IllegalStateException("BD caída")).when(outbox).save(any(ProductoOutbox.class));

    assertThatThrownBy(() -> service.reservar(
        evento(UUID.randomUUID(), new Item(p.getId(), 3))))
        .isInstanceOf(IllegalStateException.class);

    assertThat(reservas.findAll()).isEmpty();
    assertThat(outbox.findAll()).isEmpty();
    assertThat(productos.findById(p.getId()).orElseThrow().getStock()).isEqualTo(10);
  }

  @Test
  void outboxReenviaMismoEventoIdTrasReinicio() {
    limpiar();
    Producto p = producto(5);
    UUID ordenId = UUID.randomUUID();
    service.reservar(evento(ordenId, new Item(p.getId(), 1)));

    UUID eventoId = outbox.findAll().get(0).getId();
    outbox.marcarEnviado(eventoId, Instant.now());
    service.reservar(evento(ordenId, new Item(p.getId(), 1))); // simulación de reinicio

    assertThat(outbox.findAll()).hasSize(1);
    assertThat(outbox.findAll().get(0).getId()).isEqualTo(eventoId);
    assertThat(outbox.findAll().get(0).getEstado()).isEqualTo("PENDIENTE");
    assertThat(productos.findById(p.getId()).orElseThrow().getStock()).isEqualTo(4);
  }
}
