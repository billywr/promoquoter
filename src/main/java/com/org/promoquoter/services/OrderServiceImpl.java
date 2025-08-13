package com.org.promoquoter.services;

import java.time.OffsetDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.org.promoquoter.dto.cart.ConfirmRequest;
import com.org.promoquoter.dto.cart.ConfirmResponse;
import com.org.promoquoter.dto.cart.QuoteRequest;
import com.org.promoquoter.entities.IdempotencyRecord;
import com.org.promoquoter.entities.Order;
import com.org.promoquoter.repositories.IdempotencyRepository;
import com.org.promoquoter.repositories.OrderRepository;
import com.org.promoquoter.repositories.ProductRepository;

@Service
public class OrderServiceImpl implements OrderService {
  private final ProductRepository productRepo;
  private final OrderRepository orderRepo;
  private final IdempotencyRepository idemRepo;
  private final QuotationService pricingService;

  public OrderServiceImpl(ProductRepository productRepo, OrderRepository orderRepo, IdempotencyRepository idemRepo,
      QuotationService pricingService) {
        this.productRepo = productRepo;
        this.orderRepo = orderRepo;
        this.idemRepo = idemRepo;
        this.pricingService = pricingService;
  }

  @Override
  @Transactional
  public ConfirmResponse confirm(ConfirmRequest req, String idemKey) {
    // Idempotency check, if key is present just return the previous order
    if (idemKey != null && !idemKey.isBlank()) {
      var previousOrder = idemRepo.findById(idemKey).orElse(null);
      if (previousOrder != null) {
        var order = orderRepo.findById(previousOrder.getOrderId()).orElseThrow();
        return new ConfirmResponse(order.getId(), order.getTotal());
      }
    }

    var quote = pricingService.quote(new QuoteRequest(req.items(), req.customerSegment()));

    // Reserve inventory (optimistic locking via @Version on Product)
    for (var item : req.items()) {
      var product = productRepo.findById(item.productId()).orElseThrow();
      if (product.getStock() < item.qty()) {
        throw new RuntimeException("Insufficient stock for " + product.getName());
      }
      product.setStock(product.getStock() - item.qty());
      productRepo.save(product);
    }

    // Persist order
    var order = Order.builder().total(quote.total()).build();
    var saved = orderRepo.save(order);

    // Persist idempotency record
    if (idemKey != null && !idemKey.isBlank()) {
      var idempotencyRecord = IdempotencyRecord.builder()
          .idempotencyKey(idemKey)
          .orderId(saved.getId())
          .createdAt(OffsetDateTime.now())
          .requestHash("NA")
          .build();
      idemRepo.save(idempotencyRecord);
    }

    return new ConfirmResponse(saved.getId(), quote.total());
  }
}
