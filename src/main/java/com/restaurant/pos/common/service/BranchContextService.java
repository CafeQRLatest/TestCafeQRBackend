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
 *   <li><b>READ</b> operations use {@link #getReadOrgId(UUID)} — returns the selected
 *       branch when one is active; superadmin reads require a selected branch.</li>
 *   <li><b>WRITE</b> operations use {@link #requireWriteOrgId(UUID)} — always requires a
 *       branch context; throws {@link BusinessException} if none is available.</li>
 * </ul>
 */
@Service
public class BranchContextService {

    /**
     * For READ operations: returns the effective org ID to filter data by.
     * <ul>
     *   <li>Use the active org from tenant context; an explicit filter must match it.</li>
     *   <li>If the header selected a real branch, returns the current org from tenant context.</li>
     *   <li>A superadmin with no active org is rejected.</li>
     * </ul>
     *
     * @param filterOrgId optional org ID filter that must match the active branch
     * @return the org ID to filter by; a missing superadmin branch is rejected
     */
    public UUID getReadOrgId(@Nullable UUID filterOrgId) {
        UUID selectedOrgId = TenantContext.getCurrentOrg();
        if (selectedOrgId != null) {
            if (filterOrgId != null && !selectedOrgId.equals(filterOrgId)) {
                throw new BusinessException("The requested branch does not match the active branch.");
            }
            return selectedOrgId;
        }
        if (SecurityUtils.isSuperAdmin()) {
            throw new BusinessException("A branch must be selected before reading branch data.");
        }
        return null;
    }

    /**
     * For WRITE operations: always requires a branch context.
     * <p>
     * Resolution order:
     * <ol>
     *   <li>Use the current org from tenant context when a branch is selected.</li>
     *   <li>If the request org differs from the active branch, reject the write.</li>
     *   <li>If still {@code null}, throw a {@link BusinessException}.</li>
     * </ol>
     *
     * @param explicitOrgId optional org ID from the request payload
     * @return a non-null org ID
     * @throws BusinessException if no branch context is available
     */
    public UUID requireWriteOrgId(@Nullable UUID explicitOrgId) {
        UUID selectedOrgId = TenantContext.getCurrentOrg();
        if (selectedOrgId != null) {
            if (explicitOrgId != null && !selectedOrgId.equals(explicitOrgId)) {
                throw new BusinessException("The requested branch does not match the active branch.");
            }
            return selectedOrgId;
        }
        throw new BusinessException(
            "A branch must be selected before performing this operation. " +
            "Use the branch picker in the header to select an active branch."
        );
    }
}
