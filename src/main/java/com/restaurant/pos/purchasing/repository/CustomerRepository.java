package com.restaurant.pos.purchasing.repository;

import com.restaurant.pos.purchasing.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    List<Customer> findByClientIdOrderByNameAsc(UUID clientId);
    List<Customer> findByClientIdAndOrgIdOrderByNameAsc(UUID clientId, UUID orgId);
    Optional<Customer> findByIdAndClientId(UUID id, UUID clientId);
    Optional<Customer> findByIdAndClientIdAndOrgId(UUID id, UUID clientId, UUID orgId);
    Optional<Customer> findByPhoneAndClientIdAndOrgId(String phone, UUID clientId, UUID orgId);
    Optional<Customer> findByPhoneAndClientId(String phone, UUID clientId);
    Optional<Customer> findFirstByPhoneAndClientIdOrderByCreatedAtAsc(String phone, UUID clientId);
    boolean existsByClientIdAndPhone(UUID clientId, String phone);
    boolean existsByClientIdAndPhoneAndIdNot(UUID clientId, String phone, UUID id);
    Optional<Customer> findByEmailAndClientId(String email, UUID clientId);

    @Query(value = """
            SELECT *
            FROM customers c
            WHERE c.client_id = :clientId
              AND c.order_links @> CAST(:orderNeedle AS jsonb)
            ORDER BY
              CASE WHEN c.order_links @> CAST(:primaryNeedle AS jsonb) THEN 0 ELSE 1 END,
              c.name ASC
            """, nativeQuery = true)
    List<Customer> findByClientIdAndOrderLink(
            @Param("clientId") UUID clientId,
            @Param("orderNeedle") String orderNeedle,
            @Param("primaryNeedle") String primaryNeedle);
}
