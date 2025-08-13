package com.org.promoquoter.promo;

import org.springframework.stereotype.Component;

import com.org.promoquoter.entities.Promotion;

/** 
 * Maps JPA Promotion -> engine PromotionDef; sets default priority when null. 
 */
@Component
public class PromotionFactory {
  public PromotionDef fromEntity(Promotion e){
    return new PromotionDef(
      e.getId(), e.getType(), e.getName(), e.getPriority()==null?100:e.getPriority(), e.isEnabled(),
      e.getCategory(), e.getPercent(), e.getProductId(), e.getBuyQty(), e.getFreeQty()
    );
  }
}
