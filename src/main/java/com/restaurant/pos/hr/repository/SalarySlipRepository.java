package com.restaurant.pos.hr.repository;

import com.restaurant.pos.hr.entity.SalarySlip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SalarySlipRepository extends JpaRepository<SalarySlip, UUID> {
    
    @Query("SELECT s FROM SalarySlip s WHERE s.clientId = :clientId AND s.orgId = :orgId")
    List<SalarySlip> findByClientIdAndOrgId(UUID clientId, UUID orgId);

    @Query("SELECT s FROM SalarySlip s WHERE s.id = :id AND s.clientId = :clientId AND s.orgId = :orgId")
    Optional<SalarySlip> findByIdAndClientIdAndOrgId(UUID id, UUID clientId, UUID orgId);
    
    @Query("SELECT s FROM SalarySlip s WHERE s.payrollRun.id = :payrollRunId AND s.clientId = :clientId AND s.orgId = :orgId")
    List<SalarySlip> findByPayrollRunIdAndClientIdAndOrgId(UUID payrollRunId, UUID clientId, UUID orgId);
}
