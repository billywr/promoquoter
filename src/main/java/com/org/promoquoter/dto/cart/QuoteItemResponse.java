package com.org.promoquoter.dto.cart;

import java.math.BigDecimal;

public record QuoteItemResponse(
  Long productId,
  String name,
  int qty,
  BigDecimal unitPrice,
  BigDecimal originalSubtotal,
  BigDecimal discount,
  BigDecimal finalSubtotal
) {}
