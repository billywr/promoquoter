package com.org.promoquoter.entities;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity 
@Getter 
@Setter 
@NoArgsConstructor 
@AllArgsConstructor 
@Builder
public class Promotion {
  @Id 
  @GeneratedValue(strategy = GenerationType.IDENTITY)
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