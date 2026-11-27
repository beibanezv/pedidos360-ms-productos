package com.pedidos360.productos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MsProductosApplication {
  public static void main(String[] args) {
    SpringApplication.run(MsProductosApplication.class, args);
  }
}
