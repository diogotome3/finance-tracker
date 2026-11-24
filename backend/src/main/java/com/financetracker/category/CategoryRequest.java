package com.financetracker.category;

import com.financetracker.common.domain.TransactionType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CategoryRequest (
    @NotBlank @Size(max = 50) String name,
    @NotNull TransactionType type,
    @Size(max = 255) String description,
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "must be a hex colour like #8B5CF6") String color
) {
    public CategoryRequest {
        if (name != null) {
            name = name.strip();
        }
        if (description != null) {
            description = description.strip();
            if (description.isBlank()) {
                description = null;
            }
        }
        if (color != null) {
            color = color.strip();
        }
    }
}
