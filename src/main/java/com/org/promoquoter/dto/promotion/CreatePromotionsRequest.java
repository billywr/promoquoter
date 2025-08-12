package com.org.promoquoter.dto.promotion;

import java.math.BigDecimal;
import java.util.List;

import com.org.promoquoter.entities.PromotionType;

import jakarta.validation.constraints.NotNull;

public record CreatePromotionsRequest(List<PromotionCreate> promotions) {
  public record PromotionCreate(
    @NotNull PromotionType type,
    String name,
    @NotNull Integer priority,
    boolean enabled,
    // percent-off
    String category,
    BigDecimal percent,
    // buy-x-get-y
    Long productId,
    Integer buyQty,
    Integer freeQty
  ) {}
}
