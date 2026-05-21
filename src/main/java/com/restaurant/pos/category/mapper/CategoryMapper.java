package com.restaurant.pos.category.mapper;

import com.restaurant.pos.category.domain.ExpenseCategory;
import com.restaurant.pos.category.dto.CategoryResponse;
import com.restaurant.pos.category.dto.CreateCategoryRequest;
import com.restaurant.pos.category.dto.UpdateCategoryRequest;
import org.springframework.stereotype.Component;

/**
 * Maps between Category entities and their DTOs.
 */
@Component
public class CategoryMapper {

    public ExpenseCategory toEntity(CreateCategoryRequest request) {
        ExpenseCategory category = new ExpenseCategory();
        category.setName(request.getName() != null ? request.getName().trim() : null);
        category.setSortOrder(request.getSortOrder());
        return category;
    }

    public void updateEntity(ExpenseCategory entity, UpdateCategoryRequest request) {
        if (request.getName() != null) {
            entity.setName(request.getName().trim());
        }
        if (request.getSortOrder() != null) {
            entity.setSortOrder(request.getSortOrder());
        }
    }

    public CategoryResponse toResponse(ExpenseCategory entity) {
        return CategoryResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .sortOrder(entity.getSortOrder())
                .active("Y".equalsIgnoreCase(entity.getIsactive()))
                .orgId(entity.getOrgId())
                .scope(entity.getOrgId() == null ? "GLOBAL" : "BRANCH")
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public CategoryResponse.SimpleCategory toSimpleResponse(ExpenseCategory entity) {
        return CategoryResponse.SimpleCategory.builder()
                .id(entity.getId())
                .name(entity.getName())
                .build();
    }
}
