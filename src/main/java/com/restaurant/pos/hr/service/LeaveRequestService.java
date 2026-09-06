package com.restaurant.pos.hr.service;

import com.restaurant.pos.common.tenant.TenantContext;
import com.restaurant.pos.hr.dto.LeaveRequestDto;
import com.restaurant.pos.hr.entity.Employee;
import com.restaurant.pos.hr.entity.LeaveRequest;
import com.restaurant.pos.hr.repository.EmployeeRepository;
import com.restaurant.pos.hr.repository.LeaveRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LeaveRequestService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeRepository employeeRepository;

    @Transactional(readOnly = true)
    public List<LeaveRequestDto> getAllLeaveRequests() {
        UUID clientId = TenantContext.getCurrentTenant();
        UUID orgId = TenantContext.getCurrentOrg();
        
        return leaveRequestRepository.findByClientIdAndOrgId(clientId, orgId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<LeaveRequestDto> getLeaveRequestsByEmployee(UUID employeeId) {
        UUID clientId = TenantContext.getCurrentTenant();
        UUID orgId = TenantContext.getCurrentOrg();
        
        return leaveRequestRepository.findByEmployeeIdAndClientIdAndOrgId(employeeId, clientId, orgId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public LeaveRequestDto createLeaveRequest(LeaveRequestDto dto) {
        UUID clientId = TenantContext.getCurrentTenant();
        UUID orgId = TenantContext.getCurrentOrg();
        
        Employee employee = employeeRepository.findByIdAndClientIdAndOrgId(dto.getEmployeeId(), clientId, orgId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        LeaveRequest leave = new LeaveRequest();
        leave.setEmployee(employee);
        leave.setLeaveType(dto.getLeaveType());
        leave.setStartDate(dto.getStartDate());
        leave.setEndDate(dto.getEndDate());
        leave.setTotalDays(dto.getTotalDays());
        leave.setReason(dto.getReason());
        leave.setStatus("PENDING");
        
        LeaveRequest saved = leaveRequestRepository.save(leave);
        return mapToDto(saved);
    }

    @Transactional
    public LeaveRequestDto updateLeaveStatus(UUID id, String status) {
        UUID clientId = TenantContext.getCurrentTenant();
        UUID orgId = TenantContext.getCurrentOrg();
        
        LeaveRequest leave = leaveRequestRepository.findByIdAndClientIdAndOrgId(id, clientId, orgId)
                .orElseThrow(() -> new RuntimeException("LeaveRequest not found"));
                
        leave.setStatus(status);
        LeaveRequest saved = leaveRequestRepository.save(leave);
        return mapToDto(saved);
    }

    private LeaveRequestDto mapToDto(LeaveRequest entity) {
        return LeaveRequestDto.builder()
                .id(entity.getId())
                .employeeId(entity.getEmployee().getId())
                .employeeName(entity.getEmployee().getFirstName() + " " + entity.getEmployee().getLastName())
                .leaveType(entity.getLeaveType())
                .startDate(entity.getStartDate())
                .endDate(entity.getEndDate())
                .totalDays(entity.getTotalDays())
                .reason(entity.getReason())
                .status(entity.getStatus())
                .build();
    }
}
