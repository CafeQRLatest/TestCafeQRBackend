package com.restaurant.pos.sync.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncChangesResponse {
    private Instant since;
    private Instant serverTime;
    private SyncBootstrapResponse snapshot;
}
