package com.org.promoquoter.promo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// public class CartContext {
//   private final List<CartLine> cartLines;
//   private final List<String> audit = new ArrayList<>();

//   public CartContext(List<CartLine> cartLines){this.cartLines = cartLines;}

//   public List<CartLine> cartLines(){return cartLines;}
//   public CartLine lineByProduct(Long id){return cartLines.stream().filter(l -> l.getProductId().equals(id)).findFirst().orElse(null);}  
//   public List<CartLine> cartLinesInCategory(String category){
//     // in a real model, line would carry category; for brevity we assume name encodes or map elsewhere
//     return cartLines; // replace with real filtering by category
//   }
//   public void audit(String msg){audit.add(msg);}  
//   public List<String> auditEntries(){return audit;}  

//   public BigDecimal total(){
//     return cartLines.stream().map(CartLine::getFinalSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
//   }
// }

public class CartContext {
  private final List<CartLine> cartLines;
  private final List<String> audit = new ArrayList<>();

  public CartContext(List<CartLine> cartLines) {
    this.cartLines = Objects.requireNonNull(cartLines, "cartLines");
  }

  // Current API
  public List<CartLine> cartLines() { return cartLines; }
  public CartLine lineByProduct(Long id) {
    return cartLines.stream().filter(l -> l.getProductId().equals(id)).findFirst().orElse(null);
  }
  public List<CartLine> cartLinesInCategory(String category) {
    // TODO: when CartLine carries category, actually filter here.
    return cartLines;
  }
  public void audit(String msg) { audit.add(msg); }
  public List<String> auditEntries() { return audit; }
  public BigDecimal total() {
    return cartLines.stream().map(CartLine::getFinalSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  // Compatibility aliases (needed by PercentOffCategoryRule)
  public List<CartLine> lines() { return cartLines(); }
  public List<CartLine> linesInCategory(String category) { return cartLinesInCategory(category); }
}
