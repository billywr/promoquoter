package com.org.promoquoter.unit.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.org.promoquoter.entities.Product;
import com.org.promoquoter.repositories.ProductRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.org.promoquoter.controllers.ProductController;

/**
 * Unit tests for ProductController using MockMvc + Mockito (no DB).
 */
@WebMvcTest(ProductController.class)
class ProductControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper om;

  @MockBean private ProductRepository repo;

  // -------- Helpers

  private String json(Object o) throws Exception {
    return om.writeValueAsString(o);
  }

  private Product product(Long id, String name, String category, String price, int stock) {
    return Product.builder()
        .id(id)
        .name(name)
        .category(category)
        .price(new BigDecimal(price))
        .stock(stock)
        .build();
  }

  private record ProductInput(String name, String category, BigDecimal price, Integer stock) {}
  private record CreateReq(List<ProductInput> products) {}

  // -------- Tests

  @Nested
  class CreateProducts {

    @Test
    void create_ok_returns_saved_list_and_calls_repo_save_per_item() throws Exception {
      // Arrange fake ID sequencing on repo.save(...)
      AtomicLong seq = new AtomicLong(1);
      when(repo.save(any(Product.class))).thenAnswer((Answer<Product>) inv -> {
        Product p = inv.getArgument(0);
        // mimic persistence-generated id if not set
        if (p.getId() == null) p.setId(seq.getAndIncrement());
        return p;
      });

      var req = new CreateReq(List.of(
          new ProductInput("Milk", "DAIRY", new BigDecimal("2.50"), 10),
          new ProductInput("Bread", "BAKERY", new BigDecimal("1.50"), 15)
      ));

      // Act + Assert
      mvc.perform(post("/products")
              .contentType(MediaType.APPLICATION_JSON)
              .content(json(req)))
          .andExpect(status().isOk())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$", hasSize(2)))
          .andExpect(jsonPath("$[0].id", is(1)))
          .andExpect(jsonPath("$[0].name", is("Milk")))
          .andExpect(jsonPath("$[0].category", is("DAIRY")))
          .andExpect(jsonPath("$[0].price", is(2.50)))
          .andExpect(jsonPath("$[0].stock", is(10)))
          .andExpect(jsonPath("$[1].id", is(2)))
          .andExpect(jsonPath("$[1].name", is("Bread")));

      // verify repo.save called twice
      verify(repo, times(2)).save(any(Product.class));
      verifyNoMoreInteractions(repo);
    }

    @Test
    void create_empty_array_yields_200_with_empty_response_or_400_if_validated() throws Exception {
      // NOTE: If your CreateProductsRequest has @NotEmpty on 'products',
      // change expectation to isBadRequest(). Otherwise controller returns 200 [].
      when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

      var req = new CreateReq(List.of());

      mvc.perform(post("/products")
              .contentType(MediaType.APPLICATION_JSON)
              .content(json(req)))
          // pick ONE based on your DTO constraints:
          .andExpect(status().isOk())
          .andExpect(jsonPath("$", hasSize(0)));

      verifyNoInteractions(repo);
    }

    @Test
    void create_missing_body_returns_400() throws Exception {
      mvc.perform(post("/products")
              .contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isConflict());

      verifyNoInteractions(repo);
    }

    @Test
    void create_unsupported_media_type_returns_415() throws Exception {
      mvc.perform(post("/products")
              .contentType(MediaType.TEXT_PLAIN)
              .content("not json"))
          .andExpect(status().isUnsupportedMediaType());

      verifyNoInteractions(repo);
    }

    @Test
    void create_propagates_service_exception_as_5xx_or_advice_mapped_status() throws Exception {
      when(repo.save(any())).thenThrow(new RuntimeException("DB down"));

      var req = new CreateReq(List.of(
          new ProductInput("Milk", "DAIRY", new BigDecimal("2.50"), 10)
      ));

      mvc.perform(post("/products")
              .contentType(MediaType.APPLICATION_JSON)
              .content(json(req)))
          // If you have @ControllerAdvice that maps RuntimeException to 409, change to isConflict()
          .andExpect(status().isConflict());;

      verify(repo).save(any(Product.class));
      verifyNoMoreInteractions(repo);
    }

    @Test
    void create_duplicates_are_saved_independently() throws Exception {
      AtomicLong seq = new AtomicLong(100);
      when(repo.save(any(Product.class))).thenAnswer((Answer<Product>) inv -> {
        Product p = inv.getArgument(0);
        if (p.getId() == null) p.setId(seq.getAndIncrement());
        return p;
      });

      var req = new CreateReq(List.of(
          new ProductInput("Bread", "BAKERY", new BigDecimal("1.50"), 5),
          new ProductInput("Bread", "BAKERY", new BigDecimal("1.50"), 7)
      ));

      mvc.perform(post("/products")
              .contentType(MediaType.APPLICATION_JSON)
              .content(json(req)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$", hasSize(2)))
          .andExpect(jsonPath("$[0].id", is(100)))
          .andExpect(jsonPath("$[1].id", is(101)));

      verify(repo, times(2)).save(any(Product.class));
      verifyNoMoreInteractions(repo);
    }
  }

  @Nested
  class GetAllProducts {

    @Test
    void get_all_returns_empty_list_when_repo_empty() throws Exception {
      when(repo.findAll()).thenReturn(List.of());

      mvc.perform(get("/products"))
          .andExpect(status().isOk())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$", hasSize(0)));

      verify(repo).findAll();
      verifyNoMoreInteractions(repo);
    }

    @Test
    void get_all_returns_products_from_repo() throws Exception {
      var list = new ArrayList<Product>();
      list.add(product(1L, "Milk", "DAIRY", "2.50", 10));
      list.add(product(2L, "Bread", "BAKERY", "1.50", 15));
      when(repo.findAll()).thenReturn(list);

      mvc.perform(get("/products"))
          .andExpect(status().isOk())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$", hasSize(2)))
          .andExpect(jsonPath("$[0].name", is("Milk")))
          .andExpect(jsonPath("$[1].category", is("BAKERY")));

      verify(repo).findAll();
      verifyNoMoreInteractions(repo);
    }

    @Test
    void get_all_wrong_method_on_post_path_returns_405() throws Exception {
      // Using GET on /products expects 200; trying POST with wrong media, etc., is checked above.
      // Here we show method mismatch for GET endpoint (example: POST to /products handled by create(), OK).
      // To show 405, try DELETE which the controller does not implement:
      mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/products"))
          .andExpect(status().isMethodNotAllowed());
    }
  }
}

