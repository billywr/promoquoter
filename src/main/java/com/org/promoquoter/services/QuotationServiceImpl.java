package com.org.promoquoter.services;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.org.promoquoter.dto.cart.CartItem;
import com.org.promoquoter.dto.cart.QuoteItemResponse;
import com.org.promoquoter.dto.cart.QuoteRequest;
import com.org.promoquoter.dto.cart.QuoteResponse;
import com.org.promoquoter.entities.Product;
import com.org.promoquoter.promo.CartContext;
import com.org.promoquoter.promo.CartLine;
import com.org.promoquoter.promo.PromotionDef;
import com.org.promoquoter.promo.PromotionFactory;
import com.org.promoquoter.promo.PromotionPipeline;
import com.org.promoquoter.repositories.ProductRepository;
import com.org.promoquoter.repositories.PromotionRepository;

@Service
public class QuotationServiceImpl implements QuotationService {

  private final ProductRepository productRepo;
  private final PromotionRepository promoRepo;
  private final PromotionFactory factory;
  private final PromotionPipeline pipeline;

  public QuotationServiceImpl(ProductRepository productRepo,
                               PromotionRepository promoRepo,
                               PromotionFactory factory,
                               PromotionPipeline pipeline) {
    this.productRepo = productRepo;
    this.promoRepo = promoRepo;
    this.factory = factory;
    this.pipeline = pipeline;
  }

  @Override
  @Transactional(readOnly = true)
  public QuoteResponse quote(QuoteRequest req) {
    
    var products = productRepo.findAllById(
        req.items().stream().map(CartItem::productId).toList()
    );
    Map<Long, Product> map = new HashMap<>();
    products.forEach(p -> map.put(p.getId(), p));

    List<CartLine> lines = new ArrayList<>();
    for (var item : req.items()) {
      var p = map.get(item.productId());
      if (p == null) {
        throw new IllegalArgumentException("Product not found: " + item.productId());
      }
      lines.add(new CartLine(p.getId(), p.getName(), item.qty(), p.getPrice()));
    }

    var ctx = new CartContext(lines);
    var defs = promoRepo.findAll().stream().map(factory::fromEntity).toList();
    var pipeRes = pipeline.run(ctx, defs);

    List<QuoteItemResponse> items = lines.stream().map(l ->
        new QuoteItemResponse(
            l.getProductId(),
            l.getName(),
            l.getQty(),
            l.getUnitPrice(),
            l.getOriginalSubtotal(),
            l.getDiscount(),
            l.getFinalSubtotal()
        )
    ).toList();

    BigDecimal total = ctx.total();
    return new QuoteResponse(
        items,
        total,
        defs.stream().map(PromotionDef::name).toList(),
        pipeRes.audit()
    );
  }
}
