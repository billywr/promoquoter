package com.org.promoquoter.dto.cart;

import jakarta.validation.constraints.*;

public record CartItem(
  @NotNull Long productId,
  @Positive int qty
) {}