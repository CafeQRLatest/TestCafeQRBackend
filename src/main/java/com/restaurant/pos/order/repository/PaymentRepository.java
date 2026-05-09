package com.restaurant.pos.order.repository;

import com.restaurant.pos.order.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    long countByClientId(UUID clientId);
    List<com.restaurant.pos.order.domain.Payment> findByOrderId(UUID orderId);
    boolean existsByClientIdAndOrgIdAndReferenceNo(UUID clientId, UUID orgId, String referenceNo);
}
