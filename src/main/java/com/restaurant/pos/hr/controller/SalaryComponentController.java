package com.restaurant.pos.hr.controller;

import com.restaurant.pos.hr.dto.SalaryComponentDto;
import com.restaurant.pos.hr.service.SalaryComponentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/hr/salary-components")
@RequiredArgsConstructor
public class SalaryComponentController {

    private final SalaryComponentService salaryComponentService;

    @GetMapping
    public ResponseEntity<List<SalaryComponentDto>> getAllComponents() {
        return ResponseEntity.ok(salaryComponentService.getAllComponents());
    }

    @PostMapping
    public ResponseEntity<SalaryComponentDto> createComponent(@RequestBody SalaryComponentDto dto) {
        return ResponseEntity.ok(salaryComponentService.createComponent(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SalaryComponentDto> updateComponent(@PathVariable UUID id, @RequestBody SalaryComponentDto dto) {
        return ResponseEntity.ok(salaryComponentService.updateComponent(id, dto));
    }
}
