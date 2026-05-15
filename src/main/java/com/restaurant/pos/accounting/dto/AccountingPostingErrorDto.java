package com.restaurant.pos.accounting.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountingPostingErrorDto {
    private UUID id;
    private String sourceType;
    private UUID sourceId;
    private String status;
    private Integer attemptCount;
    private String lastError;
    private LocalDateTime updatedAt;
}
