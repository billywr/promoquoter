package com.org.promoquoter.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.org.promoquoter.entities.IdempotencyRecord;

@Repository
public interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, String> { }
