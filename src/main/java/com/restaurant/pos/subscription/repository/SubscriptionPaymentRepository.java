package com.restaurant.pos.subscription.repository;

import com.restaurant.pos.subscription.domain.SubscriptionPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionPaymentRepository extends JpaRepository<SubscriptionPayment, UUID> {
    Optional<SubscriptionPayment> findByPaymentId(String paymentId);
}
