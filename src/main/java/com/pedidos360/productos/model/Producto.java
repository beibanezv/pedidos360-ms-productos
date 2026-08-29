package com.pedidos360.productos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Entity
@Table(name = "productos")
public class Producto {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private UUID id;

  @NotBlank
  @Column(nullable = false)
  private String nombre;

  @NotBlank
  @Column(nullable = false)
  private String artistaOMarca;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Categoria categoria;

  @NotNull
  @Min(0)
  @Column(nullable = false)
  private Long precioClp;

  @NotNull
  @Min(0)
  @Column(nullable = false)
  private Integer stock;

  @Column(length = 2000)
  private String descripcion;

  public Producto() {}

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }

  public String getNombre() { return nombre; }
  public void setNombre(String nombre) { this.nombre = nombre; }

  public String getArtistaOMarca() { return artistaOMarca; }
  public void setArtistaOMarca(String artistaOMarca) { this.artistaOMarca = artistaOMarca; }

  public Categoria getCategoria() { return categoria; }
  public void setCategoria(Categoria categoria) { this.categoria = categoria; }

  public Long getPrecioClp() { return precioClp; }
  public void setPrecioClp(Long precioClp) { this.precioClp = precioClp; }

  public Integer getStock() { return stock; }
  public void setStock(Integer stock) { this.stock = stock; }

  public String getDescripcion() { return descripcion; }
  public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
}
