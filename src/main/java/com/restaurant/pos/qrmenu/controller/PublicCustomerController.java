package com.restaurant.pos.qrmenu.controller;

import com.restaurant.pos.auth.service.OtpService;
import com.restaurant.pos.auth.service.EmailService;
import com.restaurant.pos.common.dto.ApiResponse;
import com.restaurant.pos.purchasing.domain.Customer;
import com.restaurant.pos.purchasing.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/public/customer")
@RequiredArgsConstructor
public class PublicCustomerController {

    private final OtpService otpService;
    private final CustomerRepository customerRepository;
    private final EmailService emailService;

    @PostMapping("/send-otp")
    public ResponseEntity<ApiResponse<String>> sendOtp(@RequestBody Map<String, String> payload) {
        String identifier = payload.get("identifier"); // Can be phone or email
        if (identifier == null || identifier.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Identifier is required"));
        }
        
        boolean isEmail = identifier.contains("@");
        String sanitized = isEmail ? identifier.trim() : normalizePhone(identifier);
        String otp = otpService.generateAndSaveOtp(sanitized);
        
        if (isEmail) {
            emailService.sendOtpEmail(sanitized, otp);
        } else {
            // SMS logic - Mocked for now as requested
            log.info("============== QR MENU SMS OTP ==============");
            log.info("Phone: {}", sanitized);
            log.info("OTP: {}", otp);
            log.info("=============================================");
        }
        
        return ResponseEntity.ok(ApiResponse.success("OTP sent successfully to " + sanitized));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifyOtp(@RequestBody Map<String, String> payload) {
        String identifier = payload.get("identifier");
        String name = payload.get("name");
        String otp = payload.get("otp");
        String clientIdStr = payload.get("clientId");
        String orgIdStr = payload.get("orgId");

        if (identifier == null || otp == null || clientIdStr == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Missing required fields"));
        }

        boolean isEmail = identifier.contains("@");
        String sanitized = isEmail ? identifier.trim() : normalizePhone(identifier);
        if (!otpService.verifyOtp(sanitized, otp)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Invalid or expired OTP"));
        }

        UUID clientId = UUID.fromString(clientIdStr);
        UUID orgId = (orgIdStr != null && !orgIdStr.isBlank() && !"null".equals(orgIdStr)) ? UUID.fromString(orgIdStr) : null;

        Optional<Customer> existing = isEmail 
                ? customerRepository.findByEmailAndClientId(sanitized, clientId)
                : customerRepository.findFirstByPhoneAndClientIdOrderByCreatedAtAsc(sanitized, clientId);
                
        Customer customer;
        if (existing.isPresent()) {
            customer = existing.get();
            if ((customer.getName() == null || "Guest".equals(customer.getName())) && name != null && !name.isBlank()) {
                customer.setName(name);
                customer = customerRepository.save(customer);
            }
        } else {
            customer = Customer.builder()
                    .name(name != null && !name.isBlank() ? name : "Guest")
                    .build();
            if (isEmail) customer.setEmail(sanitized);
            else customer.setPhone(sanitized);
            
            customer.setClientId(clientId);
            customer.setOrgId(orgId);
            customer = customerRepository.save(customer);
        }

        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "customerId", customer.getId(),
                "name", customer.getName(),
                "phone", customer.getPhone() != null ? customer.getPhone() : "",
                "email", customer.getEmail() != null ? customer.getEmail() : ""
        )));
    }

    private String normalizePhone(String phone) {
        if (phone == null) {
            return null;
        }
        String normalized = phone.trim().replaceAll("[\\s()\\-]", "");
        return normalized.isBlank() ? phone.trim() : normalized;
    }
}
