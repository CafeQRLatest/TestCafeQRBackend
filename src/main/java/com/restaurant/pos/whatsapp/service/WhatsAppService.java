package com.restaurant.pos.whatsapp.service;

import com.restaurant.pos.client.domain.Client;
import com.restaurant.pos.client.domain.Organization;
import com.restaurant.pos.client.repository.ClientRepository;
import com.restaurant.pos.client.repository.OrganizationRepository;
import com.restaurant.pos.invoice.domain.Invoice;
import com.restaurant.pos.invoice.repository.InvoiceRepository;
import com.restaurant.pos.order.domain.Order;
import com.restaurant.pos.order.dto.OrderCustomerDto;
import com.restaurant.pos.order.repository.OrderRepository;
import com.restaurant.pos.purchasing.domain.Customer;
import com.restaurant.pos.purchasing.repository.CustomerRepository;
import com.restaurant.pos.whatsapp.dto.WhatsAppSendRequestDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppService {

    private final WhatsAppBillFormatter billFormatter;
    private final OrganizationRepository organizationRepository;
    private final ClientRepository clientRepository;
    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final RestTemplate restTemplate = createRestTemplate();
    private final Map<java.util.UUID, Long> recentlySentOrders = new java.util.concurrent.ConcurrentHashMap<>();

    private static RestTemplate createRestTemplate() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        return new RestTemplate(factory);
    }

    @Value("${whatsapp.gateway.url:${WHATSAPP_GATEWAY_URL:http://whatsapp-gateway:3005}}")
    private String defaultGatewayUrl;

    /**
     * Asynchronously sends the digital bill to the customer's WhatsApp upon order settlement.
     */
    @Async
    public void sendOrderSettledBillAsync(Order order) {
        try {
            sendOrderSettledBill(order);
        } catch (Exception ex) {
            log.error("[WhatsAppService] Unexpected error sending bill for order {}", order != null ? order.getId() : null, ex);
        }
    }

    public boolean sendOrderSettledBill(Order order) {
        if (order == null || order.getId() == null) return false;

        Long lastSent = recentlySentOrders.get(order.getId());
        long now = System.currentTimeMillis();
        if (lastSent != null && (now - lastSent) < 60_000) {
            log.debug("[WhatsAppService] WhatsApp bill already sent recently for order {}, skipping duplicate", order.getId());
            return true;
        }

        String rawPhone = resolveCustomerPhone(order);
        if (rawPhone == null || rawPhone.isBlank()) {
            log.debug("[WhatsAppService] No customer phone number for order {}, skipping WhatsApp bill", order.getId());
            return false;
        }

        String normalizedPhone = billFormatter.normalizePhoneNumber(rawPhone);
        if (normalizedPhone == null) {
            log.warn("[WhatsAppService] Invalid phone number '{}' for order {}", rawPhone, order.getId());
            return false;
        }

        Organization org = order.getOrgId() != null 
                ? organizationRepository.findById(order.getOrgId()).orElse(null) 
                : null;
        Client client = order.getClientId() != null 
                ? clientRepository.findById(order.getClientId()).orElse(null) 
                : null;

        // Fetch linked invoice if available
        Invoice invoice = null;
        List<Invoice> invoices = invoiceRepository.findByOrderId(order.getId());
        if (invoices != null && !invoices.isEmpty()) {
            invoice = invoices.stream()
                    .filter(inv -> !"VOID".equalsIgnoreCase(inv.getStatus()))
                    .findFirst()
                    .orElse(invoices.get(0));
        }

        String billMessage = billFormatter.formatBillMessage(order, invoice, org, client);
        if (billMessage.isBlank()) {
            log.warn("[WhatsAppService] Formatted bill message was empty for order {}", order.getId());
            return false;
        }

        String branchSessionId = order.getOrgId() != null ? "org_" + order.getOrgId() : null;
        String fallbackSessionId = order.getClientId() != null ? "client_" + order.getClientId() : null;
        String primarySessionId = branchSessionId != null ? branchSessionId : fallbackSessionId;

        String pdfBase64 = order.getInvoicePdfBase64();
        String filename = null;
        if (pdfBase64 != null && !pdfBase64.isBlank()) {
            String invoiceNo = invoice != null && invoice.getInvoiceNo() != null ? invoice.getInvoiceNo() : order.getOrderNo();
            filename = "Invoice-" + (invoiceNo != null ? invoiceNo.replaceAll("[^\\w\\-]", "_") : "Receipt") + ".pdf";
        }

        boolean dispatched = dispatchToGateway(primarySessionId, fallbackSessionId, normalizedPhone, billMessage, pdfBase64, filename);
        if (dispatched) {
            recentlySentOrders.put(order.getId(), now);
        }
        return dispatched;
    }

    public boolean sendOrderBillWithAttachment(java.util.UUID orderId, String explicitPhone, String pdfBase64) {
        if (orderId == null) return false;
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("[WhatsAppService] Order not found for id {}", orderId);
            return false;
        }
        if (pdfBase64 != null && !pdfBase64.isBlank()) {
            order.setInvoicePdfBase64(pdfBase64);
        }
        if (explicitPhone != null && !explicitPhone.isBlank()) {
            order.setCustomerPhone(explicitPhone);
        }
        recentlySentOrders.remove(order.getId());
        return sendOrderSettledBill(order);
    }

    public boolean dispatchToGateway(String phone, String text, String pdfBase64, String filename) {
        return dispatchToGateway(null, null, phone, text, pdfBase64, filename);
    }

    public boolean dispatchToGateway(String sessionId, String fallbackSessionId, String phone, String text, String pdfBase64, String filename) {
        try {
            String url = defaultGatewayUrl + "/api/send-bill";
            WhatsAppSendRequestDto req = WhatsAppSendRequestDto.builder()
                    .sessionId(sessionId)
                    .fallbackSessionId(fallbackSessionId)
                    .phone(phone)
                    .text(text)
                    .pdfBase64(pdfBase64)
                    .filename(filename)
                    .build();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<WhatsAppSendRequestDto> entity = new HttpEntity<>(req, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("[WhatsAppService] Successfully dispatched WhatsApp bill to {} (session: {})", phone, sessionId);
                return true;
            } else {
                log.warn("[WhatsAppService] Gateway returned non-2xx status: {}", response.getStatusCode());
                return false;
            }
        } catch (Exception ex) {
            log.warn("[WhatsAppService] Failed to send WhatsApp bill to {}: {}", phone, ex.getMessage());
            return false;
        }
    }

    public Map<String, Object> getGatewayStatus() {
        return getGatewayStatus(null);
    }

    public Map<String, Object> getGatewayStatus(String sessionId) {
        try {
            String url = defaultGatewayUrl + "/api/status" + (sessionId != null ? "?sessionId=" + sessionId : "");
            ResponseEntity<Map> resp = restTemplate.getForEntity(url, Map.class);
            return resp.getBody();
        } catch (Exception ex) {
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("success", false);
            fallback.put("status", "DISCONNECTED");
            fallback.put("message", "Gateway offline / unreachable: " + ex.getMessage());
            return fallback;
        }
    }

    public Map<String, Object> getGatewayQr() {
        return getGatewayQr(null);
    }

    public Map<String, Object> getGatewayQr(String sessionId) {
        try {
            String url = defaultGatewayUrl + "/api/qr" + (sessionId != null ? "?sessionId=" + sessionId : "");
            ResponseEntity<Map> resp = restTemplate.getForEntity(url, Map.class);
            return resp.getBody();
        } catch (Exception ex) {
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("success", false);
            fallback.put("status", "DISCONNECTED");
            fallback.put("message", "Gateway offline / unreachable: " + ex.getMessage());
            return fallback;
        }
    }

    public Map<String, Object> disconnectGateway() {
        return disconnectGateway(null);
    }

    public Map<String, Object> disconnectGateway(String sessionId) {
        try {
            String url = defaultGatewayUrl + "/api/disconnect";
            Map<String, String> body = sessionId != null ? Collections.singletonMap("sessionId", sessionId) : Collections.emptyMap();
            ResponseEntity<Map> resp = restTemplate.postForEntity(url, body, Map.class);
            return resp.getBody();
        } catch (Exception ex) {
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("success", false);
            fallback.put("message", "Gateway offline / unreachable: " + ex.getMessage());
            return fallback;
        }
    }

    public Map<String, Object> sendTestMessage(String phone) {
        return sendTestMessage(null, phone);
    }

    public Map<String, Object> sendTestMessage(String sessionId, String phone) {
        try {
            String url = defaultGatewayUrl + "/api/test-message";
            Map<String, String> body = new HashMap<>();
            if (sessionId != null) body.put("sessionId", sessionId);
            if (phone != null) body.put("phone", phone);
            ResponseEntity<Map> resp = restTemplate.postForEntity(url, body, Map.class);
            return resp.getBody();
        } catch (Exception ex) {
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("success", false);
            fallback.put("message", "Failed to send test message: " + ex.getMessage());
            return fallback;
        }
    }

    private String resolveCustomerPhone(Order order) {
        if (order.getCustomerPhone() != null && !order.getCustomerPhone().isBlank()) {
            return order.getCustomerPhone();
        }
        if (order.getCustomers() != null && !order.getCustomers().isEmpty()) {
            for (OrderCustomerDto c : order.getCustomers()) {
                if (c != null && c.getPhone() != null && !c.getPhone().isBlank()) {
                    return c.getPhone();
                }
            }
        }
        if (order.getCustomerId() != null) {
            Customer c = customerRepository.findById(order.getCustomerId()).orElse(null);
            if (c != null && c.getPhone() != null && !c.getPhone().isBlank()) {
                return c.getPhone();
            }
        }
        // Fallback: search in description
        if (order.getDescription() != null) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:Phone|ph|mobile):\\s*([^|,\\n]+)", java.util.regex.Pattern.CASE_INSENSITIVE)
                    .matcher(order.getDescription());
            if (m.find()) {
                return m.group(1).trim();
            }
        }
        return null;
    }
}
