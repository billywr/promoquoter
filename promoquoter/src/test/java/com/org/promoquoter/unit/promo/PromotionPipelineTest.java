package com.org.promoquoter.unit.promo;

import com.org.promoquoter.entities.PromotionType;
import com.org.promoquoter.promo.CartContext;
import com.org.promoquoter.promo.PromotionDef;
import com.org.promoquoter.promo.PromotionPipeline;
import com.org.promoquoter.promo.PromotionResult;
import com.org.promoquoter.promo.PromotionRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PromotionPipeline (pure unit; no Spring).
 */
@ExtendWith(MockitoExtension.class)
class PromotionPipelineTest {

    @Mock PromotionRule rule1;
    @Mock PromotionRule rule2;

    PromotionPipeline pipeline;

    @BeforeEach
    void setUp() {
        // order matters; pipeline picks the FIRST rule that supports a def
        pipeline = new PromotionPipeline(List.of(rule1, rule2));
    }

    // ---------- Helpers ----------

    private CartContext emptyCart() {
        return new CartContext(List.of());
    }

    private PromotionDef def(String name, int priority, boolean enabled) {
        return new PromotionDef(
                1L,
                PromotionType.PERCENT_OFF_CATEGORY,
                name,
                priority,
                enabled,
                "ANY",
                BigDecimal.ONE,
                null, null, null
        );
    }

    // ---------- Tests ----------

    @Test
    @DisplayName("Filters disabled defs and applies enabled ones in ascending priority order")
    void filtersDisabled_andOrdersByPriority() {
        var ctx = emptyCart();

        // Given three defs: B(enabled, prio 5), A(enabled, prio 1), C(disabled, prio 0)
        var defB = def("B", 5, true);
        var defA = def("A", 1, true);
        var defC = def("C", 0, false);

        // Only rule1 needs stubbing; pipeline won't query rule2.supports when rule1 already supports
        when(rule1.supports(any(PromotionDef.class))).thenReturn(true);

        // Add visible audit to verify order A then B
        doAnswer(inv -> {
            CartContext c = inv.getArgument(0);
            PromotionDef d = inv.getArgument(1);
            c.audit("applied " + d.name());
            return PromotionResult.of("rule1", BigDecimal.ZERO);
        }).when(rule1).apply(any(CartContext.class), any(PromotionDef.class));

        var result = pipeline.run(ctx, List.of(defB, defA, defC));

        InOrder inOrder = inOrder(rule1);
        inOrder.verify(rule1).apply(same(ctx), eq(defA));
        inOrder.verify(rule1).apply(same(ctx), eq(defB));
        verify(rule1, times(2)).apply(any(), any());
        verifyNoInteractions(rule2); // no unnecessary calls

        assertThat(result.audit()).containsExactly("applied A", "applied B");
        assertThat(result.audit()).isSameAs(ctx.auditEntries());
    }

    @Test
    @DisplayName("For a def supported by multiple rules, only the FIRST rule is applied")
    void firstSupportingRuleWins() {
        var ctx = emptyCart();
        var defX = def("X", 10, true);

        // Only stub rule1; pipeline won't reach rule2
        when(rule1.supports(eq(defX))).thenReturn(true);

        doAnswer(inv -> {
            CartContext c = inv.getArgument(0);
            c.audit("rule1->X");
            return PromotionResult.of("rule1", BigDecimal.ZERO);
        }).when(rule1).apply(any(CartContext.class), eq(defX));

        var res = pipeline.run(ctx, List.of(defX));

        verify(rule1).apply(same(ctx), eq(defX));
        verifyNoInteractions(rule2);

        assertThat(res.audit()).containsExactly("rule1->X");
    }

    @Test
    @DisplayName("If the first rule does NOT support a def but a later rule does, the later rule is applied")
    void laterRuleAppliedWhenFirstDoesNotSupport() {
        var ctx = emptyCart();
        var defY = def("Y", 2, true);

        when(rule1.supports(eq(defY))).thenReturn(false);
        when(rule2.supports(eq(defY))).thenReturn(true);

        doAnswer(inv -> {
            CartContext c = inv.getArgument(0);
            c.audit("rule2->Y");
            return PromotionResult.of("rule2", BigDecimal.ZERO);
        }).when(rule2).apply(any(CartContext.class), eq(defY));

        var res = pipeline.run(ctx, List.of(defY));

        verify(rule1).supports(eq(defY));
        verify(rule2).apply(same(ctx), eq(defY));

        assertThat(res.audit()).containsExactly("rule2->Y");
    }

    @Test
    @DisplayName("No rules support a def → no apply call; audit remains unchanged")
    void noSupportingRules_noApply() {
        var ctx = emptyCart();
        ctx.audit("pre-existing");

        var defZ = def("Z", 3, true);

        when(rule1.supports(eq(defZ))).thenReturn(false);
        when(rule2.supports(eq(defZ))).thenReturn(false);

        var res = pipeline.run(ctx, List.of(defZ));

        verify(rule1).supports(eq(defZ));
        verify(rule2).supports(eq(defZ));
        verify(rule1, never()).apply(any(), any());
        verify(rule2, never()).apply(any(), any());

        assertThat(res.audit()).containsExactly("pre-existing");
        assertThat(res.audit()).isSameAs(ctx.auditEntries());
    }

    @Test
    @DisplayName("Empty defs list → no rule interactions; returns current audit")
    void emptyDefs_noops() {
        var ctx = emptyCart();
        ctx.audit("hello");

        var res = pipeline.run(ctx, List.of());

        verifyNoInteractions(rule1, rule2);
        assertThat(res.audit()).containsExactly("hello");
    }
}
