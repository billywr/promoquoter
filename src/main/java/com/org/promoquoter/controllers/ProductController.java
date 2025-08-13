package com.org.promoquoter.controllers;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.org.promoquoter.dto.product.ProductsRequest;
import com.org.promoquoter.entities.Product;
import com.org.promoquoter.repositories.ProductRepository;
import java.util.*;

@RestController
@RequestMapping("/products")
public class ProductController {
  private final ProductRepository repo;
  public ProductController(ProductRepository repo){this.repo = repo;}

  @PostMapping
  public ResponseEntity<?> create(@Valid @RequestBody ProductsRequest req){
    var saved = new ArrayList<Product>();
    for (var p : req.products()) {
      saved.add(repo.save(Product.builder().name(p.name()).category(p.category()).price(p.price()).stock(p.stock()).build()));
    }
    return ResponseEntity.ok(saved);
  }

  @GetMapping
  public ResponseEntity<List<Product>> getAllProducts() {
    return ResponseEntity.ok(repo.findAll());
  }
}
