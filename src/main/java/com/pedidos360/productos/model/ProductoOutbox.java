package com.pedidos360.productos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Salida stock.* persistida antes del ACK; la PK es el eventoId estable. */
@Entity
@Table(name = "productos_outbox")
public class ProductoOutbox {

  @Id
  private UUID id;

  @Column(nullable = false)
  private String routingKey;

  @Column(nullable = false)
  private UUID ordenId;

  @Column(nullable = false)
  private String usuarioId;

  private String motivo;

  @Column(nullable = false)
  private Instant ocurrioEn;

  @Column(nullable = false)
  private String estado = "PENDIENTE";

  @Column(nullable = false)
  private int intentos;

  @Column(nullable = false)
  private Instant creadaEn = Instant.now();

  private Instant enviadaEn;

  public ProductoOutbox() {}

  public ProductoOutbox(UUID id, String routingKey, UUID ordenId, String usuarioId,
      String motivo, Instant ocurrioEn) {
    this.id = id;
    this.routingKey = routingKey;
    this.ordenId = ordenId;
    this.usuarioId = usuarioId;
    this.motivo = motivo;
    this.ocurrioEn = ocurrioEn;
  }

  public UUID getId() { return id; }
  public String getRoutingKey() { return routingKey; }
  public UUID getOrdenId() { return ordenId; }
  public String getUsuarioId() { return usuarioId; }
  public String getMotivo() { return motivo; }
  public Instant getOcurrioEn() { return ocurrioEn; }
  public String getEstado() { return estado; }
  public int getIntentos() { return intentos; }
  public Instant getCreadaEn() { return creadaEn; }
  public Instant getEnviadaEn() { return enviadaEn; }
}
