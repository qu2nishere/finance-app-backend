package com.quan.ledgerservice.category.dto;

import com.quan.ledgerservice.category.Category;
import com.quan.ledgerservice.category.CategoryFlow;

import java.util.UUID;

public record CategoryResponse(
        UUID id,
        UUID parentId,
        String name,
        CategoryFlow flow,
        String icon,
        String color,
        boolean system,
        int usageCount
) {
    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getParentId(),
                category.getName(),
                category.getFlow(),
                category.getIcon(),
                category.getColor(),
                category.isSystem(),
                category.getUsageCount()
        );
    }
}
