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

  @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}")
  private String issuerUri;

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
    // SIMPLIFICADO: issuer-uri viene de AZURE_TENANT_JWKS_URI (variable de entorno).
    // En local/test sin tenant real, NO hacer discovery contra Azure al arrancar
    // (Azure con "common" devuelve issuer "{tenantid}" y withIssuerLocation falla).
    // Solo cuando haya un tenant UUID real se hace withIssuerLocation (valida firma contra JWKS).
    String issuer = (issuerUri != null && !issuerUri.isBlank())
        ? issuerUri.trim()
        : "";

    boolean esPlaceholder = issuer.isEmpty()
        || issuer.contains("{")
        || issuer.contains("tenantid")
        || issuer.contains("__")
        || issuer.contains("common")
        || issuer.contains("REEMPLAZA")
        || issuer.contains("TENANT_ID");

    // Heuristica simple: un issuer real de Azure contiene un UUID
    boolean pareceUuid = issuer.matches(".*[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}.*");

    if (esPlaceholder || !pareceUuid) {
      // Local/dev: decoder que no hace fetch en startup. Solo intentará descargar JWK si llega un JWT.
      // GET /productos sigue público; POST/PUT/DELETE sin token -> 401 sin tocar red.
      String jwkSetUri = "https://login.microsoftonline.com/common/discovery/v2.0/keys";
      return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    }

    return NimbusJwtDecoder.withIssuerLocation(issuer).build();
  }
}
