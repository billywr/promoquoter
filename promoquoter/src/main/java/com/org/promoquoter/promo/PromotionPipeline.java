package com.org.promoquoter.promo;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class PromotionPipeline {
  private final List<PromotionRule> rules;
  public PromotionPipeline(List<PromotionRule> rules){this.rules = rules;}

  public PipelineResult run(CartContext ctx, List<PromotionDef> defs){
    defs.stream().filter(PromotionDef::enabled).sorted(Comparator.comparingInt(PromotionDef::priority)).forEach(def -> {
      rules.stream().filter(r -> r.supports(def)).findFirst().ifPresent(rule -> rule.apply(ctx, def));
    });
    return new PipelineResult(ctx.auditEntries());
  }

  public record PipelineResult(List<String> audit) {}
}