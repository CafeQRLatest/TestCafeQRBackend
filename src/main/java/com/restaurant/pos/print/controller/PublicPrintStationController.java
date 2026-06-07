package com.restaurant.pos.print.controller;

import com.restaurant.pos.common.dto.ApiResponse;
import com.restaurant.pos.print.dto.PrintJobStatusRequest;
import com.restaurant.pos.print.dto.PrintStationHeartbeatRequest;
import com.restaurant.pos.print.dto.PrintStationPairRequest;
import com.restaurant.pos.print.dto.PrintStationConfigurationRequest;
import com.restaurant.pos.print.service.PrintJobService;
import com.restaurant.pos.print.service.PrintStationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public/print-stations")
@RequiredArgsConstructor
public class PublicPrintStationController {

    private static final String TOKEN_HEADER = "X-CafeQR-Station-Token";

    private final PrintStationService stationService;
    private final PrintJobService printJobService;

    @PostMapping("/enroll")
    public ResponseEntity<ApiResponse<Map<String, Object>>> enroll(
            @RequestBody PrintStationPairRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(stationService.pair(request)));
    }

    @PostMapping("/heartbeat")
    public ResponseEntity<ApiResponse<Map<String, Object>>> heartbeat(
            @RequestHeader(TOKEN_HEADER) String token,
            @RequestBody(required = false) PrintStationHeartbeatRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(stationService.heartbeat(token, request)));
    }

    @PutMapping("/configuration")
    public ResponseEntity<ApiResponse<Map<String, Object>>> configuration(
            @RequestHeader(TOKEN_HEADER) String token,
            @RequestBody PrintStationConfigurationRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                stationService.syncConfiguration(token, request)));
    }

    @PostMapping("/jobs/claim")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> claim(
            @RequestHeader(TOKEN_HEADER) String token,
            @RequestParam(defaultValue = "5") int limit
    ) {
        return ResponseEntity.ok(ApiResponse.success(stationService.claim(token, limit).stream()
                .map(printJobService::describe)
                .toList()));
    }

    @PostMapping("/jobs/{jobId}/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> status(
            @RequestHeader(TOKEN_HEADER) String token,
            @PathVariable UUID jobId,
            @RequestBody PrintJobStatusRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                printJobService.describe(stationService.updateJob(token, jobId, request))
        ));
    }
}
