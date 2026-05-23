package com.restaurant.pos.invoice.service;

import com.restaurant.pos.common.exception.ResourceNotFoundException;
import com.restaurant.pos.common.tenant.TenantContext;
import com.restaurant.pos.invoice.domain.Invoice;
import com.restaurant.pos.invoice.domain.InvoiceLine;
import com.restaurant.pos.invoice.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.restaurant.pos.common.util.SecurityUtils;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;

    public Invoice getInvoice(UUID id) {
        UUID tenantId = TenantContext.getCurrentTenant();
        if (SecurityUtils.isSuperAdmin()) {
            return invoiceRepository.findByIdAndClientId(id, tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Invoice not found or access denied"));
        }
        return invoiceRepository.findByIdAndClientIdAndOrgId(id, tenantId, TenantContext.getCurrentOrg())
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found or access denied"));
    }

    public Invoice getInvoiceByOrder(UUID orderId) {
        UUID tenantId = TenantContext.getCurrentTenant();
        if (SecurityUtils.isSuperAdmin()) {
            return invoiceRepository.findByOrderIdAndClientId(orderId, tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Invoice not found for order or access denied"));
        }
        return invoiceRepository.findByOrderIdAndClientIdAndOrgId(orderId, tenantId, TenantContext.getCurrentOrg())
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found for order or access denied"));
    }

    @Transactional
    public Invoice createInvoice(Invoice invoice) {
        if (invoice.getClientId() == null) {
            invoice.setClientId(TenantContext.getCurrentTenant());
        }
        if (invoice.getOrgId() == null && !SecurityUtils.isSuperAdmin()) {
            invoice.setOrgId(TenantContext.getCurrentOrg());
        }

        // Prevent duplicate creation from offline sync
        if (invoice.getInvoiceNo() != null) {
             Optional<Invoice> existing;
             if (SecurityUtils.isSuperAdmin()) {
                 existing = invoiceRepository.findByInvoiceNoAndClientId(
                     invoice.getInvoiceNo(), invoice.getClientId()
                 );
             } else {
                 existing = invoiceRepository.findByInvoiceNoAndClientIdAndOrgId(
                     invoice.getInvoiceNo(), invoice.getClientId(), invoice.getOrgId()
                 );
             }
             
             if (existing.isPresent()) {
                 return existing.get();
             }
        }

        // Safeguard: Sync bidirectional parent-child relationships for lines
        if (invoice.getLines() != null) {
            for (InvoiceLine line : invoice.getLines()) {
                line.setInvoice(invoice);
            }
        }

        return invoiceRepository.save(invoice);
    }
}
