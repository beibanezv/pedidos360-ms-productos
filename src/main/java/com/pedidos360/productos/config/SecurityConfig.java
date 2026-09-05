package com.pedidos360.productos.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:https://login.microsoftonline.com/}")
  private String issuerUri;

  @Value("${azure.tenant-id:common}")
  private String tenantId;

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.GET, "/productos", "/productos/**").permitAll()
            .requestMatchers(HttpMethod.GET, "/actuator/**").permitAll()
            .requestMatchers(HttpMethod.POST, "/productos", "/productos/**").authenticated()
            .requestMatchers(HttpMethod.PUT, "/productos/**").authenticated()
            .requestMatchers(HttpMethod.DELETE, "/productos/**").authenticated()
            .anyRequest().authenticated()
        )
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));

    return http.build();
  }

  @Bean
  public JwtDecoder jwtDecoder() {
    // SIMPLIFICADO: estilo visto en clase — jwks = issuerUri + tenantId + "/discovery/v2.0/keys".
    // Sin tenant real ('common') no hay fetch en startup: los GET públicos siguen OK
    // y el resto devuelve 401 sin tocar red. Con tenant real valida firma contra JWKS de Azure.
    String base = (issuerUri != null && !issuerUri.isBlank())
        ? issuerUri.trim()
        : "https://login.microsoftonline.com/";
    if (!base.endsWith("/")) {
      base += "/";
    }
    String tid = (tenantId != null && !tenantId.isBlank()) ? tenantId.trim() : "common";
    return NimbusJwtDecoder.withJwkSetUri(base + tid + "/discovery/v2.0/keys").build();
  }
}
