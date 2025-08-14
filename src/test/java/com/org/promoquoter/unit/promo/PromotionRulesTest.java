package com.org.promoquoter.unit.promo;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.org.promoquoter.entities.PromotionType;
import com.org.promoquoter.promo.BuyXGetYRule;
import com.org.promoquoter.promo.CartContext;
import com.org.promoquoter.promo.CartLine;
import com.org.promoquoter.promo.PercentOffCategoryRule;
import com.org.promoquoter.promo.PromotionDef;

class PromotionRulesTest {

    // Helper to build a PromotionDef quickly
    private PromotionDef defPercent(String name, String category, String percent) {
        return new PromotionDef(
                1L,
                PromotionType.PERCENT_OFF_CATEGORY,
                name,
                1,
                true,
                category,
                new BigDecimal(percent),
                null,
                null,
                null
        );
    }

    private PromotionDef defBogo(String name, long productId, int buyQty, int freeQty) {
        return new PromotionDef(
                2L,
                PromotionType.BUY_X_GET_Y,
                name,
                1,
                true,
                null,
                BigDecimal.ZERO,
                productId,
                buyQty,
                freeQty
        );
    }

    // Build a CartContext with simple CartLines
    private CartContext cart(CartLine... lines) {
        return new CartContext(List.of(lines));
    }

    // CHANGED: include category in test CartLine helper
    private CartLine line(long pid, String name, String category, int qty, String unitPrice) {
        return new CartLine(pid, name, category, qty, new BigDecimal(unitPrice));
    }

    @Nested
    @DisplayName("PercentOffCategoryRule")
    class PercentOffCategoryRuleTests {
        final PercentOffCategoryRule rule = new PercentOffCategoryRule();

        @Test
        @DisplayName("supports() → true only for PERCENT_OFF_CATEGORY")
        void supports_onlyPercentCategory() {
            var yes = defPercent("Cat 10%", "ELECTRONICS", "10");
            var no  = new PromotionDef(3L, PromotionType.BUY_X_GET_Y, "BOGO", 1, true,
                    null, BigDecimal.ZERO, 1L, 2, 1);

            assertThat(rule.supports(yes)).isTrue();
            assertThat(rule.supports(no)).isFalse();
        }

