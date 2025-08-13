package com.org.promoquoter.promo;

import java.math.BigDecimal;
import java.math.RoundingMode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class CartLine {
    private final Long productId;
    private final String name;
    private final int qty;
    private final BigDecimal unitPrice;

    private BigDecimal discount = BigDecimal.ZERO;

    public BigDecimal getOriginalSubtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(qty));
    }

    public void addDiscount(BigDecimal d) {
        this.discount = this.discount.add(d);
    }

    public BigDecimal getFinalSubtotal() {
        return getOriginalSubtotal()
                .subtract(discount)
                .setScale(2, RoundingMode.HALF_UP);
    }
}