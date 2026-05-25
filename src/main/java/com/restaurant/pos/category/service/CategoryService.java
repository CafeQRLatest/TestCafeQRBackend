package com.restaurant.pos.category.service;

import com.restaurant.pos.category.domain.ExpenseCategory;
import com.restaurant.pos.category.dto.CategoryResponse;
import com.restaurant.pos.category.dto.CreateCategoryRequest;
import com.restaurant.pos.category.dto.UpdateCategoryRequest;
import com.restaurant.pos.category.mapper.CategoryMapper;
import com.restaurant.pos.category.repository.ExpenseCategoryRepository;
import com.restaurant.pos.common.context.ContextProvider;
import com.restaurant.pos.common.exception.DuplicateResourceException;
import com.restaurant.pos.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import org.springframework.validation.annotation.Validated;

/**
 * Service for managing expense categories.
 * Completely decoupled from global ThreadLocal security/tenant states
 * via ContextProvider and ExpenseCategoryPolicy.
 */
@Slf4j
@Service
@Validated
@RequiredArgsConstructor
public class CategoryService {

    private final ExpenseCategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;
    private final ContextProvider contextProvider;
    private final ExpenseCategoryPolicy categoryPolicy;

    // @Cacheable(value = "expenseCategories", keyGenerator = "categoryKeyGenerator")
    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategories(String scope, UUID branchId) {
        UUID clientId = contextProvider.getCurrentTenant();
        ExpenseCategoryPolicy.CategoryScope resolvedScope = categoryPolicy.resolveReadScope(scope, branchId, contextProvider);
        
        log.info("Fetching expense categories | clientId={} | scope={} | orgId={}",
                clientId, resolvedScope.scope(), resolvedScope.orgId());

        List<ExpenseCategory> categories = resolvedScope.all()
                ? categoryRepository.findByClientIdOrderBySortOrderAsc(clientId)
                : categoryRepository.findByClientIdAndOrgIdOrderBySortOrderAsc(clientId, resolvedScope.orgId());
        
        return categories.stream()
                .map(categoryMapper::toResponse)
                .toList();
    }

    @Transactional
    @CacheEvict(value = "expenseCategories", allEntries = true)
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        UUID clientId = contextProvider.getCurrentTenant();
        ExpenseCategoryPolicy.CategoryScope targetScope = categoryPolicy.resolveWriteScope(request.getScope(), request.getBranchId(), contextProvider);
        UUID orgId = targetScope.orgId();
        
        log.info("Creating category '{}' | scope={} | orgId={}",
                request.getName(), targetScope.scope(), orgId);

        if (categoryRepository.existsByNameIgnoreCaseAndClientIdAndOrgId(request.getName(), clientId, orgId)) {
            throw new DuplicateResourceException(
                    "Expense category '" + request.getName() + "' already exists in this branch."
            );
        }

        ExpenseCategory category = categoryMapper.toEntity(request);
        category.setClientId(clientId);
        category.setOrgId(orgId);
        
        // Resolve owner dynamically for audit fields
        String auditUser = resolveAuditUser();
        category.setCreatedBy(auditUser);
        category.setUpdatedBy(auditUser);

        ExpenseCategory saved = categoryRepository.save(category);
        return categoryMapper.toResponse(saved);
    }

    @Transactional
    @CacheEvict(value = "expenseCategories", allEntries = true)
    public CategoryResponse updateCategory(UUID id, UpdateCategoryRequest request) {
        log.info("Updating category | id={}", id);

        ExpenseCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));

        categoryPolicy.validateOwnership(category, contextProvider);

        if (!category.getName().equalsIgnoreCase(request.getName())) {
            if (categoryRepository.existsByNameIgnoreCaseAndClientIdAndOrgId(
                    request.getName(), category.getClientId(), category.getOrgId())) {
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
    public void deactivateCategory(UUID id) {
        log.info("Deactivating category | id={}", id);

        ExpenseCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));

        categoryPolicy.validateOwnership(category, contextProvider);

        category.deactivate();
        categoryRepository.save(category);
    }

    private String resolveAuditUser() {
        UUID userId = contextProvider.getCurrentUserId();
        if (userId != null) {
            return userId.toString();
        }
        String email = contextProvider.getCurrentUserEmail();
        if (email == null || email.isBlank()) {
            log.warn("No authenticated user found for audit, defaulting to SYSTEM");
            return "SYSTEM";
        }
        return email.trim();
    }
}
