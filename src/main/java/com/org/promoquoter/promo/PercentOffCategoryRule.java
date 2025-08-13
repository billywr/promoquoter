package com.org.promoquoter.promo;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Component;

import com.org.promoquoter.entities.PromotionType;

@Component
public class PercentOffCategoryRule implements PromotionRule {

  @Override 
  public boolean supports(PromotionDef def){
    return def.type()== PromotionType.PERCENT_OFF_CATEGORY;
  }

  @Override
  public PromotionResult apply(CartContext ctx, PromotionDef def) {
    BigDecimal totalDiscount = BigDecimal.ZERO;
    for (var line : ctx.linesInCategory(def.category())) {

      BigDecimal lineDiscount = line.getOriginalSubtotal()
        .multiply(def.percent())
        .divide(BigDecimal.valueOf(100))
        .setScale(2, RoundingMode.HALF_UP);

      if (lineDiscount.signum()>0) {
        line.addDiscount(lineDiscount);
        ctx.audit("PERCENT_OFF_CATEGORY("+def.percent()+"%) on "+line.getName()+": -"+lineDiscount);
        totalDiscount = totalDiscount.add(lineDiscount);
      }
    }
    return PromotionResult.of("PERCENT_OFF_CATEGORY", totalDiscount);
  }
}
