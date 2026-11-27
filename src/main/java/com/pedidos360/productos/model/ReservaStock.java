package com.pedidos360.productos.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Resultado durable de una orden.registrada: RESERVADA | RECHAZADA | LIBERADA.
 * La PK es ordenId: deduplica redeliveries sin volver a validar ni descontar.
 */
@Entity
@Table(name = "reservas_stock")
public class ReservaStock {

  @Id
  private UUID ordenId;

  @Column(nullable = false)
  private UUID eventoId;

  @Column(nullable = false)
  private String usuarioId;

  @Column(nullable = false)
  private String estado;

  private String motivo;

  @Column(nullable = false)
  private Instant ocurrioEn;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "reservas_stock_items", joinColumns = @JoinColumn(name = "orden_id"))
  private List<ReservaStockItem> items = new ArrayList<>();

  public ReservaStock() {}

  public ReservaStock(UUID ordenId, UUID eventoId, String usuarioId, String estado,
      String motivo, Instant ocurrioEn, List<ReservaStockItem> items) {
    this.ordenId = ordenId;
    this.eventoId = eventoId;
    this.usuarioId = usuarioId;
    this.estado = estado;
    this.motivo = motivo;
    this.ocurrioEn = ocurrioEn;
    this.items = new ArrayList<>(items);
  }

  public UUID getOrdenId() { return ordenId; }
  public void setOrdenId(UUID ordenId) { this.ordenId = ordenId; }

  public UUID getEventoId() { return eventoId; }
  public void setEventoId(UUID eventoId) { this.eventoId = eventoId; }

  public String getUsuarioId() { return usuarioId; }
  public void setUsuarioId(String usuarioId) { this.usuarioId = usuarioId; }

  public String getEstado() { return estado; }
  public void setEstado(String estado) { this.estado = estado; }

  public String getMotivo() { return motivo; }
  public void setMotivo(String motivo) { this.motivo = motivo; }

  public Instant getOcurrioEn() { return ocurrioEn; }
  public void setOcurrioEn(Instant ocurrioEn) { this.ocurrioEn = ocurrioEn; }

  public List<ReservaStockItem> getItems() { return items; }
  public void setItems(List<ReservaStockItem> items) { this.items = new ArrayList<>(items); }
}
