package com.org.promoquoter.promo;

import java.math.BigDecimal;

import com.org.promoquoter.entities.PromotionType;

public record PromotionDef(
  Long id,
  PromotionType type,
  String name,
  int priority,
  boolean enabled,
  String category,
  BigDecimal percent,
  Long productId,
  Integer buyQty,
  Integer freeQty
) {}
