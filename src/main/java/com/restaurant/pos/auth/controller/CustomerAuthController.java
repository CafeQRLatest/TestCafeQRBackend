package com.restaurant.pos.auth.controller;

import com.restaurant.pos.auth.service.OtpService;
import com.restaurant.pos.common.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Public auth endpoints consumed by the CafeQR Delivery Website / App.
 *
 * These endpoints do NOT require a staff JWT — they are for end-customers
 * authenticating with email OTP.
 *
 * Endpoints
 * ---------
 *  POST /api/v1/auth/customer/verify-otp
 *      Verifies a 6-digit OTP that was previously requested via
 *      POST /api/v1/auth/send-otp.
 *
 *      The OTP is stored in Redis (with fallback to in-memory) by OtpService.
 *      On success the frontend (Next.js) issues its own HttpOnly session cookie
 *      (HMAC-SHA256 via lib/auth.js) — this endpoint simply returns
 *      { verified: true, email } so the frontend knows the OTP was accepted.
 *
 * Request  { "email": "user@example.com", "otp": "123456" }
 * Response 200  { "success": true, "data": { "verified": true, "email": "..." } }
 * Response 400  { "success": false, "message": "Invalid or expired OTP" }
 */
@RestController
@RequestMapping("/api/v1/auth/customer")
@RequiredArgsConstructor
public class CustomerAuthController {

    private final OtpService otpService;

    // ── Request body DTO ──────────────────────────────────────────────────────

    @Data
    public static class VerifyOtpRequest {
        @Email(message = "Invalid email address")
        @NotBlank(message = "Email is required")
        private String email;

        @NotBlank(message = "OTP is required")
        private String otp;
    }

    // ── POST /api/v1/auth/customer/verify-otp ─────────────────────────────────

    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request
    ) {
        boolean valid = otpService.verifyOtp(request.getEmail(), request.getOtp());

        if (!valid) {
            return ResponseEntity
                    .status(400)
                    .body(ApiResponse.error("Invalid or expired OTP. Please request a new one."));
        }

        // Return minimal payload — the Next.js layer builds the session cookie
        Map<String, Object> payload = Map.of(
                "verified", true,
                "email",    request.getEmail()
        );

        return ResponseEntity.ok(ApiResponse.success(payload));
    }
}
