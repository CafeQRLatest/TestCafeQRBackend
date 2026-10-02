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

import java.util.HashMap;
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
    private final com.restaurant.pos.qrmenu.query.QrOrderQueryService qrOrderQueryService;

    @PostMapping("/check-email")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkEmail(@RequestBody Map<String, String> payload) {
        String email = payload.get("email");
        String clientIdStr = payload.get("clientId");
        if (email == null || email.isBlank() || clientIdStr == null || clientIdStr.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Email and clientId are required"));
        }
        UUID clientId = qrOrderQueryService.resolveClientId(clientIdStr);
        String sanitized = email.trim().toLowerCase();
        Optional<Customer> existing = customerRepository.findByEmailAndClientId(sanitized, clientId);
        if (existing.isPresent()) {
            Customer c = existing.get();
            return ResponseEntity.ok(ApiResponse.success(Map.of(
                    "exists", true,
                    "name", c.getName() != null ? c.getName() : "",
                    "phone", c.getPhone() != null ? c.getPhone() : ""
            )));
        } else {
            return ResponseEntity.ok(ApiResponse.success(Map.of(
                    "exists", false
            )));
        }
    }

    @PostMapping("/send-otp")
    public ResponseEntity<ApiResponse<String>> sendOtp(@RequestBody Map<String, String> payload) {
        String identifier = payload.get("identifier"); // Can be phone or email
        if (identifier == null || identifier.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Identifier is required"));
        }
        
        boolean isEmail = identifier.contains("@");
        String sanitized = isEmail ? identifier.trim().toLowerCase() : normalizePhone(identifier);
        String otp = otpService.generateAndSaveOtp(sanitized);
        
        if (isEmail) {
            log.info("============== QR MENU EMAIL OTP ==============");
            log.info("Email: {}", sanitized);
            log.info("OTP: {}", otp);
            log.info("===============================================");
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
        String phone = payload.get("phone");
        String otp = payload.get("otp");
        String clientIdStr = payload.get("clientId");
        String orgIdStr = payload.get("orgId");

        if (identifier == null || otp == null || clientIdStr == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Missing required fields"));
        }

        boolean isEmail = identifier.contains("@");
        String sanitized = isEmail ? identifier.trim().toLowerCase() : normalizePhone(identifier);
        if (!otpService.verifyOtp(sanitized, otp)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Invalid or expired OTP"));
        }

        UUID clientId = qrOrderQueryService.resolveClientId(clientIdStr);
        UUID orgId = qrOrderQueryService.resolveOrgId(clientId, orgIdStr);

        Optional<Customer> existing = isEmail 
                ? customerRepository.findByEmailAndClientId(sanitized, clientId)
                : customerRepository.findFirstByPhoneAndClientIdOrderByCreatedAtAsc(sanitized, clientId);
                
        Customer customer;
        if (existing.isPresent()) {
            customer = existing.get();
            boolean updated = false;
            if (name != null && !name.isBlank() && (customer.getName() == null || "Guest".equalsIgnoreCase(customer.getName()))) {
                customer.setName(name.trim());
                updated = true;
            }
            if (phone != null && !phone.isBlank() && (customer.getPhone() == null || customer.getPhone().isBlank())) {
                customer.setPhone(normalizePhone(phone));
                updated = true;
            }
            if (updated) {
                customer = customerRepository.save(customer);
            }
        } else {
            customer = Customer.builder()
                    .name(name != null && !name.isBlank() ? name.trim() : "Guest")
                    .build();
            if (isEmail) {
                customer.setEmail(sanitized);
                if (phone != null && !phone.isBlank()) {
                    customer.setPhone(normalizePhone(phone));
                }
            } else {
                customer.setPhone(sanitized);
            }
            
            customer.setClientId(clientId);
            customer.setOrgId(orgId);
            customer = customerRepository.save(customer);
        }

        assert customer.getName() != null;
        Map<String, Object> customerData = new HashMap<>();
        customerData.put("id", customer.getId().toString());
        customerData.put("customerId", customer.getId().toString());
        customerData.put("name", customer.getName());
        customerData.put("phone", customer.getPhone() != null ? customer.getPhone() : "");
        customerData.put("email", customer.getEmail() != null ? customer.getEmail() : "");
        return ResponseEntity.ok(ApiResponse.success(customerData));
    }

    private String normalizePhone(String phone) {
        if (phone == null) {
            return null;
        }
        String normalized = phone.trim().replaceAll("[\\s()\\-]", "");
        return normalized.isBlank() ? phone.trim() : normalized;
    }
}
