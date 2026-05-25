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
        category.updateName(request.getName());
        category.setSortOrder(request.getSortOrder());
        return category;
    }

    public void updateEntity(ExpenseCategory entity, UpdateCategoryRequest request) {
        if (request.getName() != null) {
            entity.updateName(request.getName());
        }
        if (request.getSortOrder() != null) {
            entity.updateSortOrder(request.getSortOrder());
        }
    }

    public CategoryResponse toResponse(ExpenseCategory entity) {
        return CategoryResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .sortOrder(entity.getSortOrder())
                .active(entity.isActive())
                .orgId(entity.getOrgId())
                .scope(entity.getScope())
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
