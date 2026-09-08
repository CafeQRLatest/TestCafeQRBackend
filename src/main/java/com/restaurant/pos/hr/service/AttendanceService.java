package com.restaurant.pos.hr.service;

import com.restaurant.pos.common.context.TimezoneResolver;
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
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;
    private final TimezoneResolver timezoneResolver;

    private static final BigDecimal STANDARD_HOURS_PER_DAY = new BigDecimal("8.00");

    @Transactional
    public AttendanceDto clockIn(UUID employeeId, String punchMethod) {
        UUID clientId = TenantContext.getCurrentTenant();
        UUID orgId = TenantContext.getCurrentOrg();
        ZoneId zoneId = timezoneResolver.resolveTimezone(clientId, orgId);
        
        Employee employee = employeeRepository.findByIdAndClientIdAndOrgId(employeeId, clientId, orgId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        if (!employee.isActive()) {
            throw new RuntimeException("Inactive employees cannot clock in.");
        }

        LocalDate today = LocalDate.now(zoneId);
        
        // Check if already clocked in today
        attendanceRepository.findByEmployeeIdAndDateAndClientIdAndOrgId(employeeId, today, clientId, orgId)
                .ifPresent(a -> {
                    throw new RuntimeException("Employee already clocked in today");
                });

        Attendance attendance = new Attendance();
        attendance.setEmployee(employee);
        attendance.setAttendanceDate(today);
        attendance.setClockInTime(LocalDateTime.now(zoneId));
        attendance.setPunchMethod(punchMethod);
        attendance.setStatus("PRESENT");
        
        Attendance saved = attendanceRepository.save(attendance);
        return mapToDto(saved);
    }

    @Transactional
    public AttendanceDto clockOut(UUID employeeId) {
        UUID clientId = TenantContext.getCurrentTenant();
        UUID orgId = TenantContext.getCurrentOrg();
        ZoneId zoneId = timezoneResolver.resolveTimezone(clientId, orgId);
        LocalDate today = LocalDate.now(zoneId);
        
        Attendance attendance = attendanceRepository.findByEmployeeIdAndDateAndClientIdAndOrgId(employeeId, today, clientId, orgId)
                .orElseThrow(() -> new RuntimeException("No clock-in record found for today"));

        if (attendance.getClockOutTime() != null) {
            throw new RuntimeException("Employee already clocked out today");
        }

        attendance.setClockOutTime(LocalDateTime.now(zoneId));
        
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
    public List<AttendanceDto> getAllAttendanceRecords(LocalDate startDate, LocalDate endDate) {
        UUID clientId = TenantContext.getCurrentTenant();
        UUID orgId = TenantContext.getCurrentOrg();
        ZoneId zoneId = timezoneResolver.resolveTimezone(clientId, orgId);

        LocalDate start = (startDate != null) ? startDate : LocalDate.now(zoneId).minusDays(30);
        LocalDate end = (endDate != null) ? endDate : LocalDate.now(zoneId);

        return attendanceRepository.findAllByDateRangeAndClientIdAndOrgId(start, end, clientId, orgId)
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
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

    @Transactional
    public AttendanceDto saveManualAttendance(AttendanceDto dto) {
        UUID clientId = TenantContext.getCurrentTenant();
        UUID orgId = TenantContext.getCurrentOrg();
        ZoneId zoneId = timezoneResolver.resolveTimezone(clientId, orgId);

        Attendance attendance;
        if (dto.getId() != null) {
            attendance = attendanceRepository.findByIdAndClientIdAndOrgId(dto.getId(), clientId, orgId)
                    .orElseThrow(() -> new RuntimeException("Attendance record not found"));
        } else {
            // Preventing duplicate active shifts for the same employee
            if (dto.getClockOutTime() == null) {
                List<Attendance> activeShifts = attendanceRepository.findActiveAttendanceByEmployeeIdAndClientIdAndOrgId(dto.getEmployeeId(), clientId, orgId);
                if (!activeShifts.isEmpty()) {
                    throw new RuntimeException("Employee already has an active clock-in without clock-out.");
                }
            }
            attendance = new Attendance();
        }

        Employee employee = employeeRepository.findByIdAndClientIdAndOrgId(dto.getEmployeeId(), clientId, orgId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        attendance.setEmployee(employee);
        attendance.setAttendanceDate(dto.getAttendanceDate() != null ? dto.getAttendanceDate() : LocalDate.now(zoneId));
        attendance.setStatus(dto.getStatus() != null ? dto.getStatus() : "PRESENT");
        attendance.setPunchMethod(dto.getPunchMethod() != null ? dto.getPunchMethod() : "MANUAL");

        if ("ABSENT".equalsIgnoreCase(attendance.getStatus())) {
            attendance.setClockInTime(null);
            attendance.setClockOutTime(null);
            attendance.setTotalHoursWorked(BigDecimal.ZERO);
            attendance.setOvertimeHours(BigDecimal.ZERO);
        } else {
            LocalDateTime clockIn = dto.getClockInTime();
            if (clockIn != null && attendance.getAttendanceDate() != null) {
                // Ensure date component of clockInTime matches attendanceDate
                clockIn = LocalDateTime.of(attendance.getAttendanceDate(), clockIn.toLocalTime());
            }
            attendance.setClockInTime(clockIn);

            LocalDateTime clockOut = dto.getClockOutTime();
            if (clockOut != null && attendance.getAttendanceDate() != null) {
                // Ensure clockOut is on or after clockIn
                clockOut = LocalDateTime.of(attendance.getAttendanceDate(), clockOut.toLocalTime());
                if (clockIn != null && clockOut.isBefore(clockIn)) {
                    clockOut = clockOut.plusDays(1); // Shift crossed midnight
                }
            }
            attendance.setClockOutTime(clockOut);
            calculateHours(attendance);
        }

        Attendance saved = attendanceRepository.save(attendance);
        return mapToDto(saved);
    }

    @Transactional
    public void deleteAttendanceRecord(UUID id) {
        UUID clientId = TenantContext.getCurrentTenant();
        UUID orgId = TenantContext.getCurrentOrg();

        Attendance attendance = attendanceRepository.findByIdAndClientIdAndOrgId(id, clientId, orgId)
                .orElseThrow(() -> new RuntimeException("Attendance record not found"));

        attendanceRepository.delete(attendance);
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
