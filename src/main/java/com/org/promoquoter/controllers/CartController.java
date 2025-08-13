package com.org.promoquoter.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.org.promoquoter.dto.cart.ConfirmRequest;
import com.org.promoquoter.dto.cart.ConfirmResponse;
import com.org.promoquoter.dto.cart.QuoteRequest;
import com.org.promoquoter.dto.cart.QuoteResponse;
import com.org.promoquoter.services.OrderService;
import com.org.promoquoter.services.QuotationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/cart")
public class CartController {
  private final QuotationService pricingService;
  private final OrderService orderService;

  public CartController(QuotationService pricingService, OrderService orderService){
    this.pricingService = pricingService; this.orderService = orderService;
  }

  @PostMapping("/quote")
  public ResponseEntity<QuoteResponse> quote(@Valid @RequestBody QuoteRequest req){
    return ResponseEntity.ok(pricingService.quote(req));
  }

  @PostMapping("/confirm")
  public ResponseEntity<ConfirmResponse> confirm(@RequestHeader(name = "Idempotency-Key", required = false) String idemKey, @Valid @RequestBody ConfirmRequest req){
    return ResponseEntity.ok(orderService.confirm(req, idemKey));
  }
}