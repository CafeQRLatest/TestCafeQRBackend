package com.restaurant.pos.hr.service;

import com.restaurant.pos.common.context.TimezoneResolver;
import com.restaurant.pos.common.tenant.TenantContext;
import com.restaurant.pos.hr.dto.AttendanceDto;
import com.restaurant.pos.hr.entity.Attendance;
import com.restaurant.pos.hr.entity.Employee;
import com.restaurant.pos.hr.entity.LeaveRequest;
import com.restaurant.pos.hr.repository.AttendanceRepository;
import com.restaurant.pos.hr.repository.EmployeeRepository;
import com.restaurant.pos.hr.repository.LeaveRequestRepository;
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
    private final LeaveRequestRepository leaveRequestRepository;
    private final TimezoneResolver timezoneResolver;
    private final HrSettingsService hrSettingsService;

    private static final BigDecimal DEFAULT_STANDARD_HOURS_PER_DAY = new BigDecimal("8.00");

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

        // Check if employee currently has an active open shift
        List<Attendance> activeShifts = attendanceRepository.findActiveAttendanceByEmployeeIdAndClientIdAndOrgId(employeeId, clientId, orgId);
        if (!activeShifts.isEmpty()) {
            Attendance openShift = activeShifts.get(0);
            throw new RuntimeException("Employee is already clocked in (shift started on " + openShift.getAttendanceDate() + "). Please clock out first.");
        }

        LocalDate today = LocalDate.now(zoneId);

        // Block clock-in if employee has an approved leave today
        List<LeaveRequest> approvedLeaves = leaveRequestRepository.findApprovedByEmployeeIdAndDate(
                employeeId, today, clientId, orgId);
        if (!approvedLeaves.isEmpty()) {
            LeaveRequest leave = approvedLeaves.get(0);
            throw new RuntimeException("Cannot clock in: Employee has an approved "
                    + leave.getLeaveType() + " leave from " + leave.getStartDate()
                    + " to " + leave.getEndDate() + ". Cancel the leave first.");
        }

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
        
        List<Attendance> activeShifts = attendanceRepository.findActiveAttendanceByEmployeeIdAndClientIdAndOrgId(employeeId, clientId, orgId);
        if (activeShifts.isEmpty()) {
            throw new RuntimeException("No active clock-in session found for employee.");
        }

        Attendance attendance = activeShifts.get(0);
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

            BigDecimal threshold = DEFAULT_STANDARD_HOURS_PER_DAY;
            try {
                if (hrSettingsService != null && hrSettingsService.getSettings() != null) {
                    BigDecimal customHours = hrSettingsService.getSettings().getStandardHoursPerDay();
                    if (customHours != null && customHours.compareTo(BigDecimal.ZERO) > 0) {
                        threshold = customHours;
                    }
                }
            } catch (Exception ignored) {
            }
            
            if (totalHours.compareTo(threshold) > 0) {
                attendance.setOvertimeHours(totalHours.subtract(threshold));
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
            // Block manual attendance if employee has an approved leave on that date
            List<LeaveRequest> approvedLeaves = leaveRequestRepository.findApprovedByEmployeeIdAndDate(
                    dto.getEmployeeId(), attendance.getAttendanceDate(), clientId, orgId);
            if (!approvedLeaves.isEmpty()) {
                LeaveRequest leave = approvedLeaves.get(0);
                throw new RuntimeException("Cannot create attendance: Employee has an approved "
                        + leave.getLeaveType() + " leave from " + leave.getStartDate()
                        + " to " + leave.getEndDate() + ". Cancel the leave first.");
            }
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
