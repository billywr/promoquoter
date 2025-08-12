package com.org.promoquoter.unit.promo;

import com.org.promoquoter.entities.PromotionType;
import com.org.promoquoter.promo.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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

    private CartLine line(long pid, String name, int qty, String unitPrice) {
        return new CartLine(pid, name, qty, new BigDecimal(unitPrice));
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
        @DisplayName("apply() → discounts each line, rounds HALF_UP to 2dp, writes one audit entry per line")
        void apply_discountsAndAudits_eachLine() {
            // Cart: P1=100.00 x1, P2=50.00 x2 → subtotals 100.00 and 100.00
            var ctx = cart(
                    line(1L, "Laptop", 1, "100.00"),
                    line(2L, "Mouse",  2, "50.00")
            );

            var def = defPercent("Summer Sale", "ANY", "10"); // 10%

            var result = rule.apply(ctx, def);

            // Each line: -10% of its original subtotal
            var l1 = ctx.lineByProduct(1L);
            var l2 = ctx.lineByProduct(2L);

            assertThat(l1.getDiscount()).isEqualByComparingTo("10.00");
            assertThat(l1.getFinalSubtotal()).isEqualByComparingTo("90.00");

            assertThat(l2.getDiscount()).isEqualByComparingTo("10.00");
            assertThat(l2.getFinalSubtotal()).isEqualByComparingTo("90.00");

            // Two audit entries, one per line
            assertThat(ctx.auditEntries()).hasSize(2);
            assertThat(ctx.auditEntries().get(0))
                    .isEqualTo("PERCENT_OFF_CATEGORY(10%) on Laptop: -10.00");
            assertThat(ctx.auditEntries().get(1))
                    .isEqualTo("PERCENT_OFF_CATEGORY(10%) on Mouse: -10.00");

            // Result object present (amount not asserted since type not provided here)
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("apply() → percent=0 yields no discount and no audit")
        void apply_zeroPercent_noop() {
            var ctx = cart(line(1L, "Item", 3, "12.34"));
            var def = defPercent("Zero", "ANY", "0");

            var result = rule.apply(ctx, def);

            assertThat(ctx.lineByProduct(1L).getDiscount()).isEqualByComparingTo("0.00");
            assertThat(ctx.auditEntries()).isEmpty();
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("apply() → rounding example: 15% of 0.333*3 = 1.00 total discount 0.15*0.999≈0.15 → 0.15, final 0.85")
        void apply_rounding_halfUp() {
            // One line: unit=0.333, qty=3, subtotal=0.999
            var ctx = cart(line(1L, "Tiny", 3, "0.333"));
            var def = defPercent("FifteenOff", "ANY", "15"); // 15%

            rule.apply(ctx, def);

            var l = ctx.lineByProduct(1L);
            // discount = 0.999 * 0.15 = 0.14985 → 0.15
            assertThat(l.getDiscount()).isEqualByComparingTo("0.15");
            // final = 0.999 - 0.15 = 0.84985 → 0.85 (CartLine#setScale(2, HALF_UP))
            assertThat(l.getFinalSubtotal()).isEqualByComparingTo("0.85");
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
            var ctx = cart(line(10L, "Soda", 5, "1.25"));
            var def = defBogo("Buy2Get1", 10L, 2, 1);

            var result = rule.apply(ctx, def);

            var soda = ctx.lineByProduct(10L);
            // free = (5 / (2+1)) * 1 = 1
            assertThat(soda.getDiscount()).isEqualByComparingTo("1.25");
            assertThat(soda.getFinalSubtotal()).isEqualByComparingTo("5.00"); // 5*1.25 - 1.25

            assertThat(ctx.auditEntries()).hasSize(1);
            assertThat(ctx.auditEntries().get(0))
                    .isEqualTo("BUY_2_GET_1 on Soda: free=1, -1.25");

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("apply() → not enough qty for a full block ⇒ no free items, no audit")
        void apply_bogo_noBlock_noop() {
            var ctx = cart(line(11L, "Book", 2, "10.00"));
            var def = defBogo("Buy2Get1", 11L, 2, 1); // need 3 per block; have only 2

            rule.apply(ctx, def);

            var book = ctx.lineByProduct(11L);
            assertThat(book.getDiscount()).isEqualByComparingTo("0.00");
            assertThat(ctx.auditEntries()).isEmpty();
        }

        @Test
        @DisplayName("apply() → product not in cart ⇒ no change, no audit")
        void apply_bogo_missingLine_noop() {
            var ctx = cart(line(12L, "Pen", 4, "0.50"));
            var def = defBogo("Buy2Get1", 99L, 2, 1); // product id 99 not present

            rule.apply(ctx, def);

            var pen = ctx.lineByProduct(12L);
            assertThat(pen.getDiscount()).isEqualByComparingTo("0.00");
            assertThat(ctx.auditEntries()).isEmpty();
        }

        @Test
        @DisplayName("apply() → rounding on discount uses HALF_UP to 2dp")
        void apply_bogo_rounding() {
            // unit 0.333, qty 6, buy2 get1 ⇒ blocks=(6/(2+1))=2 ⇒ free=2 ⇒ discount=2*0.333=0.666 → 0.67
            var ctx = cart(line(13L, "Tiny", 6, "0.333"));
            var def = defBogo("Buy2Get1", 13L, 2, 1);

            rule.apply(ctx, def);

            var tiny = ctx.lineByProduct(13L);
            assertThat(tiny.getDiscount()).isEqualByComparingTo("0.67");
            // final = 6*0.333 - 0.666(rounded to 0.67) → 1.998 - 0.67 = 1.328 → 1.33 after CartLine rounding
            assertThat(tiny.getFinalSubtotal()).isEqualByComparingTo("1.33");
        }
    }
}
