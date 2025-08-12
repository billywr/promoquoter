package com.org.promoquoter.promo;

import java.math.BigDecimal;

public record AppliedPromotion(String name, BigDecimal discount, String notes) {}
