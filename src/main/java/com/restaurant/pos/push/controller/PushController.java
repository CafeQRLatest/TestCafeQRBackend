package com.restaurant.pos.push.controller;

import com.restaurant.pos.common.dto.ApiResponse;
import com.restaurant.pos.common.tenant.TenantContext;
import com.restaurant.pos.common.util.SecurityUtils;
import com.restaurant.pos.push.domain.PushDeviceToken;
import com.restaurant.pos.push.service.PushNotificationService;
import com.restaurant.pos.push.dto.PushSubscribeRequest;
import com.restaurant.pos.push.dto.PushPreferencesRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/push")
@RequiredArgsConstructor
public class PushController {

    private final PushNotificationService pushNotificationService;

    @PostMapping("/subscribe")
    public ResponseEntity<ApiResponse<Void>> subscribe(@Valid @RequestBody PushSubscribeRequest request) {
        UUID clientId = TenantContext.getCurrentTenant();
        UUID userId = SecurityUtils.getCurrentUserId();
        pushNotificationService.subscribe(clientId, userId, request);
        return ResponseEntity.ok(ApiResponse.success("Subscribed successfully", null));
    }

    @PostMapping("/unsubscribe")
    public ResponseEntity<ApiResponse<Void>> unsubscribe(@RequestBody Map<String, String> body) {
        String token = body.get("deviceToken");
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("deviceToken is required"));
        }
        pushNotificationService.unsubscribe(token);
        return ResponseEntity.ok(ApiResponse.success("Unsubscribed successfully", null));
    }

    @PutMapping("/preferences")
    public ResponseEntity<ApiResponse<Void>> updatePreferences(@Valid @RequestBody PushPreferencesRequest request) {
        pushNotificationService.updatePreferences(request.getDeviceToken(), request);
        return ResponseEntity.ok(ApiResponse.success("Preferences updated successfully", null));
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<PushDeviceToken>> getStatus(@RequestParam String deviceToken) {
        return pushNotificationService.getStatus(deviceToken)
                .map(token -> ResponseEntity.ok(ApiResponse.success(token)))
                .orElse(ResponseEntity.ok(ApiResponse.error("Device token not found")));
    }
}
