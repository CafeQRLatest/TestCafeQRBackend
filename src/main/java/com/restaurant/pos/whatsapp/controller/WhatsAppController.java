package com.restaurant.pos.whatsapp.controller;

import com.restaurant.pos.common.dto.ApiResponse;
import com.restaurant.pos.common.tenant.TenantContext;
import com.restaurant.pos.whatsapp.service.WhatsAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/whatsapp")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER')")
public class WhatsAppController {

    private final WhatsAppService whatsAppService;

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStatus(
            @RequestParam(value = "sessionId", required = false) String sessionId) {
        return ResponseEntity.ok(ApiResponse.success(whatsAppService.getGatewayStatus(resolveSessionId(sessionId))));
    }

    @GetMapping("/qr")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getQr(
            @RequestParam(value = "sessionId", required = false) String sessionId) {
        return ResponseEntity.ok(ApiResponse.success(whatsAppService.getGatewayQr(resolveSessionId(sessionId))));
    }

    @PostMapping("/disconnect")
    public ResponseEntity<ApiResponse<Map<String, Object>>> disconnect(
            @RequestBody(required = false) Map<String, String> body) {
        String explicitSessionId = body != null ? body.get("sessionId") : null;
        return ResponseEntity.ok(ApiResponse.success(whatsAppService.disconnectGateway(resolveSessionId(explicitSessionId))));
    }

    @PostMapping("/test")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sendTest(
            @RequestBody Map<String, String> body) {
        String phone = body != null ? body.get("phone") : null;
        String explicitSessionId = body != null ? body.get("sessionId") : null;
        return ResponseEntity.ok(ApiResponse.success(whatsAppService.sendTestMessage(resolveSessionId(explicitSessionId), phone)));
    }

    private String resolveSessionId(String explicitSessionId) {
        if (explicitSessionId != null && !explicitSessionId.isBlank()) {
            return explicitSessionId.trim();
        }
        UUID orgId = TenantContext.getCurrentOrg();
        if (orgId != null) {
            return "org_" + orgId;
        }
        UUID tenantId = TenantContext.getCurrentTenant();
        if (tenantId != null) {
            return "client_" + tenantId;
        }
        return "default";
    }
}
