package com.restaurant.pos.hr.service;

import com.restaurant.pos.common.tenant.TenantContext;
import com.restaurant.pos.hr.dto.PayrollRunDto;
import com.restaurant.pos.hr.dto.SalarySlipDto;
import com.restaurant.pos.hr.entity.*;
import com.restaurant.pos.hr.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PayrollEngineService {

    private final PayrollRunRepository payrollRunRepository;
    private final SalarySlipRepository salarySlipRepository;
    private final EmployeeRepository employeeRepository;
    private final SalaryComponentRepository salaryComponentRepository;
    private final AttendanceRepository attendanceRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final SalaryAdvanceRepository salaryAdvanceRepository;

    @Transactional
    public PayrollRunDto initiatePayrollRun(PayrollRunDto dto) {
        PayrollRun run = new PayrollRun();
        run.setName(dto.getName());
        run.setStartDate(dto.getStartDate());
        run.setEndDate(dto.getEndDate());
        run.setStatus("PROCESSING");
        
        PayrollRun saved = payrollRunRepository.save(run);
        
        // Generate Slips
        generateSalarySlips(saved);
        
        saved.setStatus("COMPLETED");
        return mapToRunDto(payrollRunRepository.save(saved));
    }

    private void generateSalarySlips(PayrollRun run) {
        UUID clientId = TenantContext.getCurrentTenant();
        UUID orgId = TenantContext.getCurrentOrg();

        // 1. Fetch all active employees
        List<Employee> employees = employeeRepository.findByClientIdAndOrgId(clientId, orgId)
                .stream().filter(Employee::isActive).collect(Collectors.toList());

        // 2. Fetch all active salary components
        List<SalaryComponent> components = salaryComponentRepository.findActiveComponents(clientId, orgId);

        for (Employee emp : employees) {
            SalarySlip slip = new SalarySlip();
            slip.setEmployee(emp);
            slip.setPayrollRun(run);
            
            // 3. Aggregate Timecards
            List<Attendance> attendances = attendanceRepository.findByEmployeeIdAndDateRangeAndClientIdAndOrgId(
                    emp.getId(), run.getStartDate(), run.getEndDate(), clientId, orgId);
            
            BigDecimal totalHours = attendances.stream()
                    .map(Attendance::getTotalHoursWorked)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            slip.setTotalWorkedHours(totalHours);

            // 4. Aggregate Unpaid Leaves
            List<LeaveRequest> leaves = leaveRequestRepository.findApprovedByEmployeeIdAndDateRange(
                    emp.getId(), run.getStartDate(), run.getEndDate(), clientId, orgId);
                    
            int unpaidLeaveDays = leaves.stream()
                    .filter(l -> "UNPAID".equals(l.getLeaveType()))
                    .mapToInt(LeaveRequest::getTotalDays)
                    .sum();
            slip.setTotalUnpaidLeaveDays(unpaidLeaveDays);

            // 5. Calculate Base Pay
            BigDecimal grossPay = BigDecimal.ZERO;
            if ("HOURLY".equals(emp.getEmploymentType())) {
                grossPay = emp.getHourlyRate().multiply(totalHours);
            } else {
                // Monthly salaried - subtract unpaid leaves
                BigDecimal dailyRate = emp.getBaseSalary().divide(new BigDecimal("30"), 2, RoundingMode.HALF_UP);
                BigDecimal deductionForLeaves = dailyRate.multiply(BigDecimal.valueOf(unpaidLeaveDays));
                grossPay = emp.getBaseSalary().subtract(deductionForLeaves);
            }

            // 6. Apply Rules Engine (Components)
            BigDecimal totalDeductions = BigDecimal.ZERO;
            for (SalaryComponent comp : components) {
                BigDecimal compAmount = BigDecimal.ZERO;
                if ("FIXED".equals(comp.getAmountType())) {
                    compAmount = comp.getDefaultAmount();
                } else if ("PERCENTAGE".equals(comp.getAmountType())) {
                    // Calculate % of Gross Pay for now
                    compAmount = grossPay.multiply(comp.getPercentage()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                }

                if ("EARNING".equals(comp.getType())) {
                    grossPay = grossPay.add(compAmount);
                } else if ("DEDUCTION".equals(comp.getType())) {
                    totalDeductions = totalDeductions.add(compAmount);
                }
            }

            // 7. Deduct Salary Advances
            List<SalaryAdvance> advances = salaryAdvanceRepository.findActiveAdvancesByEmployeeId(emp.getId(), clientId, orgId);
            for (SalaryAdvance advance : advances) {
                BigDecimal toDeduct = advance.getMonthlyInstallmentAmount();
                if (toDeduct.compareTo(advance.getRemainingBalance()) > 0) {
                    toDeduct = advance.getRemainingBalance();
                }
                totalDeductions = totalDeductions.add(toDeduct);
                
                // Update advance balance
                advance.setRemainingBalance(advance.getRemainingBalance().subtract(toDeduct));
                if (advance.getRemainingBalance().compareTo(BigDecimal.ZERO) == 0) {
                    advance.setStatus("PAID");
                }
                salaryAdvanceRepository.save(advance);
            }

            // 8. Finalize Net Pay
            slip.setGrossPay(grossPay);
            slip.setTotalDeductions(totalDeductions);
            
            BigDecimal netPay = grossPay.subtract(totalDeductions);
            if (netPay.compareTo(BigDecimal.ZERO) < 0) netPay = BigDecimal.ZERO;
            
            slip.setNetPay(netPay);
            slip.setStatus("GENERATED");
            
            salarySlipRepository.save(slip);
        }
    }

    @Transactional(readOnly = true)
    public List<PayrollRunDto> getAllPayrollRuns() {
        UUID clientId = TenantContext.getCurrentTenant();
        UUID orgId = TenantContext.getCurrentOrg();
        
        return payrollRunRepository.findByClientIdAndOrgId(clientId, orgId).stream()
                .map(this::mapToRunDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<SalarySlipDto> getSlipsForRun(UUID payrollRunId) {
        UUID clientId = TenantContext.getCurrentTenant();
        UUID orgId = TenantContext.getCurrentOrg();
        
        return salarySlipRepository.findByPayrollRunIdAndClientIdAndOrgId(payrollRunId, clientId, orgId).stream()
                .map(this::mapToSlipDto)
                .collect(Collectors.toList());
    }

    private PayrollRunDto mapToRunDto(PayrollRun entity) {
        return PayrollRunDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .startDate(entity.getStartDate())
                .endDate(entity.getEndDate())
                .status(entity.getStatus())
                .build();
    }
    
    private SalarySlipDto mapToSlipDto(SalarySlip entity) {
        return SalarySlipDto.builder()
                .id(entity.getId())
                .employeeId(entity.getEmployee().getId())
                .employeeName(entity.getEmployee().getFirstName() + " " + entity.getEmployee().getLastName())
                .payrollRunId(entity.getPayrollRun().getId())
                .totalWorkedHours(entity.getTotalWorkedHours())
                .totalUnpaidLeaveDays(entity.getTotalUnpaidLeaveDays())
                .grossPay(entity.getGrossPay())
                .totalDeductions(entity.getTotalDeductions())
                .netPay(entity.getNetPay())
                .status(entity.getStatus())
                .build();
    }
}
