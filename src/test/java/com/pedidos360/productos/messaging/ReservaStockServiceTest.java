package com.pedidos360.productos.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pedidos360.productos.messaging.OrdenRegistradaEvent.Item;
import com.pedidos360.productos.model.Categoria;
import com.pedidos360.productos.model.Producto;
import com.pedidos360.productos.model.ReservaStock;
import com.pedidos360.productos.model.ReservaStockItem;
import com.pedidos360.productos.repository.ProductoOutboxRepository;
import com.pedidos360.productos.repository.ProductoRepository;
import com.pedidos360.productos.repository.ReservaStockRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Lógica de reserva sin BD: dedup, validación previa, orden de locks y compensación. */
@ExtendWith(MockitoExtension.class)
class ReservaStockServiceTest {

  private static final UUID A = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID B = UUID.fromString("00000000-0000-0000-0000-000000000002");

  @Mock ProductoRepository productoRepository;
  @Mock ReservaStockRepository reservaRepository;
  @Mock ProductoOutboxRepository outboxRepository;
  @InjectMocks ReservaStockService service;

  private Producto producto(UUID id, int stock) {
    Producto p = new Producto();
    p.setId(id);
    p.setNombre("Vinilo");
    p.setArtistaOMarca("Test");
    p.setCategoria(Categoria.vinilo);
    p.setPrecioClp(1000L);
    p.setStock(stock);
    return p;
  }

  private OrdenRegistradaEvent evento(UUID ordenId, List<Item> items) {
    return new OrdenRegistradaEvent(ordenId, "u1", "comprador@mail.com", items, null);
  }

  private ReservaStock marcador(UUID ordenId, UUID eventoId, String estado,
      List<ReservaStockItem> items) {
    return new ReservaStock(ordenId, eventoId, "u1", estado, null, Instant.now(), items);
  }

  @Test
  void redeliveryNoDescuentaDosVecesYReenviaElMismoEventoId() {
    UUID ordenId = UUID.randomUUID();
    UUID eventoId = UUID.randomUUID();
    when(reservaRepository.findById(ordenId))
        .thenReturn(Optional.of(marcador(ordenId, eventoId, "RESERVADA", List.of())));

    service.reservar(evento(ordenId, List.of(new Item(A, 1))));

    verify(outboxRepository).marcarPendiente(eventoId);
    verify(productoRepository, never()).descontarSiHayStock(any(), anyInt());
    verify(reservaRepository, never()).save(any());
  }

  @Test
  void multiItemSegundoSkuSinStockNoDescuentaNadaYPersisteRechazo() {
    UUID ordenId = UUID.randomUUID();
    when(reservaRepository.findById(ordenId)).thenReturn(Optional.empty());
    when(productoRepository.findByIdsForUpdate(any()))
        .thenReturn(List.of(producto(A, 10), producto(B, 0)));

    service.reservar(evento(ordenId, List.of(new Item(A, 1), new Item(B, 2))));

    verify(productoRepository, never()).descontarSiHayStock(any(), anyInt());
    ArgumentCaptor<ReservaStock> captor = ArgumentCaptor.forClass(ReservaStock.class);
    verify(reservaRepository).save(captor.capture());
    assertThat(captor.getValue().getEstado()).isEqualTo("RECHAZADA");
    assertThat(captor.getValue().getMotivo()).isEqualTo("SIN_STOCK");
    verify(outboxRepository).save(
        argThat(o -> StockTopologia.STOCK_RECHAZADO_ROUTING_KEY.equals(o.getRoutingKey())));
  }

  @Test
  void eventoSinItemsSeRechazaSinDescontar() {
    UUID ordenId = UUID.randomUUID();
    when(reservaRepository.findById(ordenId)).thenReturn(Optional.empty());

    service.reservar(evento(ordenId, List.of()));

    ArgumentCaptor<ReservaStock> captor = ArgumentCaptor.forClass(ReservaStock.class);
    verify(reservaRepository).save(captor.capture());
    assertThat(captor.getValue().getEstado()).isEqualTo("RECHAZADA");
    verify(productoRepository, never()).descontarSiHayStock(any(), anyInt());
  }

  @Test
  void locksSeAdquierenEnOrdenDeUuid() {
    UUID ordenId = UUID.randomUUID();
    when(reservaRepository.findById(ordenId)).thenReturn(Optional.empty());
    when(productoRepository.findByIdsForUpdate(any()))
        .thenReturn(List.of(producto(A, 10), producto(B, 10)));
    when(productoRepository.descontarSiHayStock(any(), anyInt())).thenReturn(1);

    service.reservar(evento(ordenId, List.of(new Item(B, 1), new Item(A, 2))));

    ArgumentCaptor<Collection<UUID>> captor = ArgumentCaptor.forClass(Collection.class);
    verify(productoRepository).findByIdsForUpdate(captor.capture());
    assertThat(new ArrayList<>(captor.getValue())).containsExactly(A, B);
  }

  @Test
  void falloTransitorioNoPersisteMarcadorNiDescuento() {
    UUID ordenId = UUID.randomUUID();
    when(reservaRepository.findById(ordenId)).thenReturn(Optional.empty());
    when(productoRepository.findByIdsForUpdate(any()))
        .thenThrow(new IllegalStateException("lock timeout"));

    assertThatThrownBy(() -> service.reservar(evento(ordenId, List.of(new Item(A, 1)))))
        .isInstanceOf(IllegalStateException.class);

    verify(reservaRepository, never()).save(any());
    verify(outboxRepository, never()).save(any());
    verify(productoRepository, never()).descontarSiHayStock(any(), anyInt());
  }

  @Test
  void dobleLiberacionNoDevuelveStockDosVeces() {
    UUID ordenId = UUID.randomUUID();
    when(reservaRepository.marcarLiberada(ordenId)).thenReturn(0);

    service.liberar(new StockLiberadoEvent(UUID.randomUUID(), ordenId, "u1", "EXPIRADA",
        Instant.now()));

    verify(productoRepository, never()).devolverStock(any(), anyInt());
  }

  @Test
  void liberacionDevuelveStockDeTodosLosItems() {
    UUID ordenId = UUID.randomUUID();
    when(reservaRepository.marcarLiberada(ordenId)).thenReturn(1);
    when(reservaRepository.findById(ordenId)).thenReturn(Optional.of(marcador(ordenId,
        UUID.randomUUID(), "LIBERADA", List.of(new ReservaStockItem(A, 2), new ReservaStockItem(B, 3)))));

    service.liberar(new StockLiberadoEvent(UUID.randomUUID(), ordenId, "u1", "CANCELADA",
        Instant.now()));

    verify(productoRepository).devolverStock(A, 2);
    verify(productoRepository).devolverStock(B, 3);
  }

  @Test
  void motivoDeLiberacionCerrado() {
    StockLiberadoEvent event = new StockLiberadoEvent(UUID.randomUUID(), UUID.randomUUID(), "u1",
        "RESERVA_TARDIA", Instant.now());

    assertThatThrownBy(() -> service.liberar(event)).isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(productoRepository);
  }
}
