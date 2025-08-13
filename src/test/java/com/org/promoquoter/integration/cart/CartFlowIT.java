package com.org.promoquoter.integration.cart;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.org.promoquoter.dto.cart.CartItem;
import com.org.promoquoter.dto.cart.ConfirmRequest;
import com.org.promoquoter.dto.cart.ConfirmResponse;
import com.org.promoquoter.dto.cart.QuoteRequest;
import com.org.promoquoter.dto.cart.QuoteResponse;
import com.org.promoquoter.entities.Product;
import com.org.promoquoter.entities.Promotion;
import com.org.promoquoter.entities.PromotionType;
import com.org.promoquoter.repositories.IdempotencyRepository;
import com.org.promoquoter.repositories.OrderRepository;
import com.org.promoquoter.repositories.ProductRepository;
import com.org.promoquoter.repositories.PromotionRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CartFlowIT {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @Autowired ProductRepository products;
    @Autowired PromotionRepository promotions;
    @Autowired OrderRepository orders;
    @Autowired IdempotencyRepository idems;

    @BeforeEach
    void cleanup() {
        idems.deleteAll();
        orders.deleteAll();
        promotions.deleteAll();
        products.deleteAll();
    }

    // ----------- helpers -----------

    private Product newProduct(String name, String category, String price, int stock) {
        return products.save(Product.builder()
                .name(name).category(category)
                .price(new BigDecimal(price)).stock(stock)
                .build());
    }

    private Promotion newPercentCategory(String name, String category, String percent, Integer priority) {
        return promotions.save(Promotion.builder()
                .type(PromotionType.PERCENT_OFF_CATEGORY)
                .name(name)
                .priority(priority)
                .enabled(true)
                .category(category)
                .percent(new BigDecimal(percent))
                .build());
    }

    private Promotion newBuyXGetY(String name, long productId, int buy, int free, Integer priority) {
        return promotions.save(Promotion.builder()
                .type(PromotionType.BUY_X_GET_Y)
                .name(name)
                .priority(priority)
                .enabled(true)
                .productId(productId)
                .buyQty(buy)
                .freeQty(free)
                .build());
    }

    private String toJson(Object o) throws Exception { return om.writeValueAsString(o); }

    // ----------- tests -----------
    @Test
    @DisplayName("POST /cart/quote — applies promotions via pipeline and returns items, total, and audit")
    void quote_appliesPromotions_andTotals() throws Exception {
        // Seed products
        var laptop = newProduct("Laptop", "ELECTRONICS", "1000.00", 50);
        var mouse  = newProduct("Mouse",  "ELECTRONICS", "25.00",   50);

        // Seed promotions:
        // 1) 10% category percent-off (our CartContext returns all lines for any category)
        // 2) Buy 1 Get 1 free for the mouse
        newPercentCategory("Cat10", "ELECTRONICS", "10", 1);
        newBuyXGetY("Mouse B1G1", mouse.getId(), 1, 1, 2);

        var req = new QuoteRequest(
                List.of(new CartItem(laptop.getId(), 1),
                        new CartItem(mouse.getId(), 2)),
                "REGULAR");

        var mvcRes = mvc.perform(post("/cart/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        var body = mvcRes.getResponse().getContentAsString();
        var quote = om.readValue(body, QuoteResponse.class);

        // Items echoed
        assertThat(quote.items()).hasSize(2);
        var iLaptop = quote.items().stream().filter(i -> i.productId().equals(laptop.getId())).findFirst().orElseThrow();
        var iMouse  = quote.items().stream().filter(i -> i.productId().equals(mouse.getId())).findFirst().orElseThrow();

        // Laptop: 10% off => 1000 - 100 = 900
        assertThat(iLaptop.originalSubtotal()).isEqualByComparingTo("1000.00");
        assertThat(iLaptop.discount()).isEqualByComparingTo("100.00");
        assertThat(iLaptop.finalSubtotal()).isEqualByComparingTo("900.00");

        // Mouse: qty=2, B1G1 => 1 free => discount=1*25=25; 10% category also applies,
        // but your PercentOffCategory rule applies per-line independently.
        // Because both promotions are in the pipeline and our implementations **add** discounts,
        // the percent rule will also add 10% of original (50*0.10=5.00) => total mouse discount 30.00
        // Final mouse subtotal: 50 - 30 = 20.00
        assertThat(iMouse.originalSubtotal()).isEqualByComparingTo("50.00");
        assertThat(iMouse.discount()).isEqualByComparingTo("30.00");
        assertThat(iMouse.finalSubtotal()).isEqualByComparingTo("20.00");

        // Total after both promos
        assertThat(quote.total()).isEqualByComparingTo("920.00");

        // Applied promotions (names) include both
        assertThat(quote.appliedPromotions()).contains("Cat10", "Mouse B1G1");
        // Audit present (details depend on rule text)
        assertThat(quote.auditTrail()).isNotEmpty();
    }

    @Test
    @DisplayName("POST /cart/confirm — reserves stock, persists order, stores idempotency record, returns total from quote")
    void confirm_reservesStock_andPersists_withIdempotency() throws Exception {
        var cable = newProduct("Cable", "ELEC", "5.00", 10);
        newPercentCategory("Cat10", "ELEC", "10", 1); // 10% of 5*2=10 => 1 => final 9

        var idemKey = "idem-abc";

        var confirmReq = new ConfirmRequest(
                List.of(new CartItem(cable.getId(), 2)),
                "REGULAR");

        var mvcRes = mvc.perform(post("/cart/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", idemKey)
                        .content(toJson(confirmReq)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.orderId").exists())
                .andExpect(jsonPath("$.total").exists())
                .andReturn();

        // Assert persistence effects
        assertThat(orders.count()).isEqualTo(1);
        assertThat(idems.findById(idemKey)).isPresent();

        // Stock reduced by 2
        var freshCable = products.findById(cable.getId()).orElseThrow();
        assertThat(freshCable.getStock()).isEqualTo(8);

        // Total matches quoted total (9.00)
        var resp = om.readValue(mvcRes.getResponse().getContentAsString(), ConfirmResponse.class);
        assertThat(resp.total()).isEqualByComparingTo("9.00");
    }

    @Test
    @DisplayName("POST /cart/confirm — second call with same Idempotency-Key returns the original order (no further stock change)")
    void confirm_idempotencyHit_returnsPrevious() throws Exception {
        var book = newProduct("Book", "BOOKS", "20.00", 5);
        newPercentCategory("Cat10", "BOOKS", "10", 1); // 10% of 20*1 => 2 => total 18

        var req = new ConfirmRequest(List.of(new CartItem(book.getId(), 1)), "REGULAR");
        var key = "same-key";

        // First call creates order & idempotency record
        var first = mvc.perform(post("/cart/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", key)
                        .content(toJson(req)))
                .andExpect(status().isOk())
                .andReturn();

        var firstResp = om.readValue(first.getResponse().getContentAsString(), ConfirmResponse.class);
        var stockAfterFirst = products.findById(book.getId()).orElseThrow().getStock();

        // Second call with SAME key should return the same order, without reducing stock again
        var second = mvc.perform(post("/cart/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", key)
                        .content(toJson(req)))
                .andExpect(status().isOk())
                .andReturn();

        var secondResp = om.readValue(second.getResponse().getContentAsString(), ConfirmResponse.class);

        assertThat(secondResp.orderId()).isEqualTo(firstResp.orderId());
        assertThat(secondResp.total()).isEqualByComparingTo(firstResp.total());
        assertThat(products.findById(book.getId()).orElseThrow().getStock()).isEqualTo(stockAfterFirst);
        assertThat(orders.count()).isEqualTo(1); // no extra order created
    }

    @Test
    @DisplayName("POST /cart/confirm — insufficient stock returns a mapped client error and does not persist")
    void confirm_insufficientStock_clientError_noPersist() throws Exception {
        var toy = newProduct("Toy", "TOYS", "12.00", 1); // stock=1
        newPercentCategory("Cat5", "TOYS", "5", 1);

        var req = new ConfirmRequest(List.of(new CartItem(toy.getId(), 2)), "REGULAR"); // need 2, have 1

        mvc.perform(post("/cart/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                        .andExpect(status().is4xxClientError());

        assertThat(orders.count()).isZero();
        assertThat(products.findById(toy.getId()).orElseThrow().getStock()).isEqualTo(1);
        assertThat(idems.count()).isZero();
    }

    @Test
    @DisplayName("POST /cart/quote — product missing yields a mapped client error")
    void quote_missingProduct_clientError() throws Exception {
        // No products saved; request references a non-existent id=999
        var req = new QuoteRequest(List.of(new CartItem(999L, 1)), "REGULAR");

        mvc.perform(post("/cart/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                        .andExpect(status().is4xxClientError());

        assertThat(promotions.count()).isZero();
        assertThat(products.count()).isZero();
    }
}
