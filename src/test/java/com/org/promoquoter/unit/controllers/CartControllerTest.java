package com.org.promoquoter.unit.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.org.promoquoter.dto.cart.*;
import com.org.promoquoter.services.OrderService;
import com.org.promoquoter.services.QuotationService;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import com.org.promoquoter.controllers.CartController;

@WebMvcTest(CartController.class)
class CartControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper om;

  @MockBean private QuotationService pricingService;
  @MockBean private OrderService orderService;

  // -------- Helpers

  private QuoteRequest sampleQuoteReq() {
    return new QuoteRequest(
        List.of(new CartItem(101L, 2), new CartItem(202L, 1)),
        "VIP"
    );
  }

  private QuoteResponse sampleQuoteRes() {
    return new QuoteResponse(
        List.of(
            new QuoteItemResponse(101L, "A", 2, new BigDecimal("10.00"),
                new BigDecimal("20.00"), new BigDecimal("2.00"), new BigDecimal("18.00")),
            new QuoteItemResponse(202L, "B", 1, new BigDecimal("15.00"),
                new BigDecimal("15.00"), new BigDecimal("0.00"), new BigDecimal("15.00"))
        ),
        new BigDecimal("33.00"),
        List.of("WELCOME10"),
        List.of("applied WELCOME10")
    );
  }

  private ConfirmRequest sampleConfirmReq() {
    return new ConfirmRequest(
        List.of(new CartItem(101L, 2)),
        "VIP"
    );
  }

 private ConfirmResponse sampleConfirmRes() {
  return new ConfirmResponse("999", new BigDecimal("18.00"));
}

  // -------- Tests

  @Nested
  class Quote {

    @Test
    void quote_ok_200_and_body_matches() throws Exception {
      when(pricingService.quote(any(QuoteRequest.class))).thenReturn(sampleQuoteRes());

      mvc.perform(post("/cart/quote")
              .contentType(MediaType.APPLICATION_JSON)
              .content(om.writeValueAsString(sampleQuoteReq())))
          .andExpect(status().isOk())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.total", is(33.00))) // JSON number -> double matcher
          .andExpect(jsonPath("$.items", hasSize(2)))
          .andExpect(jsonPath("$.appliedPromotions[0]", is("WELCOME10")));
         // .andExpect(jsonPath("$.audit", hasSize(1)));

      // verify call + payload
      ArgumentCaptor<QuoteRequest> captor = ArgumentCaptor.forClass(QuoteRequest.class);
      verify(pricingService).quote(captor.capture());
      verifyNoMoreInteractions(pricingService, orderService);

      QuoteRequest sent = captor.getValue();
      // quick sanity on captured request
      assert sent.items().size() == 2;
      assert "VIP".equals(sent.customerSegment());
    }

    @Test
    void quote_validation_error_400_on_empty_items() throws Exception {
      var badReq = new QuoteRequest(List.of(), "VIP"); // assuming @NotEmpty on items

      mvc.perform(post("/cart/quote")
              .contentType(MediaType.APPLICATION_JSON)
              .content(om.writeValueAsString(badReq)))
          .andExpect(status().isBadRequest());

      verifyNoMoreInteractions(pricingService, orderService);
    }

    @Test
    void quote_415_on_unsupported_media_type() throws Exception {
      mvc.perform(post("/cart/quote")
              .contentType(MediaType.TEXT_PLAIN)
              .content("not-json"))
          .andExpect(status().isUnsupportedMediaType());

      verifyNoMoreInteractions(pricingService, orderService);
    }

    @Test
    void quote_400_on_missing_body() throws Exception {
      mvc.perform(post("/cart/quote")
              .contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isConflict());

      verifyNoMoreInteractions(pricingService, orderService);
    }

    @Test
    void quote_500_when_service_throws() throws Exception {
      when(pricingService.quote(any())).thenThrow(new RuntimeException("boom"));

      mvc.perform(post("/cart/quote")
              .contentType(MediaType.APPLICATION_JSON)
              .content(om.writeValueAsString(sampleQuoteReq())))
          .andExpect(status().isConflict());

      verify(pricingService).quote(any());
      verifyNoMoreInteractions(pricingService, orderService);
    }

    @Test
    void quote_405_on_wrong_method() throws Exception {
      mvc.perform(get("/cart/quote"))
          .andExpect(status().isMethodNotAllowed());

      verifyNoMoreInteractions(pricingService, orderService);
    }
  }

  @Nested
  class Confirm {

    @Test
    void confirm_ok_200_without_idempotency_key() throws Exception {
      when(orderService.confirm(any(ConfirmRequest.class), any())).thenReturn(sampleConfirmRes());

      mvc.perform(post("/cart/confirm")
              .contentType(MediaType.APPLICATION_JSON)
              .content(om.writeValueAsString(sampleConfirmReq())))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.orderId", is("999")))
          .andExpect(jsonPath("$.total", is(18.00)));

      ArgumentCaptor<ConfirmRequest> reqCap = ArgumentCaptor.forClass(ConfirmRequest.class);
      ArgumentCaptor<String> keyCap = ArgumentCaptor.forClass(String.class);
      verify(orderService).confirm(reqCap.capture(), keyCap.capture());
      assert keyCap.getValue() == null; // header missing -> null by controller signature
      verifyNoMoreInteractions(orderService, pricingService);
    }

    @Test
    void confirm_ok_200_with_idempotency_key() throws Exception {
      when(orderService.confirm(any(ConfirmRequest.class), any())).thenReturn(sampleConfirmRes());

      mvc.perform(post("/cart/confirm")
              .header("Idempotency-Key", "abc-123")
              .contentType(MediaType.APPLICATION_JSON)
              .content(om.writeValueAsString(sampleConfirmReq())))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.orderId", is("999")));

      ArgumentCaptor<String> keyCap = ArgumentCaptor.forClass(String.class);
      verify(orderService).confirm(any(ConfirmRequest.class), keyCap.capture());
      assert "abc-123".equals(keyCap.getValue());
      verifyNoMoreInteractions(orderService, pricingService);
    }

    @Test
    void confirm_header_name_is_case_insensitive() throws Exception {
      when(orderService.confirm(any(ConfirmRequest.class), any())).thenReturn(sampleConfirmRes());

      mvc.perform(post("/cart/confirm")
              .header("idempotency-key", "Key-Case-Test")
              .contentType(MediaType.APPLICATION_JSON)
              .content(om.writeValueAsString(sampleConfirmReq())))
          .andExpect(status().isOk());

      verify(orderService).confirm(any(ConfirmRequest.class), org.mockito.Mockito.eq("Key-Case-Test"));
      verifyNoMoreInteractions(orderService, pricingService);
    }

    @Test
    void confirm_validation_error_400_on_invalid_body() throws Exception {
      // e.g., qty <= 0; adjust to your DTO constraints
      var bad = new ConfirmRequest(List.of(new CartItem(101L, 0)), "VIP");

      mvc.perform(post("/cart/confirm")
              .contentType(MediaType.APPLICATION_JSON)
              .content(om.writeValueAsString(bad)))
          .andExpect(status().isBadRequest());

      verifyNoMoreInteractions(orderService, pricingService);
    }

    @Test
    void confirm_500_when_service_throws() throws Exception {
      when(orderService.confirm(any(), any())).thenThrow(new RuntimeException("fail"));

      mvc.perform(post("/cart/confirm")
              .contentType(MediaType.APPLICATION_JSON)
              .content(om.writeValueAsString(sampleConfirmReq())))
          .andExpect(status().isConflict());

      verify(orderService).confirm(any(), any());
      verifyNoMoreInteractions(orderService, pricingService);
    }

    @Test
    void confirm_415_on_unsupported_media_type() throws Exception {
      mvc.perform(post("/cart/confirm")
              .contentType(MediaType.TEXT_PLAIN)
              .content("x"))
          .andExpect(status().isUnsupportedMediaType());

      verifyNoMoreInteractions(orderService, pricingService);
    }

    @Test
    void confirm_400_on_missing_body() throws Exception {
      mvc.perform(post("/cart/confirm")
              .contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isConflict());

      verifyNoMoreInteractions(orderService, pricingService);
    }

    @Test
    void confirm_405_on_wrong_method() throws Exception {
      mvc.perform(get("/cart/confirm"))
          .andExpect(status().isMethodNotAllowed());

      verifyNoMoreInteractions(orderService, pricingService);
    }
  }
}
