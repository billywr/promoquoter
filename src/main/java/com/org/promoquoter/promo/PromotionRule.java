package com.org.promoquoter.promo;

public interface PromotionRule {
  boolean supports(PromotionDef def);
  PromotionResult apply(CartContext ctx, PromotionDef def);
}