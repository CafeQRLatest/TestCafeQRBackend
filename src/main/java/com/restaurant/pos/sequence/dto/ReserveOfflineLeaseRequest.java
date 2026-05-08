package com.restaurant.pos.sequence.dto;

import com.restaurant.pos.sequence.domain.DocumentType;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class ReserveOfflineLeaseRequest {
    private UUID terminalId;
    private Integer blockSize;
    private List<DocumentType> documentTypes;
}
