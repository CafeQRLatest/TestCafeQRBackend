package com.restaurant.pos.category.service;

import com.restaurant.pos.category.domain.ExpenseCategory;
import com.restaurant.pos.category.dto.CategoryResponse;
import com.restaurant.pos.category.dto.CreateCategoryRequest;
import com.restaurant.pos.category.dto.UpdateCategoryRequest;
import com.restaurant.pos.category.mapper.CategoryMapper;
import com.restaurant.pos.category.repository.ExpenseCategoryRepository;
import com.restaurant.pos.common.exception.DuplicateResourceException;
import com.restaurant.pos.common.exception.ResourceNotFoundException;
import com.restaurant.pos.common.tenant.TenantContext;
import com.restaurant.pos.common.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing expense categories.
 * Now lives in its own module for cross-domain reusability.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final ExpenseCategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    @Cacheable(
            value = "expenseCategories",
            key = "#root.methodName + '_' + T(com.restaurant.pos.common.tenant.TenantContext).getCurrentOrg() + '_' + T(com.restaurant.pos.common.util.SecurityUtils).getCurrentUserEmail()"
    )
    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategories() {
        UUID clientId = TenantContext.getCurrentTenant();
        UUID orgId = TenantContext.getCurrentOrg();
        String profileOwner = currentProfileOwner();
        
        log.info("Fetching expense categories | clientId={} | orgId={} | profileOwner={}", clientId, orgId, profileOwner);
        
        return categoryRepository.findByClientIdAndOrgIdAndCreatedByOrderBySortOrderAsc(clientId, orgId, profileOwner)
                .stream()
                .map(categoryMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    @CacheEvict(value = "expenseCategories", allEntries = true)
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        UUID clientId = TenantContext.getCurrentTenant();
        UUID orgId = TenantContext.getCurrentOrg();
        String profileOwner = currentProfileOwner();
        
        log.info("Creating category '{}' | orgId={} | profileOwner={}", request.getName(), orgId, profileOwner);

        if (categoryRepository.existsByNameIgnoreCaseAndClientIdAndOrgIdAndCreatedBy(request.getName(), clientId, orgId, profileOwner)) {
            throw new DuplicateResourceException(
                    "Expense category '" + request.getName() + "' already exists in this profile."
            );
        }

        ExpenseCategory category = categoryMapper.toEntity(request);
        category.setClientId(clientId);
        category.setOrgId(orgId);
        category.setCreatedBy(profileOwner);
        category.setUpdatedBy(profileOwner);

        ExpenseCategory saved = categoryRepository.save(category);
        return categoryMapper.toResponse(saved);
    }

    @Transactional
    @CacheEvict(value = "expenseCategories", allEntries = true)
    public CategoryResponse updateCategory(UUID id, UpdateCategoryRequest request) {
        log.info("Updating category | id={}", id);

        String profileOwner = currentProfileOwner();
        ExpenseCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));

        validateOwnership(category, profileOwner);

        if (!category.getName().equalsIgnoreCase(request.getName())) {
            if (categoryRepository.existsByNameIgnoreCaseAndClientIdAndOrgIdAndCreatedBy(
                    request.getName(), category.getClientId(), category.getOrgId(), profileOwner)) {
                throw new DuplicateResourceException(
                        "Category name '" + request.getName() + "' is already in use."
                );
            }
        }

        categoryMapper.updateEntity(category, request);

        // Handle status via domain behavior
        if (request.getActive() != null) {
            if (request.getActive()) category.activate();
            else category.deactivate();
        }

        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @Transactional
    @CacheEvict(value = "expenseCategories", allEntries = true)
    public void deleteCategory(UUID id) {
        log.info("Soft-deleting category | id={}", id);

        String profileOwner = currentProfileOwner();
        ExpenseCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));

        validateOwnership(category, profileOwner);

        category.deactivate();
        categoryRepository.save(category);
    }

    private void validateOwnership(ExpenseCategory category, String profileOwner) {
        UUID clientId = TenantContext.getCurrentTenant();
        UUID orgId = TenantContext.getCurrentOrg();
        if (clientId != null && category.getClientId() != null && !clientId.equals(category.getClientId())) {
            throw new AccessDeniedException("Unauthorized access to tenant-specific category.");
        }
        if (orgId != null && category.getOrgId() != null && !orgId.equals(category.getOrgId())) {
            throw new AccessDeniedException("Unauthorized access to branch-specific category.");
        }
        if (profileOwner != null && category.getCreatedBy() != null && !profileOwner.equalsIgnoreCase(category.getCreatedBy())) {
            throw new AccessDeniedException("Unauthorized access to profile-specific category.");
        }
        if (category.getCreatedBy() == null || category.getCreatedBy().isBlank()) {
            throw new AccessDeniedException("Unauthorized access to legacy unowned category.");
        }
    }

    private String currentProfileOwner() {
        String owner = SecurityUtils.getCurrentUserEmail();
        if (owner == null || owner.isBlank()) {
            throw new AccessDeniedException("Authenticated profile is required for expense categories.");
        }
        return owner.trim();
    }
}
