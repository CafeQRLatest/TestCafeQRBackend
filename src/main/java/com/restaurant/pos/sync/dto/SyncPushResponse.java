package com.restaurant.pos.sync.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncPushResponse {
    private Instant serverTime;

    @Builder.Default
    private List<SyncOperationResult> results = new ArrayList<>();
}
