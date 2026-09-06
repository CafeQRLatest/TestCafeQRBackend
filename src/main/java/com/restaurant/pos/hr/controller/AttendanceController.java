package com.restaurant.pos.hr.controller;

import com.restaurant.pos.hr.dto.AttendanceDto;
import com.restaurant.pos.hr.service.AttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/hr/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    @PostMapping("/clock-in/{employeeId}")
    public ResponseEntity<AttendanceDto> clockIn(
            @PathVariable UUID employeeId,
            @RequestParam(defaultValue = "MANUAL") String punchMethod) {
        return ResponseEntity.ok(attendanceService.clockIn(employeeId, punchMethod));
    }

    @PostMapping("/clock-out/{employeeId}")
    public ResponseEntity<AttendanceDto> clockOut(@PathVariable UUID employeeId) {
        return ResponseEntity.ok(attendanceService.clockOut(employeeId));
    }

    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<List<AttendanceDto>> getAttendanceForEmployee(
            @PathVariable UUID employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(attendanceService.getAttendanceByEmployeeAndDateRange(employeeId, startDate, endDate));
    }
}
