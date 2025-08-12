package com.org.promoquoter.dto.cart;

import java.math.BigDecimal;
import java.util.List;

public record QuoteResponse(
  List<QuoteItemResponse> items,
  BigDecimal total,
  List<String> appliedPromotions,
  List<String> auditTrail
) {}
