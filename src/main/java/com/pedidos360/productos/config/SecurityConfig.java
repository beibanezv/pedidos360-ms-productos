package com.pedidos360.productos.config;

import java.util.List;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:https://login.microsoftonline.com/}")
  private String issuerUri;

  @Value("${azure.tenant-id:common}")
  private String tenantId;

  @Value("${cors.allowed-origins:http://localhost:4200}")
  private String allowedOrigins;

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
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

  // CORS: orígenes configurables por env (coma-separados). En local el default
  // basta; en EC2 se pasa CORS_ALLOWED_ORIGINS con la URL https del frontend.
  // En producción el CORS grueso vive en el API Gateway (ver docs/aws-setup.md
  // del frontend); esto evita el 403 "Invalid CORS request" de Spring cuando el
  // Gateway reenvía el Origin del navegador al backend.
  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of(allowedOrigins.split(",")));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }

  @Bean
  public JwtDecoder jwtDecoder() {
    // SIMPLIFICADO: el access token real es ver 1.0 (iss sts.windows.net/<tid>/),
    // por eso se usa el JWKS v1: issuerUri + tenantId + "/discovery/keys".
    // Sin tenant real ('common') no hay fetch en startup: los GET públicos siguen OK
    // y el resto devuelve 401 sin tocar red. Con tenant real valida firma contra JWKS de Azure.
    String base = (issuerUri != null && !issuerUri.isBlank())
        ? issuerUri.trim()
        : "https://login.microsoftonline.com/";
    if (!base.endsWith("/")) {
      base += "/";
    }
    String tid = (tenantId != null && !tenantId.isBlank()) ? tenantId.trim() : "common";
    return NimbusJwtDecoder.withJwkSetUri(base + tid + "/discovery/keys").build();
  }
}
