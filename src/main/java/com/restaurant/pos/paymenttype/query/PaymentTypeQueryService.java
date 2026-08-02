package com.restaurant.pos.paymenttype.query;

import com.restaurant.pos.common.service.BranchContextService;
import com.restaurant.pos.common.tenant.TenantContext;
import com.restaurant.pos.paymenttype.domain.PaymentType;
import com.restaurant.pos.paymenttype.repository.PaymentTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

/**
 * CQRS Query Service — handles all read-side operations for PaymentType.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentTypeQueryService {

    private final PaymentTypeRepository paymentTypeRepository;
    private final BranchContextService branchContext;

    /**
     * Returns all payment types for the current branch.
     * Falls back to client-wide list when no orgId is resolved.
     */
    @Transactional(readOnly = true)
    public List<PaymentType> getPaymentTypes(UUID orgIdOverride) {
        UUID tenantId = TenantContext.getCurrentTenant();
        UUID orgId = branchContext.getReadOrgId(orgIdOverride);
        if (orgId == null) {
            return paymentTypeRepository.findByClientIdOrderBySortOrderAscDisplayNameAsc(tenantId);
        }
        return paymentTypeRepository.findByClientIdAndOrgIdOrderBySortOrderAscDisplayNameAsc(tenantId, orgId);
    }

    /** Convenience overload — uses branch from security context. */
    @Transactional(readOnly = true)
    public List<PaymentType> getPaymentTypes() {
        return getPaymentTypes(null);
    }

    /**
     * Returns payment types filtered by applicable context: SALES, PURCHASES, or EXPENSES.
     * Falls back to all payment types when context is blank or unrecognised.
     */
    @Transactional(readOnly = true)
    public List<PaymentType> getPaymentTypesByApplicableFor(String context, UUID orgIdOverride) {
        UUID tenantId = TenantContext.getCurrentTenant();
        UUID orgId = branchContext.getReadOrgId(orgIdOverride);

        if (!StringUtils.hasText(context)) {
            return getPaymentTypes(orgIdOverride);
        }

        String upper = context.trim().toUpperCase();

        if (orgId == null) {
            return paymentTypeRepository.findByClientIdOrderBySortOrderAscDisplayNameAsc(tenantId)
                    .stream()
                    .filter(pt -> {
                        if ("SALES".equals(upper))     return "Y".equals(pt.getSales());
                        if ("PURCHASES".equals(upper)) return "Y".equals(pt.getPurchase());
                        if ("EXPENSES".equals(upper))  return "Y".equals(pt.getExpense());
                        return true;
                    })
                    .toList();
        }

        if ("SALES".equals(upper)) {
            return paymentTypeRepository.findByClientIdAndOrgIdAndSalesOrderBySortOrderAsc(tenantId, orgId, "Y");
        }
        if ("PURCHASES".equals(upper)) {
            return paymentTypeRepository.findByClientIdAndOrgIdAndPurchaseOrderBySortOrderAsc(tenantId, orgId, "Y");
        }
        if ("EXPENSES".equals(upper)) {
            return paymentTypeRepository.findByClientIdAndOrgIdAndExpenseOrderBySortOrderAsc(tenantId, orgId, "Y");
        }

        return getPaymentTypes(orgIdOverride);
    }
}