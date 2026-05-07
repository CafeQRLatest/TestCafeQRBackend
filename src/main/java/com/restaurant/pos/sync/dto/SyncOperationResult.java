package com.restaurant.pos.sync.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncOperationResult {
    private String operationId;
    private boolean success;
    private String status;
    private String message;
    private Object data;
}
