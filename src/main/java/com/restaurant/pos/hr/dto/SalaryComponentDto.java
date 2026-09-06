package com.restaurant.pos.hr.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalaryComponentDto {
    private UUID id;
    private String name;
    private String type; // EARNING or DEDUCTION
    private boolean isTaxApplicable;
    private boolean dependsOnAttendance;
    private String amountType; // FIXED or PERCENTAGE
    private BigDecimal defaultAmount;
    private BigDecimal percentage;
    private String percentageOfComponent;
    private boolean isActive;
}
