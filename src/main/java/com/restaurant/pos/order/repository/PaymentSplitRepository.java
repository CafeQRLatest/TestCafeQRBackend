package com.restaurant.pos.order.repository;

import com.restaurant.pos.order.domain.PaymentSplit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PaymentSplitRepository extends JpaRepository<PaymentSplit, UUID> {

    List<PaymentSplit> findByPaymentIdOrderByCreatedAtAsc(UUID paymentId);

    List<PaymentSplit> findByPaymentIdInOrderByCreatedAtAsc(Collection<UUID> paymentIds);
}