        @Test
        @DisplayName("apply() → discounts each matching-category line, HALF_UP to 2dp, one audit per line")
        void apply_discountsAndAudits_eachLine() {
            // Cart: two ELECTRONICS lines so both get 10%
            var ctx = cart(
                    line(1L, "Laptop", "ELECTRONICS", 1, "100.00"),
                    line(2L, "Mouse",  "ELECTRONICS", 2, "50.00")
            );

            var def = defPercent("Summer Sale", "ELECTRONICS", "10");

            var result = rule.apply(ctx, def);

            var l1 = ctx.lineByProduct(1L);
            var l2 = ctx.lineByProduct(2L);

            assertThat(l1.getDiscount()).isEqualByComparingTo("10.00");
            assertThat(l1.getFinalSubtotal()).isEqualByComparingTo("90.00");

            assertThat(l2.getDiscount()).isEqualByComparingTo("10.00");
            assertThat(l2.getFinalSubtotal()).isEqualByComparingTo("90.00");

            assertThat(ctx.auditEntries()).hasSize(2);
            assertThat(ctx.auditEntries().get(0)).isEqualTo("PERCENT_OFF_CATEGORY(10%) on Laptop: -10.00");
            assertThat(ctx.auditEntries().get(1)).isEqualTo("PERCENT_OFF_CATEGORY(10%) on Mouse: -10.00");

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("apply() → percent=0 yields no discount and no audit (matching category)")
        void apply_zeroPercent_noop() {
            var ctx = cart(line(1L, "Item", "ANY", 3, "12.34"));
            var def = defPercent("Zero", "ANY", "0");

            var result = rule.apply(ctx, def);

            assertThat(ctx.lineByProduct(1L).getDiscount()).isEqualByComparingTo("0.00");
            assertThat(ctx.auditEntries()).isEmpty();
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("apply() → rounding HALF_UP: 15% of 0.999 = 0.15; final 0.85")
        void apply_rounding_halfUp() {
            // One line in MISC so it matches def category
            var ctx = cart(line(1L, "Tiny", "MISC", 3, "0.333"));
            var def = defPercent("FifteenOff", "MISC", "15");

            rule.apply(ctx, def);

            var l = ctx.lineByProduct(1L);
            assertThat(l.getDiscount()).isEqualByComparingTo("0.15");
            assertThat(l.getFinalSubtotal()).isEqualByComparingTo("0.85");
        }

        @Test
        @DisplayName("apply() → non-matching category yields no discount")
        void apply_nonMatchingCategory_noop() {
            var ctx = cart(line(1L, "Milk", "DAIRY", 2, "2.50"));
            var def = defPercent("Electronics Sale", "ELECTRONICS", "10");

            rule.apply(ctx, def);

            var milk = ctx.lineByProduct(1L);
            assertThat(milk.getDiscount()).isEqualByComparingTo("0.00");
            assertThat(ctx.auditEntries()).isEmpty();
        }
    }

    @Nested
    @DisplayName("BuyXGetYRule")
    class BuyXGetYRuleTests {
        final BuyXGetYRule rule = new BuyXGetYRule();

        @Test
        @DisplayName("supports() → true only for BUY_X_GET_Y")
        void supports_onlyBogo() {
            var yes = defBogo("BOGO", 1L, 2, 1);
            var no  = defPercent("Cat 10%", "ANY", "10");

            assertThat(rule.supports(yes)).isTrue();
            assertThat(rule.supports(no)).isFalse();
        }

        @Test
        @DisplayName("apply() → qty=5, buy2 get1 ⇒ free=1, discount=1*unit, audit written")
        void apply_bogo_basic() {
            var ctx = cart(line(10L, "Soda", "DRINKS", 5, "1.25"));
            var def = defBogo("Buy2Get1", 10L, 2, 1);

            var result = rule.apply(ctx, def);

            var soda = ctx.lineByProduct(10L);
            assertThat(soda.getDiscount()).isEqualByComparingTo("1.25");
            assertThat(soda.getFinalSubtotal()).isEqualByComparingTo("5.00");
            assertThat(ctx.auditEntries()).containsExactly("BUY_2_GET_1 on Soda: free=1, -1.25");
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("apply() → not enough qty for a full block ⇒ no free items, no audit")
        void apply_bogo_noBlock_noop() {
            var ctx = cart(line(11L, "Book", "BOOKS", 2, "10.00"));
            var def = defBogo("Buy2Get1", 11L, 2, 1);

            rule.apply(ctx, def);

            var book = ctx.lineByProduct(11L);
            assertThat(book.getDiscount()).isEqualByComparingTo("0.00");
            assertThat(ctx.auditEntries()).isEmpty();
        }

        @Test
        @DisplayName("apply() → product not in cart ⇒ no change, no audit")
        void apply_bogo_missingLine_noop() {
            var ctx = cart(line(12L, "Pen", "STATIONERY", 4, "0.50"));
            var def = defBogo("Buy2Get1", 99L, 2, 1);

            rule.apply(ctx, def);

            var pen = ctx.lineByProduct(12L);
            assertThat(pen.getDiscount()).isEqualByComparingTo("0.00");
            assertThat(ctx.auditEntries()).isEmpty();
        }

        @Test
        @DisplayName("apply() → rounding on discount uses HALF_UP to 2dp")
        void apply_bogo_rounding() {
            var ctx = cart(line(13L, "Tiny", "MISC", 6, "0.333"));
            var def = defBogo("Buy2Get1", 13L, 2, 1);

            rule.apply(ctx, def);

            var tiny = ctx.lineByProduct(13L);
            assertThat(tiny.getDiscount()).isEqualByComparingTo("0.67");
            assertThat(tiny.getFinalSubtotal()).isEqualByComparingTo("1.33");
        }
    }
}
