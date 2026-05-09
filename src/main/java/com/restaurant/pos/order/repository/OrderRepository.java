package com.restaurant.pos.order.repository;

import com.restaurant.pos.order.domain.Order;
import com.restaurant.pos.order.domain.OrderType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {
    @EntityGraph(attributePaths = "lines")
    List<Order> findByClientIdOrderByCreatedAtDesc(UUID clientId);

    @EntityGraph(attributePaths = "lines")
    List<Order> findByClientIdAndOrgIdOrderByCreatedAtDesc(UUID clientId, UUID orgId);

    // Use OrderType enum — Spring Data JPA handles @Enumerated(STRING) automatically
    @EntityGraph(attributePaths = "lines")
    List<Order> findByClientIdAndOrderTypeOrderByCreatedAtDesc(UUID clientId, OrderType orderType);

    @EntityGraph(attributePaths = "lines")
    List<Order> findByClientIdAndOrgIdAndOrderTypeOrderByCreatedAtDesc(UUID clientId, UUID orgId, OrderType orderType);

    @EntityGraph(attributePaths = "lines")
    List<Order> findByClientIdAndOrderStatusInOrderByCreatedAtDesc(UUID clientId, List<String> statuses);

    @EntityGraph(attributePaths = "lines")
    List<Order> findByClientIdAndOrgIdAndOrderStatusInOrderByCreatedAtDesc(UUID clientId, UUID orgId, List<String> statuses);

    @EntityGraph(attributePaths = "lines")
    Optional<Order> findByIdAndClientId(UUID id, UUID clientId);

    @EntityGraph(attributePaths = "lines")
    Optional<Order> findByIdAndClientIdAndOrgId(UUID id, UUID clientId, UUID orgId);

    @EntityGraph(attributePaths = "lines")
    Optional<Order> findByOrderNoAndClientId(String orderNo, UUID clientId);

    boolean existsByClientIdAndOrgIdAndOrderNo(UUID clientId, UUID orgId, String orderNo);

    @EntityGraph(attributePaths = "lines")
    Optional<Order> findBySourceOperationIdAndClientId(String sourceOperationId, UUID clientId);

    long countByClientId(UUID clientId);
}
