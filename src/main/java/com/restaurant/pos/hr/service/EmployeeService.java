package com.restaurant.pos.hr.service;

import com.restaurant.pos.common.tenant.TenantContext;
import com.restaurant.pos.hr.dto.EmployeeDto;
import com.restaurant.pos.hr.entity.Department;
import com.restaurant.pos.hr.entity.Designation;
import com.restaurant.pos.hr.entity.Employee;
import com.restaurant.pos.hr.repository.DepartmentRepository;
import com.restaurant.pos.hr.repository.DesignationRepository;
import com.restaurant.pos.hr.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final DesignationRepository designationRepository;

    @Transactional(readOnly = true)
    public List<EmployeeDto> getAllEmployees() {
        UUID clientId = TenantContext.getCurrentTenant();
        UUID orgId = TenantContext.getCurrentOrg();
        
        return employeeRepository.findByClientIdAndOrgId(clientId, orgId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public EmployeeDto getEmployeeById(UUID id) {
        UUID clientId = TenantContext.getCurrentTenant();
        UUID orgId = TenantContext.getCurrentOrg();
        
        return employeeRepository.findByIdAndClientIdAndOrgId(id, clientId, orgId)
                .map(this::mapToDto)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
    }

    @Transactional
    public EmployeeDto createEmployee(EmployeeDto dto) {
        validateUniqueness(dto, null);
        Employee employee = new Employee();
        mapToEntity(dto, employee);
        employee.setActive(true);
        Employee saved = employeeRepository.save(employee);
        return mapToDto(saved);
    }

    @Transactional
    public EmployeeDto updateEmployee(UUID id, EmployeeDto dto) {
        UUID clientId = TenantContext.getCurrentTenant();
        UUID orgId = TenantContext.getCurrentOrg();
        
        Employee employee = employeeRepository.findByIdAndClientIdAndOrgId(id, clientId, orgId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
                
        validateUniqueness(dto, id);

        mapToEntity(dto, employee);
        Employee saved = employeeRepository.save(employee);
        return mapToDto(saved);
    }

    private void validateUniqueness(EmployeeDto dto, UUID currentId) {
        UUID clientId = TenantContext.getCurrentTenant();
        UUID orgId = TenantContext.getCurrentOrg();

        if (dto.getFirstName() == null || dto.getFirstName().trim().isEmpty()) {
            throw new RuntimeException("First name is required.");
        }

        if (dto.getLastName() == null || dto.getLastName().trim().isEmpty()) {
            throw new RuntimeException("Last name is required.");
        }

        if (dto.getPinCode() != null && !dto.getPinCode().isBlank()) {
            String cleanPin = dto.getPinCode().trim();
            if (!cleanPin.matches("\\d{4}")) {
                throw new RuntimeException("Kiosk PIN must be exactly 4 numeric digits.");
            }
            if (employeeRepository.existsByPinCodeAndClientIdAndOrgId(cleanPin, clientId, orgId, currentId)) {
                throw new RuntimeException("The PIN code '" + cleanPin + "' is already assigned to another employee.");
            }
        }

        if (dto.getEmail() != null && !dto.getEmail().isBlank()) {
            if (employeeRepository.existsByEmailAndClientId(dto.getEmail().trim(), clientId, currentId)) {
                throw new RuntimeException("An employee with email '" + dto.getEmail().trim() + "' already exists.");
            }
        }

        if (dto.getBaseSalary() != null && dto.getBaseSalary().compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw new RuntimeException("Base salary cannot be negative.");
        }

        if (dto.getHourlyRate() != null && dto.getHourlyRate().compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw new RuntimeException("Hourly rate cannot be negative.");
        }
    }

    @Transactional
    public void deleteEmployee(UUID id) {
        UUID clientId = TenantContext.getCurrentTenant();
        UUID orgId = TenantContext.getCurrentOrg();
        
        Employee employee = employeeRepository.findByIdAndClientIdAndOrgId(id, clientId, orgId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
                
        employeeRepository.delete(employee);
    }

    private void mapToEntity(EmployeeDto dto, Employee employee) {
        employee.setFirstName(dto.getFirstName());
        employee.setLastName(dto.getLastName());
        employee.setEmail(dto.getEmail());
        employee.setPhoneNumber(dto.getPhoneNumber());
        employee.setDateOfJoining(dto.getDateOfJoining());
        employee.setDateOfBirth(dto.getDateOfBirth());
        employee.setGender(dto.getGender());
        employee.setUserId(dto.getUserId());
        employee.setBaseSalary(dto.getBaseSalary());
        employee.setHourlyRate(dto.getHourlyRate());
        employee.setEmploymentType(dto.getEmploymentType());
        employee.setBankName(dto.getBankName());
        employee.setBankAccountNumber(dto.getBankAccountNumber());
        employee.setBankRoutingNumber(dto.getBankRoutingNumber());
        employee.setPinCode(dto.getPinCode());
        employee.setActive(dto.isActive());

        if (dto.getDepartmentId() != null) {
            Department department = departmentRepository.findById(dto.getDepartmentId())
                    .orElseThrow(() -> new RuntimeException("Department not found"));
            employee.setDepartment(department);
        } else {
            employee.setDepartment(null);
        }

        if (dto.getDesignationId() != null) {
            Designation designation = designationRepository.findById(dto.getDesignationId())
                    .orElseThrow(() -> new RuntimeException("Designation not found"));
            employee.setDesignation(designation);
        } else {
            employee.setDesignation(null);
        }
    }

    private EmployeeDto mapToDto(Employee entity) {
        EmployeeDto dto = EmployeeDto.builder()
                .id(entity.getId())
                .firstName(entity.getFirstName())
                .lastName(entity.getLastName())
                .email(entity.getEmail())
                .phoneNumber(entity.getPhoneNumber())
                .dateOfJoining(entity.getDateOfJoining())
                .dateOfBirth(entity.getDateOfBirth())
                .gender(entity.getGender())
                .userId(entity.getUserId())
                .baseSalary(entity.getBaseSalary())
                .hourlyRate(entity.getHourlyRate())
                .employmentType(entity.getEmploymentType())
                .bankName(entity.getBankName())
                .bankAccountNumber(entity.getBankAccountNumber())
                .bankRoutingNumber(entity.getBankRoutingNumber())
                .pinCode(entity.getPinCode())
                .isActive(entity.isActive())
                .build();
                
        if (entity.getDepartment() != null) {
            try {
                dto.setDepartmentId(entity.getDepartment().getId());
                dto.setDepartmentName(entity.getDepartment().getName());
            } catch (Exception ignored) {
                dto.setDepartmentId(null);
                dto.setDepartmentName(null);
            }
        }
        
        if (entity.getDesignation() != null) {
            try {
                dto.setDesignationId(entity.getDesignation().getId());
                dto.setDesignationName(entity.getDesignation().getName());
            } catch (Exception ignored) {
                dto.setDesignationId(null);
                dto.setDesignationName(null);
            }
        }
        
        return dto;
    }
}
