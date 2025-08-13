package com.org.promoquoter.promo;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Applies promotions to a cart:
 * - Injects all PromotionRule beans.
 * - Filters enabled PromotionDef, sorts by priority (asc).
 * - For each def, applies the first supporting rule to the CartContext.
 * - Returns audit entries from the run.
 */
@Component
public class PromotionPipeline {

  private final List<PromotionRule> rules;

  public PromotionPipeline(List<PromotionRule> rules) {
    this.rules = rules;
  }

  public PipelineResult run(CartContext ctx, List<PromotionDef> defs) {
  defs.stream()
    .filter(PromotionDef::enabled)                              // keep only enabled promos
    .sorted(Comparator.comparingInt(PromotionDef::priority))    // lowest priority runs first
    .forEach(def -> {
      rules.stream()
           .filter(r -> r.supports(def))                        // find a rule that can handle def
           .findFirst()                                         // IMPORTANT: only the first one
           .ifPresent(rule -> rule.apply(ctx, def));            // apply it to the cart context
    });

  return new PipelineResult(ctx.auditEntries());
}

  public record PipelineResult(List<String> audit) {}
}