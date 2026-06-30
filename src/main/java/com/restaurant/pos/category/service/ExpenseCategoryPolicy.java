package com.restaurant.pos.category.service;

import com.restaurant.pos.category.domain.ExpenseCategory;
import com.restaurant.pos.common.context.ContextProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import com.restaurant.pos.expense.domain.ScopeType;

import java.util.UUID;

/**
 * Pure, stateless policy component defining read/write scopes and authorization
 * rules for Expense Categories. Devoid of static ThreadLocal dependencies.
 */
@Component
public class ExpenseCategoryPolicy {

    public CategoryScope resolveReadScope(String requestedScope, UUID branchId, ContextProvider context) {
        UUID currentOrgId = context.getCurrentOrg();
        boolean canManageAll = context.isSuperAdmin() || context.hasRole("ADMIN");
        String scope = normalizeScope(requestedScope, branchId, currentOrgId, canManageAll);
        
        if ("ALL".equals(scope)) {
            if (canManageAll) {
                return new CategoryScope("ALL", null, true);
            }
            if (currentOrgId != null) {
                return new CategoryScope("BRANCH", currentOrgId, false);
            }
            throw new AccessDeniedException("A branch must be selected before viewing expense categories.");
        }
        if ("GLOBAL".equals(scope)) {
            if (!canManageAll) {
                throw new AccessDeniedException("Only organization admins can view organization-level expense categories.");
            }
            return new CategoryScope("GLOBAL", null, false);
        }
        UUID targetOrgId = branchId != null ? branchId : currentOrgId;
        if (targetOrgId == null) {
            throw new AccessDeniedException("A branch must be selected before viewing branch expense categories.");
        }
        if (!canManageAll && !targetOrgId.equals(currentOrgId)) {
            throw new AccessDeniedException("Unauthorized access to another branch's expense categories.");
        }
        return new CategoryScope("BRANCH", targetOrgId, false);
    }

    public CategoryScope resolveWriteScope(String requestedScope, UUID branchId, ContextProvider context) {
        UUID currentOrgId = context.getCurrentOrg();
        boolean canManageAll = context.isSuperAdmin() || context.hasRole("ADMIN");
        String scope = normalizeScope(requestedScope, branchId, currentOrgId, canManageAll);

        if ("ALL".equals(scope)) {
            throw new AccessDeniedException("Select Organization or a branch before creating an expense category.");
        }
        if ("GLOBAL".equals(scope)) {
            if (!canManageAll) {
                throw new AccessDeniedException("Only organization admins can create organization-level expense categories.");
            }
            return new CategoryScope("GLOBAL", null, false);
        }
        UUID targetOrgId = branchId != null ? branchId : currentOrgId;
        if (targetOrgId == null) {
            throw new AccessDeniedException("A branch must be selected before creating an expense category.");
        }
        if (!canManageAll && !targetOrgId.equals(currentOrgId)) {
            throw new AccessDeniedException("Unauthorized access to another branch's expense categories.");
        }
        return new CategoryScope("BRANCH", targetOrgId, false);
    }

    public void validateOwnership(ExpenseCategory category, ContextProvider context) {
        UUID clientId = context.getCurrentTenant();
        UUID orgId = context.getCurrentOrg();
        boolean canManageAll = context.isSuperAdmin() || context.hasRole("ADMIN");

        if (clientId != null && category.getClientId() != null && !clientId.equals(category.getClientId())) {
            throw new AccessDeniedException("Unauthorized access to tenant-specific category.");
        }
        if (!canManageAll && orgId != null && !orgId.equals(category.getOrgId())) {
            throw new AccessDeniedException("Unauthorized access to branch-specific category.");
        }
        if (!canManageAll && orgId == null && category.getOrgId() == null) {
            throw new AccessDeniedException("Unauthorized access to organization category.");
        }
    }

    private String normalizeScope(String requestedScope, UUID branchId, UUID currentOrgId, boolean canManageAll) {
        if (requestedScope != null && !requestedScope.isBlank()) {
            ScopeType type = ScopeType.from(requestedScope);
            if (type == null) {
                throw new IllegalArgumentException("Invalid scope: " + requestedScope);
            }
            if (type == ScopeType.ALL) {
                return "ALL";
            }
            if (type == ScopeType.GLOBAL) {
                return "GLOBAL";
            }
            return "BRANCH";
        }
        if (branchId != null || currentOrgId != null) {
            return "BRANCH";
        }
        return canManageAll ? "ALL" : "BRANCH";
    }

    public record CategoryScope(String scope, UUID orgId, boolean all) {}
}
