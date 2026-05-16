package com.restaurant.pos.order.repository;

import com.restaurant.pos.order.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    long countByClientId(UUID clientId);
    List<com.restaurant.pos.order.domain.Payment> findByOrderId(UUID orderId);
    List<Payment> findByClientIdAndOrgIdAndPaymentDateBetweenOrderByPaymentDateAsc(UUID clientId, UUID orgId, LocalDateTime from, LocalDateTime to);
    @Query("""
            SELECT p FROM Payment p
            WHERE p.clientId = :clientId
              AND (:orgId IS NULL OR p.orgId = :orgId)
              AND p.paymentDate BETWEEN :from AND :to
              AND p.isactive = 'Y'
              AND UPPER(COALESCE(p.status, 'COMPLETED')) NOT IN ('VOID', 'VOIDED')
              AND UPPER(COALESCE(p.docStatus, 'COMPLETED')) NOT IN ('VOID', 'VOIDED')
            ORDER BY p.paymentDate ASC
            """)
    List<Payment> findActivePaymentsInPeriod(
            @Param("clientId") UUID clientId,
            @Param("orgId") UUID orgId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
    boolean existsByClientIdAndOrgIdAndReferenceNo(UUID clientId, UUID orgId, String referenceNo);
}
