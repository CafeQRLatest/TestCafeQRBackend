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
            key = "#root.methodName + '_' + (#scope ?: 'DEFAULT') + '_' + (#branchId ?: 'NONE') + '_' + T(com.restaurant.pos.common.tenant.TenantContext).getCurrentOrg() + '_' + T(com.restaurant.pos.common.util.SecurityUtils).getCurrentUserEmail()"
    )
    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategories(String scope, UUID branchId) {
        UUID clientId = TenantContext.getCurrentTenant();
        CategoryScope resolvedScope = resolveReadScope(scope, branchId);
        String profileOwner = currentProfileOwner();
        
        log.info("Fetching expense categories | clientId={} | scope={} | orgId={} | profileOwner={}",
                clientId, resolvedScope.scope(), resolvedScope.orgId(), profileOwner);

        List<ExpenseCategory> categories = resolvedScope.all()
                ? categoryRepository.findByClientIdAndCreatedByOrderBySortOrderAsc(clientId, profileOwner)
                : categoryRepository.findByClientIdAndOrgIdAndCreatedByOrderBySortOrderAsc(clientId, resolvedScope.orgId(), profileOwner);
        
        return categories.stream()
                .map(categoryMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    @CacheEvict(value = "expenseCategories", allEntries = true)
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        UUID clientId = TenantContext.getCurrentTenant();
        CategoryScope targetScope = resolveWriteScope(request.getScope(), request.getBranchId());
        UUID orgId = targetScope.orgId();
        String profileOwner = currentProfileOwner();
        
        log.info("Creating category '{}' | scope={} | orgId={} | profileOwner={}",
                request.getName(), targetScope.scope(), orgId, profileOwner);

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
        if (!canManageAllScopes() && orgId != null && !orgId.equals(category.getOrgId())) {
            throw new AccessDeniedException("Unauthorized access to branch-specific category.");
        }
        if (!canManageAllScopes() && orgId == null && category.getOrgId() == null) {
            throw new AccessDeniedException("Unauthorized access to organization category.");
        }
        if (profileOwner != null && category.getCreatedBy() != null && !profileOwner.equalsIgnoreCase(category.getCreatedBy())) {
            throw new AccessDeniedException("Unauthorized access to profile-specific category.");
        }
        if (category.getCreatedBy() == null || category.getCreatedBy().isBlank()) {
            throw new AccessDeniedException("Unauthorized access to legacy unowned category.");
        }
    }

    private CategoryScope resolveReadScope(String requestedScope, UUID branchId) {
        UUID currentOrgId = TenantContext.getCurrentOrg();
        String scope = normalizeScope(requestedScope, branchId, currentOrgId);
        if ("ALL".equals(scope)) {
            if (canManageAllScopes()) {
                return new CategoryScope("ALL", null, true);
            }
            if (currentOrgId != null) {
                return new CategoryScope("BRANCH", currentOrgId, false);
            }
            throw new AccessDeniedException("A branch must be selected before viewing expense categories.");
        }
        if ("GLOBAL".equals(scope)) {
            if (!canManageAllScopes()) {
                throw new AccessDeniedException("Only organization admins can view organization-level expense categories.");
            }
            return new CategoryScope("GLOBAL", null, false);
        }
        UUID targetOrgId = branchId != null ? branchId : currentOrgId;
        if (targetOrgId == null) {
            throw new AccessDeniedException("A branch must be selected before viewing branch expense categories.");
        }
        if (!canManageAllScopes() && !targetOrgId.equals(currentOrgId)) {
            throw new AccessDeniedException("Unauthorized access to another branch's expense categories.");
        }
        return new CategoryScope("BRANCH", targetOrgId, false);
    }

    private CategoryScope resolveWriteScope(String requestedScope, UUID branchId) {
        UUID currentOrgId = TenantContext.getCurrentOrg();
        String scope = normalizeScope(requestedScope, branchId, currentOrgId);
        if ("ALL".equals(scope)) {
            throw new AccessDeniedException("Select Organization or a branch before creating an expense category.");
        }
        if ("GLOBAL".equals(scope)) {
            if (!canManageAllScopes()) {
                throw new AccessDeniedException("Only organization admins can create organization-level expense categories.");
            }
            return new CategoryScope("GLOBAL", null, false);
        }
        UUID targetOrgId = branchId != null ? branchId : currentOrgId;
        if (targetOrgId == null) {
            throw new AccessDeniedException("A branch must be selected before creating an expense category.");
        }
        if (!canManageAllScopes() && !targetOrgId.equals(currentOrgId)) {
            throw new AccessDeniedException("Unauthorized access to another branch's expense categories.");
        }
        return new CategoryScope("BRANCH", targetOrgId, false);
    }

    private String normalizeScope(String requestedScope, UUID branchId, UUID currentOrgId) {
        if (requestedScope != null && !requestedScope.isBlank()) {
            String scope = requestedScope.trim().toUpperCase();
            return switch (scope) {
                case "ALL", "GLOBAL", "BRANCH" -> scope;
                default -> throw new AccessDeniedException("Invalid expense category scope.");
            };
        }
        if (branchId != null || currentOrgId != null) {
            return "BRANCH";
        }
        return canManageAllScopes() ? "ALL" : "BRANCH";
    }

    private boolean canManageAllScopes() {
        return SecurityUtils.isSuperAdmin() || SecurityUtils.hasRole("ADMIN");
    }

    private String currentProfileOwner() {
        String owner = SecurityUtils.getCurrentUserEmail();
        if (owner == null || owner.isBlank()) {
            throw new AccessDeniedException("Authenticated profile is required for expense categories.");
        }
        return owner.trim();
    }

    private record CategoryScope(String scope, UUID orgId, boolean all) {
    }
}
