package com.org.promoquoter.promo;

import java.math.BigDecimal;

public class CartLine {
  private final Long productId;
  private final String name;
  private final int qty;
  private final BigDecimal unitPrice;
  private BigDecimal discount = BigDecimal.ZERO;

  public CartLine(Long productId, String name, int qty, BigDecimal unitPrice) {
    this.productId = productId; this.name = name; this.qty = qty; this.unitPrice = unitPrice;
  }
  public Long getProductId(){return productId;}
  public String getName(){return name;}
  public int getQty(){return qty;}
  public BigDecimal getUnitPrice(){return unitPrice;}

  public BigDecimal getOriginalSubtotal(){return unitPrice.multiply(BigDecimal.valueOf(qty));}
  public BigDecimal getDiscount(){return discount;}
  public void addDiscount(java.math.BigDecimal d){this.discount = this.discount.add(d);}  
  public BigDecimal getFinalSubtotal(){return getOriginalSubtotal().subtract(discount).setScale(2, java.math.RoundingMode.HALF_UP);}  
}