package com.restaurant.pos.hr.repository;

import com.restaurant.pos.hr.entity.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, UUID> {
    
    @Query("SELECT a FROM Attendance a WHERE a.clientId = :clientId AND a.orgId = :orgId")
    List<Attendance> findByClientIdAndOrgId(UUID clientId, UUID orgId);

    @Query("SELECT a FROM Attendance a WHERE a.id = :id AND a.clientId = :clientId AND a.orgId = :orgId")
    Optional<Attendance> findByIdAndClientIdAndOrgId(UUID id, UUID clientId, UUID orgId);

    @Query("SELECT a FROM Attendance a WHERE a.employee.id = :employeeId AND a.attendanceDate = :date AND a.clientId = :clientId AND a.orgId = :orgId")
    Optional<Attendance> findByEmployeeIdAndDateAndClientIdAndOrgId(UUID employeeId, LocalDate date, UUID clientId, UUID orgId);
    
    @Query("SELECT a FROM Attendance a WHERE a.employee.id = :employeeId AND a.attendanceDate BETWEEN :startDate AND :endDate AND a.clientId = :clientId AND a.orgId = :orgId")
    List<Attendance> findByEmployeeIdAndDateRangeAndClientIdAndOrgId(UUID employeeId, LocalDate startDate, LocalDate endDate, UUID clientId, UUID orgId);
}
