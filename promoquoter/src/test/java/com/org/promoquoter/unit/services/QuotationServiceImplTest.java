package com.org.promoquoter.unit.services;

import com.org.promoquoter.dto.cart.*;
import com.org.promoquoter.entities.Product;
import com.org.promoquoter.entities.Promotion;
import com.org.promoquoter.entities.PromotionType;
import com.org.promoquoter.promo.PromotionDef;
import com.org.promoquoter.promo.PromotionPipeline;
import com.org.promoquoter.promo.PromotionFactory;
import com.org.promoquoter.repositories.ProductRepository;
import com.org.promoquoter.repositories.PromotionRepository;
import com.org.promoquoter.services.QuotationServiceImpl;
import com.org.promoquoter.promo.CartContext;
import com.org.promoquoter.promo.CartLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for QuotationServiceImpl (no Spring).
 */
@ExtendWith(MockitoExtension.class)
class QuotationServiceImplTest {

    @Mock ProductRepository productRepo;
    @Mock PromotionRepository promoRepo;
    @Mock PromotionPipeline pipeline;
    @Mock PromotionFactory factory;

    @Captor ArgumentCaptor<Iterable<Long>> idsCaptor;
    @Captor ArgumentCaptor<CartContext> ctxCaptor;
    @Captor ArgumentCaptor<List<PromotionDef>> defsCaptor;

    QuotationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new QuotationServiceImpl(productRepo, promoRepo, factory, pipeline);
    }

    // ----------------- Helpers -----------------

    private Product product(long id, String name, String price) {
        return Product.builder()
                .id(id)
                .name(name)
                .category("DEFAULT")
                .price(new BigDecimal(price))
                .stock(1_000)
                .version(0L)
                .build();
    }

    private QuoteRequest req(List<CartItem> items) {
        return new QuoteRequest(items, "REGULAR");
    }

    private CartItem li(long pid, int qty) { return new CartItem(pid, qty); }

    private PromotionDef def(long id, String name) {
        return new PromotionDef(id, PromotionType.PERCENT_OFF_CATEGORY, name, 1, true, null,
                BigDecimal.ZERO, null, null, null);
    }

    // ----------------- Tests -----------------

    @Test
    @DisplayName("quote(): happy path with NO promotions → maps lines, totals correctly, no audit")
    void quote_happy_noPromotions() {
        // products in repo
        var p1 = product(1L, "P1", "19.99");
        var p2 = product(2L, "P2", "5.50");
        when(productRepo.findAllById(anyIterable())).thenReturn(List.of(p1, p2));

        // no promotions
        when(promoRepo.findAll()).thenReturn(List.of());

        // pipeline returns audit as-is and doesn't touch discounts
        when(pipeline.run(any(CartContext.class), anyList()))
                .thenAnswer(inv -> {
                    CartContext ctx = inv.getArgument(0);
                    return new PromotionPipeline.PipelineResult(ctx.auditEntries());
                });

        var request = req(List.of(li(1L, 2), li(2L, 3)));
        var res = service.quote(request);

        // called with the right IDs
        verify(productRepo).findAllById(idsCaptor.capture());
        assertThat(idsCaptor.getValue()).containsExactlyInAnyOrder(1L, 2L);

        // items mapped
        assertThat(res.items()).hasSize(2);
        QuoteItemResponse i0 = res.items().get(0);
        QuoteItemResponse i1 = res.items().get(1);

        assertThat(i0).extracting(QuoteItemResponse::productId, QuoteItemResponse::name, QuoteItemResponse::qty)
                      .containsExactly(1L, "P1", 2);
        assertThat(i0.unitPrice()).isEqualByComparingTo("19.99");
        assertThat(i0.originalSubtotal()).isEqualByComparingTo("39.98");
        assertThat(i0.discount()).isEqualByComparingTo("0");
        assertThat(i0.finalSubtotal()).isEqualByComparingTo("39.98");

        assertThat(i1).extracting(QuoteItemResponse::productId, QuoteItemResponse::name, QuoteItemResponse::qty)
                      .containsExactly(2L, "P2", 3);
        assertThat(i1.originalSubtotal()).isEqualByComparingTo("16.50");
        assertThat(i1.discount()).isEqualByComparingTo("0");
        assertThat(i1.finalSubtotal()).isEqualByComparingTo("16.50");

        // total
        assertThat(res.total()).isEqualByComparingTo("56.48"); // 39.98 + 16.50

        // promotions/audit empty
        assertThat(res.appliedPromotions()).isEmpty();
        assertThat(res.auditTrail()).isEmpty();

        // verify pipeline invoked
        verify(pipeline).run(any(CartContext.class), argThat(List::isEmpty));
    }

    @Test
    @DisplayName("quote(): promotions present → factory mapping, pipeline applies discounts + audit, appliedPromotions returned")
    void quote_promotions_pipelineAppliesDiscounts_andAudit() {
        var p1 = product(1L, "Laptop", "1000.00");
        var p2 = product(2L, "Mouse", "25.00");
        when(productRepo.findAllById(anyIterable())).thenReturn(List.of(p1, p2));

        // Entities from repo -> mapped via factory to defs
        Promotion e1 = mock(Promotion.class);
        Promotion e2 = mock(Promotion.class);
        when(promoRepo.findAll()).thenReturn(List.of(e1, e2));
        when(factory.fromEntity(e1)).thenReturn(def(10L, "PromoA"));
        when(factory.fromEntity(e2)).thenReturn(def(20L, "PromoB"));

        // pipeline mutates context: add discounts & audit
        when(pipeline.run(any(CartContext.class), anyList()))
                .thenAnswer(inv -> {
                    CartContext ctx = inv.getArgument(0);
                    CartLine laptop = ctx.lineByProduct(1L);
                    CartLine mouse  = ctx.lineByProduct(2L);
                    laptop.addDiscount(new BigDecimal("100.00")); // 10% off
                    mouse.addDiscount(new BigDecimal("25.00"));   // free
                    ctx.audit("Applied 10% off and free mouse");
                    return new PromotionPipeline.PipelineResult(ctx.auditEntries());
                });

        var res = service.quote(req(List.of(li(1L, 1), li(2L, 1))));

        // verify pipeline received defs mapped by factory
        verify(pipeline).run(ctxCaptor.capture(), defsCaptor.capture());
        List<PromotionDef> passedDefs = defsCaptor.getValue();
        assertThat(passedDefs).extracting(PromotionDef::name).containsExactly("PromoA", "PromoB");

        // item subtotals reflect discounts
        QuoteItemResponse laptop = res.items().stream().filter(i -> i.productId() == 1L).findFirst().orElseThrow();
        QuoteItemResponse mouse  = res.items().stream().filter(i -> i.productId() == 2L).findFirst().orElseThrow();

        assertThat(laptop.originalSubtotal()).isEqualByComparingTo("1000.00");
        assertThat(laptop.discount()).isEqualByComparingTo("100.00");
        assertThat(laptop.finalSubtotal()).isEqualByComparingTo("900.00");

        assertThat(mouse.originalSubtotal()).isEqualByComparingTo("25.00");
        assertThat(mouse.discount()).isEqualByComparingTo("25.00");
        assertThat(mouse.finalSubtotal()).isEqualByComparingTo("0.00");

        // total from ctx.total()
        assertThat(res.total()).isEqualByComparingTo("900.00");

        // applied promotions from defs
        assertThat(res.appliedPromotions()).containsExactly("PromoA", "PromoB");

        // audit trail from pipeline
        assertThat(res.auditTrail()).containsExactly("Applied 10% off and free mouse");
    }

    @Test
    @DisplayName("quote(): product in request not found in repository → IllegalArgumentException with product id")
    void quote_missingProduct_throws() {
        var p1 = product(1L, "P1", "10.00");
        when(productRepo.findAllById(anyIterable())).thenReturn(List.of(p1)); // id 2 missing

        var ex = catchThrowable(() ->
            service.quote(req(List.of(li(1L, 1), li(2L, 1))))
        );

        assertThat(ex)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Product not found: 2");

        // pipeline should never be called due to early failure
        verifyNoInteractions(pipeline);
    }

    @Test
    @DisplayName("quote(): duplicate items for same product → creates separate lines and sums correctly")
    void quote_duplicateItems_createSeparateLines_andTotals() {
        var p1 = product(1L, "Soda", "1.25");
        when(productRepo.findAllById(anyIterable())).thenReturn(List.of(p1));

        when(promoRepo.findAll()).thenReturn(List.of());
        when(pipeline.run(any(CartContext.class), anyList()))
                .thenAnswer(inv -> new PromotionPipeline.PipelineResult(((CartContext) inv.getArgument(0)).auditEntries()));

        // same product twice with different qty
        var res = service.quote(req(List.of(li(1L, 2), li(1L, 3))));

        assertThat(res.items()).hasSize(2);
        BigDecimal total = res.items().stream()
                .map(QuoteItemResponse::finalSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(total).isEqualByComparingTo("6.25"); // (2+3)*1.25

        assertThat(res.total()).isEqualByComparingTo("6.25");
    }

    @Test
    @DisplayName("quote(): rounding on final subtotal uses HALF_UP to 2dp")
    void quote_rounding_halfUp_twoDecimals() {
        // Price 0.333 * qty 3 = 0.999 → final should be 1.00 after rounding
        var p = product(1L, "Tiny", "0.333");
        when(productRepo.findAllById(anyIterable())).thenReturn(List.of(p));
        when(promoRepo.findAll()).thenReturn(List.of());
        when(pipeline.run(any(CartContext.class), anyList()))
                .thenAnswer(inv -> new PromotionPipeline.PipelineResult(((CartContext)inv.getArgument(0)).auditEntries()));

        var res = service.quote(req(List.of(li(1L, 3))));
        assertThat(res.items()).hasSize(1);
        assertThat(res.items().get(0).finalSubtotal()).isEqualByComparingTo("1.00");
        assertThat(res.total()).isEqualByComparingTo("1.00");
    }
}





