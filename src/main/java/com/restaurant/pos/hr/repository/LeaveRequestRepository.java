package com.restaurant.pos.hr.repository;

import com.restaurant.pos.hr.entity.LeaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, UUID> {
    
    @Query("SELECT l FROM LeaveRequest l WHERE l.clientId = :clientId AND l.orgId = :orgId")
    List<LeaveRequest> findByClientIdAndOrgId(UUID clientId, UUID orgId);

    @Query("SELECT l FROM LeaveRequest l WHERE l.id = :id AND l.clientId = :clientId AND l.orgId = :orgId")
    Optional<LeaveRequest> findByIdAndClientIdAndOrgId(UUID id, UUID clientId, UUID orgId);

    @Query("SELECT l FROM LeaveRequest l WHERE l.employee.id = :employeeId AND l.clientId = :clientId AND l.orgId = :orgId")
    List<LeaveRequest> findByEmployeeIdAndClientIdAndOrgId(UUID employeeId, UUID clientId, UUID orgId);
    
    @Query("SELECT l FROM LeaveRequest l WHERE l.employee.id = :employeeId AND l.status = 'APPROVED' AND l.startDate <= :endDate AND l.endDate >= :startDate AND l.clientId = :clientId AND l.orgId = :orgId")
    List<LeaveRequest> findApprovedByEmployeeIdAndDateRange(UUID employeeId, LocalDate startDate, LocalDate endDate, UUID clientId, UUID orgId);
}
