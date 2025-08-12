package com.org.promoquoter.promo;
import java.math.BigDecimal;

public record PromotionResult(String code, BigDecimal discount, String notes) {
  public static PromotionResult of(String code, BigDecimal discount){
    return new PromotionResult(code, discount, null);
  }
  public static PromotionResult none(String code){return new PromotionResult(code, BigDecimal.ZERO, null);}  
}
