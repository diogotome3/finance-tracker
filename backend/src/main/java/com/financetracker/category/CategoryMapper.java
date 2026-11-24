package com.financetracker.category;

import org.springframework.stereotype.Component;

@Component
public class CategoryMapper {

    public CategoryResponse toResponse(Category category) {
        return new CategoryResponse(
            category.getId(),
            category.getName(),
            category.getType(),
            category.getDescription(),
            category.getColor(),
            category.getCreatedAt(),
            category.getUpdatedAt()
        );
    }

    public Category toEntity(CategoryRequest request, Long userId) {
        Category category = new Category(
            userId,
            request.name(),
            request.type()
        );
        updateEntity(category, request);
        return category;
    }

    public void updateEntity(Category category, CategoryRequest request) {
        category.setName(request.name());
        category.setType(request.type());
        category.setDescription(request.description());
        category.setColor(request.color());
    }

}
