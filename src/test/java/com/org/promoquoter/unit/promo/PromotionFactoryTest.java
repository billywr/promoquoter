package com.org.promoquoter.unit.promo;

import java.math.BigDecimal;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.org.promoquoter.entities.Promotion;
import com.org.promoquoter.entities.PromotionType;
import com.org.promoquoter.promo.PromotionDef;
import com.org.promoquoter.promo.PromotionFactory;

class PromotionFactoryTest {

    private final PromotionFactory factory = new PromotionFactory();

    @Test
    @DisplayName("fromEntity(): maps all fields for PERCENT_OFF_CATEGORY")
    void maps_percentOffCategory() {
        Promotion entity = Promotion.builder()
                .id(1L)
                .type(PromotionType.PERCENT_OFF_CATEGORY)
                .name("Cat 15%")
                .priority(5)
                .enabled(true)
                .category("BOOKS")
                .percent(new BigDecimal("15"))
                .productId(null)
                .buyQty(null)
                .freeQty(null)
                .build();

        PromotionDef def = factory.fromEntity(entity);

        Assertions.assertThat(def.id()).isEqualTo(1L);
        Assertions.assertThat(def.type()).isEqualTo(PromotionType.PERCENT_OFF_CATEGORY);
        Assertions.assertThat(def.name()).isEqualTo("Cat 15%");
        Assertions.assertThat(def.priority()).isEqualTo(5);
        Assertions.assertThat(def.enabled()).isTrue();
        Assertions.assertThat(def.category()).isEqualTo("BOOKS");
        Assertions.assertThat(def.percent()).isEqualByComparingTo("15");
        Assertions.assertThat(def.productId()).isNull();
        Assertions.assertThat(def.buyQty()).isNull();
        Assertions.assertThat(def.freeQty()).isNull();
    }

    @Test
    @DisplayName("fromEntity(): maps all fields for BUY_X_GET_Y")
    void maps_buyXGetY() {
        Promotion entity = Promotion.builder()
                .id(2L)
                .type(PromotionType.BUY_X_GET_Y)
                .name("Buy2Get1")
                .priority(3)
                .enabled(true)
                .category(null)
                .percent(null)
                .productId(42L)
                .buyQty(2)
                .freeQty(1)
                .build();

        PromotionDef def = factory.fromEntity(entity);

        Assertions.assertThat(def.id()).isEqualTo(2L);
        Assertions.assertThat(def.type()).isEqualTo(PromotionType.BUY_X_GET_Y);
        Assertions.assertThat(def.name()).isEqualTo("Buy2Get1");
        Assertions.assertThat(def.priority()).isEqualTo(3);
        Assertions.assertThat(def.enabled()).isTrue();
        Assertions.assertThat(def.category()).isNull();
        Assertions.assertThat(def.percent()).isNull();
        Assertions.assertThat(def.productId()).isEqualTo(42L);
        Assertions.assertThat(def.buyQty()).isEqualTo(2);
        Assertions.assertThat(def.freeQty()).isEqualTo(1);
    }

    @Test
    @DisplayName("fromEntity(): defaults priority to 100 when entity priority is null")
    void defaults_priority_whenNull() {
        Promotion entity = Promotion.builder()
                .id(3L)
                .type(PromotionType.PERCENT_OFF_CATEGORY)
                .name("NoPriority")
                .priority(null) // <- important
                .enabled(true)
                .category("ANY")
                .percent(new BigDecimal("10"))
                .build();

        PromotionDef def = factory.fromEntity(entity);

        Assertions.assertThat(def.priority()).isEqualTo(100);
    }

    @Test
    @DisplayName("fromEntity(): maps enabled=false correctly")
    void maps_enabled_false() {
        Promotion entity = Promotion.builder()
                .id(4L)
                .type(PromotionType.PERCENT_OFF_CATEGORY)
                .name("Disabled")
                .priority(1)
                .enabled(false) // <- important
                .category("ELECTRONICS")
                .percent(new BigDecimal("5"))
                .build();

        PromotionDef def = factory.fromEntity(entity);

        Assertions.assertThat(def.enabled()).isFalse();
    }

    @Test
    @DisplayName("fromEntity(): passes through nullable fields (name, category, percent, productId, buyQty, freeQty)")
    void passesThrough_nullables() {
        Promotion entity = Promotion.builder()
                .id(5L)
                .type(PromotionType.BUY_X_GET_Y)
                .name(null)
                .priority(7)
                .enabled(true)
                .category(null)
                .percent(null)
                .productId(null)
                .buyQty(null)
                .freeQty(null)
                .build();

        PromotionDef def = factory.fromEntity(entity);

        Assertions.assertThat(def.name()).isNull();
        Assertions.assertThat(def.category()).isNull();
        Assertions.assertThat(def.percent()).isNull();
        Assertions.assertThat(def.productId()).isNull();
        Assertions.assertThat(def.buyQty()).isNull();
        Assertions.assertThat(def.freeQty()).isNull();
    }

    @Test
    @DisplayName("fromEntity(): id may be null (unsaved entity) and is passed through")
    void id_mayBeNull_andPassesThrough() {
        Promotion entity = Promotion.builder()
                .id(null) // unsaved
                .type(PromotionType.PERCENT_OFF_CATEGORY)
                .name("Unsaved")
                .priority(2)
                .enabled(true)
                .category("GROCERY")
                .percent(new BigDecimal("12.5"))
                .build();

        PromotionDef def = factory.fromEntity(entity);

        Assertions.assertThat(def.id()).isNull();
        Assertions.assertThat(def.percent()).isEqualByComparingTo("12.5");
    }
}
