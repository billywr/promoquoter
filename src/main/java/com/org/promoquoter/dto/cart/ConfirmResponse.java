package com.org.promoquoter.dto.cart;

import java.math.BigDecimal;

public record ConfirmResponse(
  String orderId,
  BigDecimal total
) {}
