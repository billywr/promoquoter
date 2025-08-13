package com.org.promoquoter.promo;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Component;

import com.org.promoquoter.entities.PromotionType;

@Component
public class BuyXGetYRule implements PromotionRule {

  @Override 
  public boolean supports(PromotionDef def) {
    return def.type()== PromotionType.BUY_X_GET_Y;
  }

  @Override
  public PromotionResult apply(CartContext ctx, PromotionDef def) {
    var line = ctx.lineByProduct(def.productId());
    if (line==null) return PromotionResult.none("BUY_X_GET_Y");

    int qty = line.getQty();
    int x = def.buyQty();
    int y = def.freeQty();
    int block = x + y;
    int free = (qty / block) * y; // standard formula

    BigDecimal discount = line.getUnitPrice().multiply(BigDecimal.valueOf(free)).setScale(2, RoundingMode.HALF_UP);

    if (free>0) {
      line.addDiscount(discount);
      ctx.audit("BUY_"+x+"_GET_"+y+" on "+line.getName()+": free="+free+", -"+discount);
    }

    return PromotionResult.of("BUY_X_GET_Y", discount);
  }
}
