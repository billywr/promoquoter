package com.org.promoquoter.integration.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.org.promoquoter.entities.Product;
import com.org.promoquoter.repositories.ProductRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ProductControllerIT {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired ProductRepository repo;

    @BeforeEach
    void clean() {
        repo.deleteAll();
    }

    // -------------------- helpers --------------------
    private String json(Object o) throws Exception { return om.writeValueAsString(o); }

    private Product save(String name, String category, String price, int stock) {
        return repo.save(Product.builder()
                .name(name).category(category)
                .price(new BigDecimal(price)).stock(stock)
                .build());
    }

    // -------------------- tests --------------------

    @Test
    @DisplayName("POST /products — saves multiple products and returns 200 with array payload")
    void create_multiple_ok() throws Exception {
        String body = """
        {
          "products": [
            {"name":"Cable","category":"ELEC","price":5.00,"stock":10},
            {"name":"Mouse","category":"ELEC","price":25.00,"stock":5}
          ]
        }
        """;

        mvc.perform(post("/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
           .andExpect(status().isOk())
           .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
           .andExpect(jsonPath("$.length()").value(2))
           .andExpect(jsonPath("$[0].name").value("Cable"))
           .andExpect(jsonPath("$[1].price").value(25.00));

        List<Product> all = repo.findAll();
        assertThat(all).hasSize(2);

        Product cable = all.stream().filter(p -> p.getName().equals("Cable")).findFirst().orElseThrow();
        assertThat(cable.getCategory()).isEqualTo("ELEC");
        assertThat(cable.getPrice()).isEqualByComparingTo("5.00");
        assertThat(cable.getStock()).isEqualTo(10);

        Product mouse = all.stream().filter(p -> p.getName().equals("Mouse")).findFirst().orElseThrow();
        assertThat(mouse.getCategory()).isEqualTo("ELEC");
        assertThat(mouse.getPrice()).isEqualByComparingTo("25.00");
        assertThat(mouse.getStock()).isEqualTo(5);
    }

    @Test
    @DisplayName("POST /products — maps all fields correctly for a single product")
    void create_mapsFields_single() throws Exception {
        String body = """
        {
          "products": [
            {"name":"Keyboard","category":"ELEC","price":17.99,"stock":3}
          ]
        }
        """;

        mvc.perform(post("/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
           .andExpect(status().isOk());

        var saved = repo.findAll();
        assertThat(saved).hasSize(1);
        Product kb = saved.get(0);
        assertThat(kb.getName()).isEqualTo("Keyboard");
        assertThat(kb.getCategory()).isEqualTo("ELEC");
        assertThat(kb.getPrice()).isEqualByComparingTo("17.99");
        assertThat(kb.getStock()).isEqualTo(3);
    }

    @Test
    @DisplayName("POST /products — empty list returns 200 with [] and does not persist")
    void create_emptyList_ok() throws Exception {
        String body = """
        { "products": [] }
        """;

        mvc.perform(post("/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
           .andExpect(status().isOk())
           .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
           .andExpect(jsonPath("$.length()").value(0));

        assertThat(repo.count()).isZero();
    }

    @Test
    @DisplayName("POST /products — missing body yields 409 (mapped) and nothing is saved")
    void create_missingBody_409() throws Exception {
        mvc.perform(post("/products")
                .contentType(MediaType.APPLICATION_JSON))
           .andExpect(status().isConflict()) // your app maps HttpMessageNotReadableException to 409
           .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        assertThat(repo.count()).isZero();
    }

    @Test
    @DisplayName("POST /products — invalid JSON payload (e.g., price as string) yields 4xx and nothing is saved")
    void create_invalidJson_4xx() throws Exception {
        String bad = """
        {
          "products": [
            {"name":"Invalid","category":"ELEC","price":"oops","stock":1}
          ]
        }
        """;

        mvc.perform(post("/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(bad))
           .andExpect(status().is4xxClientError());

        assertThat(repo.count()).isZero();
    }

    @Test
    @DisplayName("GET /products — returns [] when DB is empty")
    void getAll_empty_ok() throws Exception {
        mvc.perform(get("/products")
                .accept(MediaType.APPLICATION_JSON))
           .andExpect(status().isOk())
           .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
           .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("GET /products — returns all persisted products")
    void getAll_populated_ok() throws Exception {
        save("AA Battery", "ELECTRICAL", "0.99", 100);
        save("USB-C Cable", "ELEC", "7.50", 25);

        mvc.perform(get("/products")
                .accept(MediaType.APPLICATION_JSON))
           .andExpect(status().isOk())
           .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
           .andExpect(jsonPath("$.length()").value(2))
           .andExpect(jsonPath("$[?(@.name=='AA Battery')]").exists())
           .andExpect(jsonPath("$[?(@.name=='USB-C Cable')]").exists());
    }
}

