package com.org.promoquoter.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.org.promoquoter.entities.Promotion;

@Repository
public interface PromotionRepository extends JpaRepository<Promotion, Long> { }

