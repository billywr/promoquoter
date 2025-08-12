package com.org.promoquoter.dto.cart;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

public record QuoteRequest(
  @NotEmpty @Valid  List<CartItem> items,
  @NotBlank String customerSegment
) {}
