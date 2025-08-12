package com.org.promoquoter.unit.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.org.promoquoter.entities.Promotion;
import com.org.promoquoter.repositories.PromotionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.org.promoquoter.controllers.PromotionController;

@WebMvcTest(PromotionController.class)
class PromotionControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @MockBean PromotionRepository repo;

    @Test
    @DisplayName("POST /promotions: saves a single promotion and returns 200 with array payload")
    void create_single_ok() throws Exception {
        given(repo.save(any(Promotion.class))).willAnswer(inv -> inv.getArgument(0));

        var json = """
        {
          "promotions": [
            {
              "type":"PERCENT_OFF_CATEGORY",
              "name":"July Sale",
              "priority":10,
              "enabled":true,
              "category":"ELECTRONICS",
              "percent":15,
              "productId":0,
              "buyQty":0,
              "freeQty":0
            }
          ]
        }
        """;

        mvc.perform(post("/promotions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
           .andExpect(status().isOk())
           .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
           .andExpect(jsonPath("$.length()").value(1))
           .andExpect(jsonPath("$[0].name").value("July Sale"))
           .andExpect(jsonPath("$[0].type").value("PERCENT_OFF_CATEGORY"));

        verify(repo, times(1)).save(any(Promotion.class));
    }

    @Test
    @DisplayName("POST /promotions: saves multiple promotions (loop branch) and returns all")
    void create_multiple_ok() throws Exception {
        given(repo.save(any(Promotion.class))).willAnswer(inv -> inv.getArgument(0));

        var json = """
        {
          "promotions": [
            {"type":"BUY_X_GET_Y","name":"Buy1Get1","priority":1,"enabled":true,"category":"FOOD","percent":0,"productId":123,"buyQty":1,"freeQty":1},
            {"type":"PERCENT_OFF_CATEGORY","name":"BackToSchool","priority":5,"enabled":false,"category":"BOOKS","percent":20,"productId":0,"buyQty":0,"freeQty":0}
          ]
        }
        """;

        mvc.perform(post("/promotions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.length()").value(2))
           .andExpect(jsonPath("$[0].name").value("Buy1Get1"))
           .andExpect(jsonPath("$[1].percent").value(20));

        verify(repo, times(2)).save(any(Promotion.class));
    }

    @Test
    @DisplayName("POST /promotions: empty list returns 200 with [] and does not call repo.save()")
    void create_emptyList_ok_noSaves() throws Exception {
        var json = """
        { "promotions": [] }
        """;

        mvc.perform(post("/promotions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.length()").value(0));

        verify(repo, never()).save(any(Promotion.class));
    }

    @Test
    @DisplayName("POST /promotions: maps all fields correctly into entity sent to repository")
    void create_mapsFields() throws Exception {
        given(repo.save(any(Promotion.class))).willAnswer(inv -> inv.getArgument(0));

        var json = """
        {
          "promotions": [
            {"type":"PERCENT_OFF_CATEGORY","name":"Mapped","priority":7,"enabled":true,"category":"TOYS","percent":12,"productId":456,"buyQty":2,"freeQty":0}
          ]
        }
        """;

        mvc.perform(post("/promotions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
           .andExpect(status().isOk());

        ArgumentCaptor<Promotion> cap = ArgumentCaptor.forClass(Promotion.class);
        verify(repo).save(cap.capture());
        Promotion p = cap.getValue();

        assertThat(p.getType()).isEqualTo(com.org.promoquoter.entities.PromotionType.PERCENT_OFF_CATEGORY);
        assertThat(p.getName()).isEqualTo("Mapped");
        assertThat(p.getPriority()).isEqualTo(7);
        assertThat(p.isEnabled()).isTrue();
        assertThat(p.getCategory()).isEqualTo("TOYS");
        // percent is BigDecimal in the entity
        assertThat(p.getPercent()).isEqualByComparingTo(new BigDecimal("12"));
        assertThat(p.getProductId()).isEqualTo(456L);
        assertThat(p.getBuyQty()).isEqualTo(2);
        assertThat(p.getFreeQty()).isEqualTo(0);
    }

    @Test
    @DisplayName("POST /promotions: when repository throws, returns 409 (mapped) and stops further saves")
    void create_repoThrows_500() throws Exception {
        // Your ControllerAdvice maps RuntimeException -> 409 Conflict
        given(repo.save(any(Promotion.class))).willAnswer(inv -> { throw new RuntimeException("DB down"); });

        var json = """
        {
          "promotions": [
            {"type":"BUY_X_GET_Y","name":"First","priority":1,"enabled":true,"category":"A","percent":0,"productId":1,"buyQty":1,"freeQty":1},
            {"type":"PERCENT_OFF_CATEGORY","name":"Second","priority":2,"enabled":true,"category":"B","percent":10,"productId":2,"buyQty":0,"freeQty":0}
          ]
        }
        """;

        mvc.perform(post("/promotions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
           .andExpect(status().isConflict());

        verify(repo, times(1)).save(any(Promotion.class));
    }

    @Test
    @DisplayName("POST /promotions: missing/invalid body results in 409 (mapped by advice)")
    void create_missingBody_400() throws Exception {
        mvc.perform(post("/promotions")
                .contentType(MediaType.APPLICATION_JSON))
           .andExpect(status().isConflict());
        verifyNoInteractions(repo);
    }
}
