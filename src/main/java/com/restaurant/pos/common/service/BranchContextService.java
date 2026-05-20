package com.restaurant.pos.common.service;

import com.restaurant.pos.common.exception.BusinessException;
import com.restaurant.pos.common.tenant.TenantContext;
import com.restaurant.pos.common.util.SecurityUtils;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Centralizes branch (org) context resolution for multi-branch operations.
 *
 * <h3>Design Contract:</h3>
 * <ul>
 *   <li><b>READ</b> operations use {@link #getReadOrgId(UUID)} — returns {@code null} for
 *       Super Admins (meaning "all branches"), unless a filter override is given.</li>
 *   <li><b>WRITE</b> operations use {@link #requireWriteOrgId(UUID)} — always requires a
 *       branch context; throws {@link BusinessException} if none is available.</li>
 * </ul>
 */
@Service
public class BranchContextService {

    /**
     * For READ operations: returns the effective org ID to filter data by.
     * <ul>
     *   <li>If {@code filterOrgId} is provided (e.g. from {@code ?orgId=} query param), use it.</li>
     *   <li>If the current user is a Super Admin, returns {@code null} (all branches).</li>
     *   <li>Otherwise, returns the current org from tenant context.</li>
     * </ul>
     *
     * @param filterOrgId optional org ID override from a query parameter
     * @return the org ID to filter by, or {@code null} for "all branches"
     */
    public UUID getReadOrgId(@Nullable UUID filterOrgId) {
        if (filterOrgId != null) {
            return filterOrgId;
        }
        if (SecurityUtils.isSuperAdmin()) {
            return null; // All branches
        }
        return TenantContext.getCurrentOrg();
    }

    /**
     * For WRITE operations: always requires a branch context.
     * <p>
     * Resolution order:
     * <ol>
     *   <li>Use {@code explicitOrgId} if provided (e.g. from the request body).</li>
     *   <li>Fall back to the current org from tenant context.</li>
     *   <li>If still {@code null}, throw a {@link BusinessException}.</li>
     * </ol>
     *
     * @param explicitOrgId optional org ID from the request payload
     * @return a non-null org ID
     * @throws BusinessException if no branch context is available
     */
    public UUID requireWriteOrgId(@Nullable UUID explicitOrgId) {
        UUID orgId = explicitOrgId != null ? explicitOrgId : TenantContext.getCurrentOrg();
        if (orgId == null) {
            throw new BusinessException(
                "A branch must be selected before performing this operation. " +
                "Use the branch picker in the header to select an active branch."
            );
        }
        return orgId;
    }
}
