package com.org.promoquoter.integration.promo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.org.promoquoter.entities.Promotion;
import com.org.promoquoter.entities.PromotionType;
import com.org.promoquoter.repositories.PromotionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class PromotionControllerIT {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired PromotionRepository repo;

    @BeforeEach
    void clean() {
        repo.deleteAll();
    }

    // -------- tests --------

    @Test
    @DisplayName("POST /promotions — saves multiple promotions and returns 200 with array payload")
    void create_multiple_ok() throws Exception {
        String body = """
        {
          "promotions": [
            {
              "type":"BUY_X_GET_Y",
              "name":"B1G1 Soda",
              "priority":1,
              "enabled":true,
              "productId":111,
              "buyQty":1,
              "freeQty":1
            },
            {
              "type":"PERCENT_OFF_CATEGORY",
              "name":"Books 20%",
              "priority":5,
              "enabled":false,
              "category":"BOOKS",
              "percent":20
            }
          ]
        }
        """;

        mvc.perform(post("/promotions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
           .andExpect(status().isOk())
           .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
           .andExpect(jsonPath("$.length()").value(2))
           .andExpect(jsonPath("$[0].name").value("B1G1 Soda"))
           .andExpect(jsonPath("$[1].type").value("PERCENT_OFF_CATEGORY"));

        // persisted?
        List<Promotion> all = repo.findAll();
        assertThat(all).hasSize(2);

        Promotion bogo = all.stream().filter(p -> p.getName().equals("B1G1 Soda")).findFirst().orElseThrow();
        assertThat(bogo.getType()).isEqualTo(PromotionType.BUY_X_GET_Y);
        assertThat(bogo.isEnabled()).isTrue();
        assertThat(bogo.getProductId()).isEqualTo(111L);
        assertThat(bogo.getBuyQty()).isEqualTo(1);
        assertThat(bogo.getFreeQty()).isEqualTo(1);

        Promotion pct = all.stream().filter(p -> p.getName().equals("Books 20%")).findFirst().orElseThrow();
        assertThat(pct.getType()).isEqualTo(PromotionType.PERCENT_OFF_CATEGORY);
        assertThat(pct.isEnabled()).isFalse();
        assertThat(pct.getCategory()).isEqualTo("BOOKS");
        assertThat(pct.getPercent()).isEqualByComparingTo(new BigDecimal("20"));
        // and not populated fields remain null
        assertThat(pct.getProductId()).isNull();
        assertThat(pct.getBuyQty()).isNull();
        assertThat(pct.getFreeQty()).isNull();
    }

    @Test
    @DisplayName("POST /promotions — empty list returns 200 with [] and does not persist")
    void create_emptyList_ok() throws Exception {
        String body = """
        { "promotions": [] }
        """;

        mvc.perform(post("/promotions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
           .andExpect(status().isOk())
           .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
           .andExpect(jsonPath("$.length()").value(0));

        assertThat(repo.count()).isZero();
    }

    @Test
    @DisplayName("POST /promotions — maps all fields correctly for PERCENT_OFF_CATEGORY")
    void create_mapsFields_percentOff() throws Exception {
        String body = """
        {
          "promotions": [
            {
              "type":"PERCENT_OFF_CATEGORY",
              "name":"Mapped",
              "priority":7,
              "enabled":true,
              "category":"TOYS",
              "percent":12
            }
          ]
        }
        """;

        mvc.perform(post("/promotions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
           .andExpect(status().isOk());

        Promotion saved = repo.findAll().get(0);
        assertThat(saved.getType()).isEqualTo(PromotionType.PERCENT_OFF_CATEGORY);
        assertThat(saved.getName()).isEqualTo("Mapped");
        assertThat(saved.getPriority()).isEqualTo(7);
        assertThat(saved.isEnabled()).isTrue();
        assertThat(saved.getCategory()).isEqualTo("TOYS");
        assertThat(saved.getPercent()).isEqualByComparingTo("12");
        // unused fields should be null
        assertThat(saved.getProductId()).isNull();
        assertThat(saved.getBuyQty()).isNull();
        assertThat(saved.getFreeQty()).isNull();
    }

    @Test
    @DisplayName("POST /promotions — invalid enum value yields 4xx and nothing is saved")
    void create_invalidEnum_4xx() throws Exception {
        // 'BOGO' is not a valid enum literal for PromotionType in your model
        String body = """
        {
          "promotions": [
            { "type":"BOGO", "name":"BadType", "priority":1, "enabled":true }
          ]
        }
        """;

        mvc.perform(post("/promotions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
           .andExpect(status().is4xxClientError())
           .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        assertThat(repo.count()).isZero();
    }

    @Test
    @DisplayName("POST /promotions — missing body yields 409 (mapped) and nothing is saved")
    void create_missingBody_409() throws Exception {
        mvc.perform(post("/promotions")
                .contentType(MediaType.APPLICATION_JSON))
           .andExpect(status().isConflict()) // 409 from your exception mapping
           .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        assertThat(repo.count()).isZero();
    }
}
