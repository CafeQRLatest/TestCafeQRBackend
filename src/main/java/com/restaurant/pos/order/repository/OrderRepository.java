package com.restaurant.pos.order.repository;

import com.restaurant.pos.order.domain.Order;
import com.restaurant.pos.order.domain.OrderType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
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
    @Query("""
            SELECT o FROM Order o
            WHERE o.clientId = :clientId
              AND (:orgId IS NULL OR o.orgId = :orgId)
              AND o.orderType = :orderType
              AND o.isactive = 'Y'
              AND (o.orderStatus IS NULL OR UPPER(o.orderStatus) NOT IN :closedStatuses)
            ORDER BY o.orderDate DESC, o.createdAt DESC
            """)
    List<Order> findLiveOrders(UUID clientId, UUID orgId, OrderType orderType, Collection<String> closedStatuses);

    @EntityGraph(attributePaths = "lines")
    @Query("""
            SELECT o FROM Order o
            WHERE o.clientId = :clientId
              AND (:orgId IS NULL OR o.orgId = :orgId)
              AND o.orderType = :orderType
              AND o.isactive = 'Y'
              AND o.updatedAt >= :updatedAfter
            ORDER BY o.updatedAt DESC, o.orderDate DESC
            """)
    List<Order> findChangedOrders(UUID clientId, UUID orgId, OrderType orderType, LocalDateTime updatedAfter, Pageable pageable);

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
