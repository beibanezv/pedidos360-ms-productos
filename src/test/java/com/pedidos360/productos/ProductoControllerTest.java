package com.pedidos360.productos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.productos.config.SecurityConfig;
import com.pedidos360.productos.model.Categoria;
import com.pedidos360.productos.model.Producto;
import com.pedidos360.productos.repository.ProductoRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@WebMvcTest(com.pedidos360.productos.controller.ProductoController.class)
@Import(SecurityConfig.class)
class ProductoControllerTest {

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;

  @MockBean ProductoRepository repository;
  @MockBean JwtDecoder jwtDecoder;

  private Producto productoEjemplo() {
    Producto p = new Producto();
    p.setId(UUID.randomUUID());
    p.setNombre("Abbey Road");
    p.setArtistaOMarca("The Beatles");
    p.setCategoria(Categoria.vinilo);
    p.setPrecioClp(29990L);
    p.setStock(10);
    p.setDescripcion("Vinilo 180g remasterizado");
    return p;
  }

  @Test
  void getProductos_publico_retornaLista() throws Exception {
    Producto p = productoEjemplo();
    when(repository.findAll()).thenReturn(List.of(p));

    mockMvc.perform(get("/productos"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].nombre").value("Abbey Road"))
        .andExpect(jsonPath("$[0].categoria").value("vinilo"));

    Mockito.verify(repository).findAll();
  }

  @Test
  void getProductos_conFiltroCategoria_usaFindByCategoria() throws Exception {
    Producto p = productoEjemplo();
    p.setCategoria(Categoria.tornamesa);
    when(repository.findByCategoria(Categoria.tornamesa)).thenReturn(List.of(p));

    mockMvc.perform(get("/productos").param("categoria", "tornamesa"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].categoria").value("tornamesa"));

    Mockito.verify(repository).findByCategoria(Categoria.tornamesa);
  }

  @Test
  void postProductos_sinJwt_retorna401() throws Exception {
    Producto p = productoEjemplo();
    p.setId(null);

    mockMvc.perform(post("/productos")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(p)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void postProductos_conJwtAdmin_retorna201() throws Exception {
    Producto p = productoEjemplo();
    Producto guardado = productoEjemplo();
    when(repository.save(any(Producto.class))).thenReturn(guardado);

    Producto payload = productoEjemplo();
    payload.setId(null);

    mockMvc.perform(post("/productos")
            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Admin")))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(payload)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.nombre").value("Abbey Road"));
  }

  @Test
  void postProductos_conJwtSinRolAdmin_retorna403() throws Exception {
    Producto payload = productoEjemplo();
    payload.setId(null);

    mockMvc.perform(post("/productos")
            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Cliente")))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(payload)))
        .andExpect(status().isForbidden());

    Mockito.verify(repository, Mockito.never()).save(any(Producto.class));
  }

  @Test
  void putProductos_sinJwt_retorna401() throws Exception {
    Producto p = productoEjemplo();
    mockMvc.perform(put("/productos/{id}", UUID.randomUUID())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(p)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void getProductoPorId_existente_retorna200() throws Exception {
    Producto p = productoEjemplo();
    when(repository.findById(p.getId())).thenReturn(Optional.of(p));

    mockMvc.perform(get("/productos/{id}", p.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nombre").value("Abbey Road"));
  }

  @Test
  void getProductoPorId_inexistente_retorna404() throws Exception {
    when(repository.findById(any())).thenReturn(Optional.empty());

    mockMvc.perform(get("/productos/{id}", UUID.randomUUID()))
        .andExpect(status().isNotFound());
  }
}
