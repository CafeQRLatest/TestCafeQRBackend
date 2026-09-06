package com.restaurant.pos.hr.repository;

import com.restaurant.pos.hr.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, UUID> {
    
    @Query("SELECT e FROM Employee e WHERE e.clientId = :clientId AND e.orgId = :orgId")
    List<Employee> findByClientIdAndOrgId(UUID clientId, UUID orgId);

    @Query("SELECT e FROM Employee e WHERE e.id = :id AND e.clientId = :clientId AND e.orgId = :orgId")
    Optional<Employee> findByIdAndClientIdAndOrgId(UUID id, UUID clientId, UUID orgId);

    @Query("SELECT e FROM Employee e WHERE e.userId = :userId AND e.clientId = :clientId AND e.orgId = :orgId")
    Optional<Employee> findByUserIdAndClientIdAndOrgId(UUID userId, UUID clientId, UUID orgId);
}
