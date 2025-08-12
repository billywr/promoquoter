package com.org.promoquoter.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.org.promoquoter.entities.Product;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> { }
