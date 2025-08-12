package com.org.promoquoter.dto.cart;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;


public record ConfirmResponse(
  String orderId,
  BigDecimal total
) {}
