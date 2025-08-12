package com.org.promoquoter.config;

import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

@OpenAPIDefinition(
  info = @Info(
    title = "PromoQuoter API",
    version = "v1",
    description = "Cart pricing & reservation microservice with pluggable promotions"
  )
)
@Configuration
public class OpenApiConfig { }
