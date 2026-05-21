package com.restaurant.pos.invoice.repository;

import com.restaurant.pos.invoice.domain.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.time.LocalDateTime;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID>, JpaSpecificationExecutor<Invoice> {
    Optional<Invoice> findByOrderIdAndClientId(UUID orderId, UUID clientId);
    Optional<Invoice> findByOrderIdAndClientIdAndOrgId(UUID orderId, UUID clientId, UUID orgId);
    List<Invoice> findByOrderId(UUID orderId);
    
    Optional<Invoice> findByIdAndClientId(UUID id, UUID clientId);
    Optional<Invoice> findByIdAndClientIdAndOrgId(UUID id, UUID clientId, UUID orgId);
    List<Invoice> findByExpenseId(UUID expenseId);
    
    Optional<Invoice> findByInvoiceNoAndClientId(String invoiceNo, UUID clientId);
    Optional<Invoice> findByInvoiceNoAndClientIdAndOrgId(String invoiceNo, UUID clientId, UUID orgId);

    List<Invoice> findByClientIdAndOrgIdAndInvoiceDateBetweenOrderByInvoiceDateAsc(UUID clientId, UUID orgId, LocalDateTime from, LocalDateTime to);

    boolean existsByClientIdAndOrgIdAndInvoiceNo(UUID clientId, UUID orgId, String invoiceNo);
    
    long countByClientId(UUID clientId);
}
