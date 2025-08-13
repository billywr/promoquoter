package com.org.promoquoter.dto.product;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record ProductsRequest(List<CreateProduct> products) {
  public record CreateProduct(
    @NotBlank String name,
    @NotBlank String category,
    @Positive BigDecimal price,
    @PositiveOrZero int stock
  ) {}
}
