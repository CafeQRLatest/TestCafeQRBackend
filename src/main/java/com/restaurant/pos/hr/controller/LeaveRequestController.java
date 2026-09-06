package com.restaurant.pos.hr.controller;

import com.restaurant.pos.hr.dto.LeaveRequestDto;
import com.restaurant.pos.hr.service.LeaveRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/hr/leaves")
@RequiredArgsConstructor
public class LeaveRequestController {

    private final LeaveRequestService leaveRequestService;

    @GetMapping
    public ResponseEntity<List<LeaveRequestDto>> getAllLeaveRequests() {
        return ResponseEntity.ok(leaveRequestService.getAllLeaveRequests());
    }
    
    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<List<LeaveRequestDto>> getLeaveRequestsByEmployee(@PathVariable UUID employeeId) {
        return ResponseEntity.ok(leaveRequestService.getLeaveRequestsByEmployee(employeeId));
    }

    @PostMapping
    public ResponseEntity<LeaveRequestDto> createLeaveRequest(@RequestBody LeaveRequestDto dto) {
        return ResponseEntity.ok(leaveRequestService.createLeaveRequest(dto));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<LeaveRequestDto> updateLeaveStatus(
            @PathVariable UUID id,
            @RequestParam String status) {
        return ResponseEntity.ok(leaveRequestService.updateLeaveStatus(id, status));
    }
}
