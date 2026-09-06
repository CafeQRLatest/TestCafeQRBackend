package com.restaurant.pos.hr.service;

import com.restaurant.pos.common.tenant.TenantContext;
import com.restaurant.pos.hr.dto.AttendanceDto;
import com.restaurant.pos.hr.entity.Attendance;
import com.restaurant.pos.hr.entity.Employee;
import com.restaurant.pos.hr.repository.AttendanceRepository;
import com.restaurant.pos.hr.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;

    private static final BigDecimal STANDARD_HOURS_PER_DAY = new BigDecimal("8.00");

    @Transactional
    public AttendanceDto clockIn(UUID employeeId, String punchMethod) {
        UUID clientId = TenantContext.getCurrentTenant();
        UUID orgId = TenantContext.getCurrentOrg();
        
        Employee employee = employeeRepository.findByIdAndClientIdAndOrgId(employeeId, clientId, orgId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        LocalDate today = LocalDate.now();
        
        // Check if already clocked in today
        attendanceRepository.findByEmployeeIdAndDateAndClientIdAndOrgId(employeeId, today, clientId, orgId)
                .ifPresent(a -> {
                    throw new RuntimeException("Employee already clocked in today");
                });

        Attendance attendance = new Attendance();
        attendance.setEmployee(employee);
        attendance.setAttendanceDate(today);
        attendance.setClockInTime(LocalDateTime.now());
        attendance.setPunchMethod(punchMethod);
        attendance.setStatus("PRESENT");
        
        Attendance saved = attendanceRepository.save(attendance);
        return mapToDto(saved);
    }

    @Transactional
    public AttendanceDto clockOut(UUID employeeId) {
        UUID clientId = TenantContext.getCurrentTenant();
        UUID orgId = TenantContext.getCurrentOrg();
        LocalDate today = LocalDate.now();
        
        Attendance attendance = attendanceRepository.findByEmployeeIdAndDateAndClientIdAndOrgId(employeeId, today, clientId, orgId)
                .orElseThrow(() -> new RuntimeException("No clock-in record found for today"));

        if (attendance.getClockOutTime() != null) {
            throw new RuntimeException("Employee already clocked out today");
        }

        attendance.setClockOutTime(LocalDateTime.now());
        
        // Calculate hours and overtime using math similar to payroll-ddd
        calculateHours(attendance);
        
        Attendance saved = attendanceRepository.save(attendance);
        return mapToDto(saved);
    }

    private void calculateHours(Attendance attendance) {
        if (attendance.getClockInTime() != null && attendance.getClockOutTime() != null) {
            Duration duration = Duration.between(attendance.getClockInTime(), attendance.getClockOutTime());
            double hours = duration.toMinutes() / 60.0;
            BigDecimal totalHours = BigDecimal.valueOf(hours).setScale(2, RoundingMode.HALF_UP);
            
            attendance.setTotalHoursWorked(totalHours);
            
            if (totalHours.compareTo(STANDARD_HOURS_PER_DAY) > 0) {
                attendance.setOvertimeHours(totalHours.subtract(STANDARD_HOURS_PER_DAY));
            } else {
                attendance.setOvertimeHours(BigDecimal.ZERO);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<AttendanceDto> getAttendanceByEmployeeAndDateRange(UUID employeeId, LocalDate startDate, LocalDate endDate) {
        UUID clientId = TenantContext.getCurrentTenant();
        UUID orgId = TenantContext.getCurrentOrg();
        
        return attendanceRepository.findByEmployeeIdAndDateRangeAndClientIdAndOrgId(employeeId, startDate, endDate, clientId, orgId)
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    private AttendanceDto mapToDto(Attendance entity) {
        return AttendanceDto.builder()
                .id(entity.getId())
                .employeeId(entity.getEmployee().getId())
                .employeeName(entity.getEmployee().getFirstName() + " " + entity.getEmployee().getLastName())
                .attendanceDate(entity.getAttendanceDate())
                .clockInTime(entity.getClockInTime())
                .clockOutTime(entity.getClockOutTime())
                .totalHoursWorked(entity.getTotalHoursWorked())
                .overtimeHours(entity.getOvertimeHours())
                .status(entity.getStatus())
                .punchMethod(entity.getPunchMethod())
                .build();
    }
}
