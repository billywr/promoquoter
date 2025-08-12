package com.org.promoquoter.controllers;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.org.promoquoter.dto.promotion.CreatePromotionsRequest;
import com.org.promoquoter.entities.Promotion;
import com.org.promoquoter.repositories.PromotionRepository;

import java.util.*;

@RestController
@RequestMapping("/promotions")
public class PromotionController {
  private final PromotionRepository repo;
  public PromotionController(PromotionRepository repo){this.repo = repo;}

  @PostMapping
  public ResponseEntity<?> create(@Valid @RequestBody CreatePromotionsRequest req){
    var saved = new ArrayList<Promotion>();
    for (var r : req.promotions()) {
      saved.add(repo.save(Promotion.builder()
        .type(r.type()).name(r.name()).priority(r.priority()).enabled(r.enabled())
        .category(r.category()).percent(r.percent())
        .productId(r.productId()).buyQty(r.buyQty()).freeQty(r.freeQty())
        .build()));
    }
    return ResponseEntity.ok(saved);
  }
}
