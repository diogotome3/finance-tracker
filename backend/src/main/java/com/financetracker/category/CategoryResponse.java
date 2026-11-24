package com.financetracker.category;

import com.financetracker.common.domain.TransactionType;
import java.time.Instant;

public record CategoryResponse (
    Long id,
    String name,
    TransactionType type,
    String description,
    String color,
    Instant createdAt,
    Instant updatedAt
) {}
