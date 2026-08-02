package com.restaurant.pos.paymenttype.command;

import com.restaurant.pos.common.exception.ResourceNotFoundException;
import com.restaurant.pos.common.service.BranchContextService;
import com.restaurant.pos.common.tenant.TenantContext;
import com.restaurant.pos.paymenttype.domain.PaymentType;
import com.restaurant.pos.paymenttype.repository.PaymentTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * CQRS Command Service — handles all write-side operations for PaymentType.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentTypeCommandService {

    private final PaymentTypeRepository paymentTypeRepository;
    private final BranchContextService branchContext;

    @Transactional
    public PaymentType createPaymentType(PaymentTypeCommand command) {
        UUID tenantId = TenantContext.getCurrentTenant();
        UUID orgId = branchContext.requireWriteOrgId(null);

        if (command.getSortOrder() != null) {
            if (paymentTypeRepository.existsByClientIdAndOrgIdAndSortOrder(tenantId, orgId, command.getSortOrder())) {
                throw new IllegalArgumentException(
                    "Sort order " + command.getSortOrder() + " is already assigned to another payment type");
            }
        }

        PaymentType paymentType = PaymentType.builder()
                .clientId(tenantId)
                .orgId(orgId)
                .displayName(command.getDisplayName())
                .paymentType(command.getPaymentType() != null ? command.getPaymentType() : "OTHERS")
                .sales(command.getSales() != null ? command.getSales() : "Y")
                .purchase(command.getPurchase() != null ? command.getPurchase() : "Y")
                .expense(command.getExpense() != null ? command.getExpense() : "Y")
                .ledgerRef(command.getLedgerRef())
                .isDefault(command.getIsDefault())
                .sortOrder(command.getSortOrder() != null ? command.getSortOrder() : 0)
                .description(command.getDescription())
                .isactive(command.getIsactive() != null ? command.getIsactive() : "Y")
                .build();

        if (Boolean.TRUE.equals(command.getIsDefault())) {
            clearDefaultPaymentTypes(tenantId, orgId);
        }

        PaymentType saved = paymentTypeRepository.save(paymentType);
        log.info("Created payment type id={} name={} orgId={}", saved.getId(), saved.getDisplayName(), orgId);
        return saved;
    }

    @Transactional
    public PaymentType updatePaymentType(UUID id, PaymentTypeCommand command) {
        UUID tenantId = TenantContext.getCurrentTenant();
        PaymentType existing = paymentTypeRepository.findByIdAndClientId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment type not found for ID: " + id));
        branchContext.requireWriteOrgId(existing.getOrgId());

        if (command.getSortOrder() != null) {
            boolean sortExists = paymentTypeRepository.existsByClientIdAndOrgIdAndSortOrderAndIdNot(
                    tenantId, existing.getOrgId(), command.getSortOrder(), id);
            if (sortExists) {
                throw new IllegalArgumentException(
                    "Sort order " + command.getSortOrder() + " is already assigned to another payment type");
            }
        }

        existing.setDisplayName(command.getDisplayName());
        existing.setPaymentType(command.getPaymentType());
        existing.setSales(command.getSales());
        existing.setPurchase(command.getPurchase());
        existing.setExpense(command.getExpense());
        existing.setLedgerRef(command.getLedgerRef());
        existing.setSortOrder(command.getSortOrder());
        existing.setDescription(command.getDescription());

        if (Boolean.TRUE.equals(command.getIsDefault())) {
            clearDefaultPaymentTypes(tenantId, existing.getOrgId());
        }
        existing.setIsDefault(command.getIsDefault());
        if (command.getIsactive() != null) {
            existing.setIsactive(command.getIsactive());
        }

        PaymentType saved = paymentTypeRepository.save(existing);
        log.info("Updated payment type id={} orgId={}", id, existing.getOrgId());
        return saved;
    }

    @Transactional
    public void deletePaymentType(UUID id) {
        UUID tenantId = TenantContext.getCurrentTenant();
        PaymentType existing = paymentTypeRepository.findByIdAndClientId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment type not found for ID: " + id));
        branchContext.requireWriteOrgId(existing.getOrgId());
        paymentTypeRepository.delete(existing);
        log.info("Deleted payment type id={}", id);
    }

    // ─────────────────────────────────────────────────────────────────────────
    private void clearDefaultPaymentTypes(UUID clientId, UUID orgId) {
        List<PaymentType> defaults = paymentTypeRepository.findByClientIdAndIsDefaultTrueAndOrgId(clientId, orgId);
        defaults.forEach(pt -> pt.setIsDefault(false));
        paymentTypeRepository.saveAll(defaults);
    }
}