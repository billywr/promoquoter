package com.org.promoquoter.entities;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Promotion {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PromotionType type;

  private String name;
  // lower = earlier
  private Integer priority;
  private boolean enabled;

  // PERCENT_OFF_CATEGORY
  private String category;
  private BigDecimal percent;

  // BUY_X_GET_Y
  private Long productId;
  private Integer buyQty;
  private Integer freeQty;
}