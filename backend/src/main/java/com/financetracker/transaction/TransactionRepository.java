package com.financetracker.transaction;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByIdAndUserId(Long id, Long userId);

    // "CategoryId" is resolved as the nested path category.id; Hibernate reads the
    // category_id column directly, without joining categories.
    boolean existsByCategoryId(Long categoryId);

    long countByCategoryId(Long categoryId);
}
