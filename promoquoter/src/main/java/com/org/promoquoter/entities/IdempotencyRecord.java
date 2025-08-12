package com.org.promoquoter.entities;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Table(name = "idempotency_record")
public class IdempotencyRecord {
  @Id
  @Column(name = "idempotency_key", length = 255)
  private String idempotencyKey;

  @Column(length = 2000)
  private String requestHash;

  private String orderId;

  @Column(columnDefinition = "TIMESTAMP WITH TIME ZONE")
  private OffsetDateTime createdAt;
}
