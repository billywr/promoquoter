package com.org.promoquoter.promo;

/** 
 * Contract for pluggable promotion rules. 
 * 
*/
public interface PromotionRule {

  /** 
   * Can this rule handle the given promotion definition? 
   */
  boolean supports(PromotionDef def);

   /**
    * Apply the rule to the cart and return the outcome
    * (e.g., total discount, audit).
    */
  PromotionResult apply(CartContext ctx, PromotionDef def);
}