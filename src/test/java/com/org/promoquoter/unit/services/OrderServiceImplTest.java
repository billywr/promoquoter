package com.org.promoquoter.unit.services;



import com.org.promoquoter.dto.cart.CartItem;
import com.org.promoquoter.dto.cart.ConfirmRequest;
import com.org.promoquoter.dto.cart.ConfirmResponse;
import com.org.promoquoter.dto.cart.QuoteRequest;
import com.org.promoquoter.dto.cart.QuoteResponse;
import com.org.promoquoter.entities.IdempotencyRecord;
import com.org.promoquoter.entities.Order;
import com.org.promoquoter.entities.Product;
import com.org.promoquoter.repositories.IdempotencyRepository;
import com.org.promoquoter.repositories.OrderRepository;
import com.org.promoquoter.repositories.ProductRepository;
import com.org.promoquoter.services.QuotationService;
import com.org.promoquoter.services.OrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock ProductRepository productRepo;
    @Mock OrderRepository orderRepo;
    @Mock IdempotencyRepository idemRepo;
    @Mock QuotationService pricingService;

    @Captor ArgumentCaptor<QuoteRequest> quoteReqCaptor;
    @Captor ArgumentCaptor<IdempotencyRecord> idemRecordCaptor;
    @Captor ArgumentCaptor<Product> productCaptor;

    OrderServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrderServiceImpl(productRepo, orderRepo, idemRepo, pricingService);
    }

    // ---------- Helpers ----------

    private ConfirmRequest req(List<CartItem> items, String segment) {
        return new ConfirmRequest(items, segment);
    }

    private CartItem li(long productId, int qty) {
        return new CartItem(productId, qty);
    }

    private QuoteResponse quote(BigDecimal total) {
        return new QuoteResponse(List.of(), total, List.of(), List.of());
    }

    private Product product(long id, String name, int stock) {
        return Product.builder().id(id).name(name).stock(stock).build();
    }

    private Order order(String id, BigDecimal total) {
        return Order.builder().id(id).total(total).build();
    }

    // ---------- Tests ----------

    @Test
    @DisplayName("confirm(): idempotency HIT → returns existing order without quoting or reserving stock")
    void confirm_idempotencyHit_returnsExisting() {
        String idemKey = "idem-123";
        String existingOrderId = "42";
        BigDecimal existingTotal = new BigDecimal("199.99");

        when(idemRepo.findById(idemKey)).thenReturn(Optional.of(
                IdempotencyRecord.builder()
                        .idempotencyKey(idemKey)
                        .orderId(existingOrderId)
                        .createdAt(OffsetDateTime.now())
                        .requestHash("NA")
                        .build()
        ));
        when(orderRepo.findById(existingOrderId)).thenReturn(Optional.of(order(existingOrderId, existingTotal)));

        ConfirmRequest request = req(List.of(li(10L, 1)), "REGULAR");

        ConfirmResponse resp = service.confirm(request, idemKey);

        assertThat(resp.orderId()).isEqualTo(existingOrderId);
        assertThat(resp.total()).isEqualByComparingTo(existingTotal);

        verifyNoInteractions(pricingService, productRepo);
        verify(orderRepo).findById(existingOrderId);
        verifyNoMoreInteractions(orderRepo);
    }

    @Test
    @DisplayName("confirm(): idempotency MISS (with key) → quotes, reserves stock, saves order, saves idempotency record")
    void confirm_idempotencyMissWithKey_createsOrder_andSavesIdem() {
        String idemKey = "idem-456";
        when(idemRepo.findById(idemKey)).thenReturn(Optional.empty());

        BigDecimal total = new BigDecimal("1200.00");
        when(pricingService.quote(any(QuoteRequest.class))).thenReturn(quote(total));

        Product p1 = product(1L, "Laptop", 5);
        Product p2 = product(2L, "Mouse", 10);
        when(productRepo.findById(1L)).thenReturn(Optional.of(p1));
        when(productRepo.findById(2L)).thenReturn(Optional.of(p2));

        when(orderRepo.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            return Order.builder().id("999").total(o.getTotal()).build();
        });

        ConfirmRequest request = req(List.of(li(1L, 2), li(2L, 3)), "VIP");

        ConfirmResponse resp = service.confirm(request, idemKey);

        assertThat(resp.orderId()).isEqualTo("999");
        assertThat(resp.total()).isEqualByComparingTo(total);

        verify(pricingService).quote(quoteReqCaptor.capture());
        QuoteRequest sentQ = quoteReqCaptor.getValue();
        assertThat(sentQ.customerSegment()).isEqualTo("VIP");
        assertThat(sentQ.items()).hasSize(2);
        assertThat(sentQ.items().get(0).productId()).isEqualTo(1L);
        assertThat(sentQ.items().get(0).qty()).isEqualTo(2);
        assertThat(sentQ.items().get(1).productId()).isEqualTo(2L);
        assertThat(sentQ.items().get(1).qty()).isEqualTo(3);

        verify(productRepo, times(2)).save(productCaptor.capture());
        List<Product> savedProducts = productCaptor.getAllValues();
        assertThat(savedProducts.get(0).getId()).isEqualTo(1L);
        assertThat(savedProducts.get(0).getStock()).isEqualTo(3);  // 5 - 2
        assertThat(savedProducts.get(1).getId()).isEqualTo(2L);
        assertThat(savedProducts.get(1).getStock()).isEqualTo(7);  // 10 - 3

        verify(idemRepo).save(idemRecordCaptor.capture());
        IdempotencyRecord savedIdem = idemRecordCaptor.getValue();
        assertThat(savedIdem.getIdempotencyKey()).isEqualTo(idemKey);
        assertThat(savedIdem.getOrderId()).isEqualTo("999");
        assertThat(savedIdem.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("confirm(): no idempotency key → quotes, reserves stock, saves order, does NOT save idempotency record")
    void confirm_noIdemKey_createsOrder_withoutIdemRecord() {
        when(pricingService.quote(any(QuoteRequest.class))).thenReturn(quote(new BigDecimal("250.50")));

        Product p = product(10L, "Book", 4);
        when(productRepo.findById(10L)).thenReturn(Optional.of(p));

        when(orderRepo.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            return Order.builder().id("77").total(o.getTotal()).build();
        });

        ConfirmRequest request = req(List.of(li(10L, 2)), "REGULAR");

        ConfirmResponse resp = service.confirm(request, null);

        assertThat(resp.orderId()).isEqualTo("77");
        assertThat(resp.total()).isEqualByComparingTo("250.50");

        verify(idemRepo, never()).save(any());
    }

    @Test
    @DisplayName("confirm(): insufficient stock → throws; no order/idempotency saved; no product save")
    void confirm_insufficientStock_throws() {
        when(pricingService.quote(any(QuoteRequest.class))).thenReturn(quote(new BigDecimal("10.00")));

        Product p = product(5L, "Cable", 1); // stock=1
        when(productRepo.findById(5L)).thenReturn(Optional.of(p));

        ConfirmRequest request = req(List.of(li(5L, 2)), "REGULAR"); // need 2, have 1

        assertThatThrownBy(() -> service.confirm(request, "k"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Insufficient stock");

        verify(orderRepo, never()).save(any());
        verify(idemRepo, never()).save(any());
        verify(productRepo, never()).save(any());
    }

    @Test
    @DisplayName("confirm(): product not found → orElseThrow bubbles (NoSuchElementException)")
    void confirm_productMissing_throws() {
        when(pricingService.quote(any(QuoteRequest.class))).thenReturn(quote(new BigDecimal("10.00")));
        when(productRepo.findById(111L)).thenReturn(Optional.empty());

        ConfirmRequest request = req(List.of(li(111L, 1)), "REGULAR");

        assertThatThrownBy(() -> service.confirm(request, "k"))
                .isInstanceOf(java.util.NoSuchElementException.class);

        verify(orderRepo, never()).save(any());
        verify(idemRepo, never()).save(any());
        verify(productRepo, never()).save(any());
    }

    @Test
    @DisplayName("confirm(): blank idempotency key is treated as absent (no idempotency record saved)")
    void confirm_blankIdemKey_treatedAsAbsent() {
        when(pricingService.quote(any(QuoteRequest.class))).thenReturn(quote(new BigDecimal("55.00")));

        Product p = product(9L, "Pen", 3);
        when(productRepo.findById(9L)).thenReturn(Optional.of(p));

        when(orderRepo.save(any(Order.class))).thenAnswer(inv ->
                Order.builder().id("501").total(inv.getArgument(0, Order.class).getTotal()).build()
        );

        ConfirmRequest request = req(List.of(li(9L, 1)), "REGULAR");

        ConfirmResponse resp = service.confirm(request, "   "); // blank

        assertThat(resp.orderId()).isEqualTo("501");
        verify(idemRepo, never()).save(any());
    }
}
