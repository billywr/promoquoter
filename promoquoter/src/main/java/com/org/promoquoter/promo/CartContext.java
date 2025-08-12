package com.org.promoquoter.promo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class CartContext {
  private final List<CartLine> lines;
  private final List<String> audit = new ArrayList<>();

  public CartContext(List<CartLine> lines){this.lines = lines;}

  public List<CartLine> lines(){return lines;}
  public CartLine lineByProduct(Long id){return lines.stream().filter(l -> l.getProductId().equals(id)).findFirst().orElse(null);}  
  public List<CartLine> linesInCategory(String category){
    // in a real model, line would carry category; for brevity we assume name encodes or map elsewhere
    return lines; // replace with real filtering by category
  }
  public void audit(String msg){audit.add(msg);}  
  public List<String> auditEntries(){return audit;}  

  public java.math.BigDecimal total(){
    return lines.stream().map(CartLine::getFinalSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}
