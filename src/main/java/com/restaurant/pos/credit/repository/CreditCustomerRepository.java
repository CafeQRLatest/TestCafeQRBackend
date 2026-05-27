package com.restaurant.pos.credit.repository;

import com.restaurant.pos.credit.domain.CreditCustomer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CreditCustomerRepository extends JpaRepository<CreditCustomer, UUID>, JpaSpecificationExecutor<CreditCustomer> {
    List<CreditCustomer> findByClientIdAndIsactiveOrderByNameAsc(UUID clientId, String isactive);
    List<CreditCustomer> findByClientIdAndStatusAndIsactiveOrderByNameAsc(UUID clientId, String status, String isactive);
    Optional<CreditCustomer> findByIdAndClientId(UUID id, UUID clientId);
    Optional<CreditCustomer> findByClientIdAndLinkedCustomerIdAndIsactive(UUID clientId, UUID linkedCustomerId, String isactive);
    Optional<CreditCustomer> findFirstByClientIdAndPhoneAndIsactiveOrderByCreatedAtAsc(UUID clientId, String phone, String isactive);
    boolean existsByClientIdAndPhoneAndIsactive(UUID clientId, String phone, String isactive);
    boolean existsByClientIdAndPhoneAndIsactiveAndIdNot(UUID clientId, String phone, String isactive, UUID id);
}
