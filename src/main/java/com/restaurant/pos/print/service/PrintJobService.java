package com.restaurant.pos.print.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.pos.common.exception.BusinessException;
import com.restaurant.pos.common.tenant.TenantContext;
import com.restaurant.pos.order.domain.Order;
import com.restaurant.pos.order.domain.OrderLine;
import com.restaurant.pos.order.repository.OrderRepository;
import com.restaurant.pos.print.domain.PrintJob;
import com.restaurant.pos.print.domain.PrintJobKind;
import com.restaurant.pos.print.domain.PrintJobStatus;
import com.restaurant.pos.print.repository.PrintJobAttemptRepository;
import com.restaurant.pos.print.repository.PrintJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrintJobService {

    private final PrintJobRepository printJobRepository;
    private final PrintJobAttemptRepository printJobAttemptRepository;
    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public PrintJob enqueueOrderJob(UUID orderId, String jobKind) {
        UUID clientId = TenantContext.getCurrentTenant();
        Order order = orderRepository.findByIdAndClientId(orderId, clientId)
                .orElseThrow(() -> new BusinessException("Order not found"));
        return enqueueForOrder(order, parseKind(jobKind), "manual");
    }

    @Transactional
    public PrintJob enqueueForOrder(Order order, PrintJobKind kind, String reason) {
        if (order == null || order.getId() == null || kind == null) {
            throw new BusinessException("Invalid print job request");
        }

        UUID clientId = order.getClientId() != null ? order.getClientId() : TenantContext.getCurrentTenant();
        if (clientId == null) {
            throw new BusinessException("Print job tenant is missing");
        }
        String dedupeKey = buildDedupeKey(order, kind, reason);

        return printJobRepository.findByClientIdAndDedupeKey(clientId, dedupeKey)
                .orElseGet(() -> createJob(order, kind, reason, dedupeKey, clientId));
    }

    @Transactional
    public List<PrintJob> claimJobs(int limit) {
        UUID clientId = TenantContext.getCurrentTenant();
        if (clientId == null) {
            return List.of();
        }
        UUID orgId = TenantContext.getCurrentOrg();
        UUID terminalId = TenantContext.getCurrentTerminal();
        int boundedLimit = Math.max(1, Math.min(limit <= 0 ? 5 : limit, 20));

        List<PrintJob> jobs = printJobRepository.findClaimable(
                clientId,
                orgId,
                List.of(PrintJobStatus.PENDING, PrintJobStatus.RETRY),
                List.of(PrintJobKind.KOT, PrintJobKind.BILL),
                LocalDateTime.now(),
                PageRequest.of(0, boundedLimit)
        );

        LocalDateTime now = LocalDateTime.now();
        for (PrintJob job : jobs) {
            job.setStatus(PrintJobStatus.CLAIMED);
            job.setClaimedByTerminalId(terminalId);
            job.setClaimedAt(now);
            job.setAttempts((job.getAttempts() == null ? 0 : job.getAttempts()) + 1);
        }
        return printJobRepository.saveAll(jobs);
    }

    @Transactional
    public PrintJob markPrinted(UUID id) {
        PrintJob job = getJob(id);
        markPrintedFields(job, LocalDateTime.now());
        return printJobRepository.save(job);
    }

    @Transactional
    public List<PrintJob> markPrintedForOrder(UUID orderId, String jobKind) {
        UUID clientId = TenantContext.getCurrentTenant();
        if (clientId == null || orderId == null) {
            return List.of();
        }

        List<PrintJob> jobs = printJobRepository.findByClientIdAndOrderIdAndJobKindAndStatusInOrderByCreatedAtDesc(
                clientId,
                orderId,
                parseKind(jobKind),
                List.of(
                        PrintJobStatus.PENDING,
                        PrintJobStatus.CLAIMED,
                        PrintJobStatus.LEASED,
                        PrintJobStatus.LOCAL_QUEUED,
                        PrintJobStatus.SPOOLING,
                        PrintJobStatus.RETRY,
                        PrintJobStatus.RETRY_WAIT,
                        PrintJobStatus.HELD_AMBIGUOUS,
                        PrintJobStatus.FAILED
                )
        );

        LocalDateTime now = LocalDateTime.now();
        for (PrintJob job : jobs) {
            markPrintedFields(job, now);
        }
        return printJobRepository.saveAll(jobs);
    }

    @Transactional
    public PrintJob markFailed(UUID id, String message) {
        PrintJob job = getJob(id);
        job.setStatus(PrintJobStatus.FAILED);
        job.setErrorMessage(message == null || message.isBlank() ? "Printing failed" : message);
        job.setNextAttemptAt(null);
        return printJobRepository.save(job);
    }

    @Transactional
    public PrintJob retry(UUID id) {
        PrintJob job = getJob(id);
        job.setStatus(PrintJobStatus.RETRY);
        job.setNextAttemptAt(LocalDateTime.now());
        job.setErrorMessage(null);
        return printJobRepository.save(job);
    }

    @Transactional(readOnly = true)
    public List<PrintJob> recent() {
        UUID clientId = TenantContext.getCurrentTenant();
        if (clientId == null) {
            return List.of();
        }
        return printJobRepository.findRecentValid(clientId);
    }

    public Map<String, Object> payload(PrintJob job) {
        try {
            if (job == null || job.getPayloadJson() == null || job.getPayloadJson().isBlank()) {
                return Map.of();
            }
            return objectMapper.readValue(job.getPayloadJson(), new TypeReference<>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    public Map<String, Object> describe(PrintJob job) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", job.getId());
        dto.put("orderId", job.getOrderId());
        dto.put("offlineOperationId", job.getOfflineOperationId());
        dto.put("sourceOperationId", job.getSourceOperationId());
        dto.put("sourceTerminalId", job.getSourceTerminalId());
        dto.put("targetTerminalId", job.getTargetTerminalId());
        dto.put("claimedByTerminalId", job.getClaimedByTerminalId());
        dto.put("leasedByStationId", job.getLeasedByStationId());
        dto.put("leaseToken", job.getLeaseToken());
        dto.put("leaseExpiresAt", job.getLeaseExpiresAt());
        dto.put("jobKind", job.getJobKind() == null ? "bill" : job.getJobKind().name().toLowerCase());
        dto.put("status", job.getStatus() == null ? "PENDING" : job.getStatus().name());
        dto.put("attempts", job.getAttempts() == null ? 0 : job.getAttempts());
        dto.put("errorMessage", job.getErrorMessage());
        dto.put("failureCode", job.getFailureCode());
        dto.put("spoolJobId", job.getSpoolJobId());
        dto.put("printerProfileId", job.getPrinterProfileId());
        dto.put("routeId", job.getRouteId());
        dto.put("outputFormat", job.getOutputFormat());
        dto.put("ambiguous", Boolean.TRUE.equals(job.getAmbiguous()));
        dto.put("payload", payload(job));
        dto.put("createdAt", job.getCreatedAt());
        dto.put("updatedAt", job.getUpdatedAt());
        dto.put("attemptHistory", printJobAttemptRepository.findAllByPrintJobIdOrderByCreatedAtAsc(job.getId()).stream()
                .map(attempt -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("attempt", attempt.getAttemptNumber());
                    item.put("status", attempt.getStatus());
                    item.put("message", attempt.getMessage());
                    item.put("failureCode", attempt.getFailureCode());
                    item.put("spoolJobId", attempt.getSpoolJobId());
                    item.put("createdAt", attempt.getCreatedAt());
                    return item;
                })
                .toList());
        return dto;
    }

    private PrintJob createJob(Order order, PrintJobKind kind, String reason, String dedupeKey, UUID clientId) {
        try {
            String payloadJson = objectMapper.writeValueAsString(Map.of(
                    "order", orderSnapshot(order),
                    "jobKind", kind.name().toLowerCase(),
                    "reason", reason == null ? "auto" : reason
            ));

            PrintJob job = PrintJob.builder()
                    .orderId(order.getId())
                    .offlineOperationId(order.getSourceOfflineId())
                    .sourceOperationId(order.getSourceOperationId())
                    .sourceTerminalId(order.getSourceTerminalId() != null ? order.getSourceTerminalId() : order.getTerminalId())
                    .sourceDeviceId(order.getSourceDeviceId())
                    .jobKind(kind)
                    .status(PrintJobStatus.PENDING)
                    .dedupeKey(dedupeKey)
                    .payloadJson(payloadJson)
                    .build();
            job.setClientId(clientId);
            job.setOrgId(order.getOrgId());
            return printJobRepository.save(job);
        } catch (DataIntegrityViolationException ex) {
            return printJobRepository.findByClientIdAndDedupeKey(clientId, dedupeKey)
                    .orElseThrow(() -> ex);
        } catch (Exception ex) {
            log.warn("Unable to create print job for order {}", order.getId(), ex);
            throw new BusinessException("Unable to create print job");
        }
    }

    @Transactional
    public PrintJob enqueueKotEditJob(Order order, List<OrderLine> addedLines, List<OrderLine> removedLines, String reason) {
        if (order == null || order.getId() == null) {
            throw new BusinessException("Invalid print job request");
        }

        UUID clientId = order.getClientId() != null ? order.getClientId() : TenantContext.getCurrentTenant();
        if (clientId == null) {
            throw new BusinessException("Print job tenant is missing");
        }
        String dedupeKey = buildDedupeKey(order, PrintJobKind.KOT, reason);

        return printJobRepository.findByClientIdAndDedupeKey(clientId, dedupeKey)
                .orElseGet(() -> createKotEditJob(order, addedLines, removedLines, reason, dedupeKey, clientId));
    }

    private PrintJob createKotEditJob(Order order, List<OrderLine> addedLines, List<OrderLine> removedLines, String reason, String dedupeKey, UUID clientId) {
        try {
            Map<String, Object> orderSnap = orderSnapshot(order);
            List<Map<String, Object>> addedSnaps = addedLines == null
                    ? List.of()
                    : addedLines.stream().map(this::lineSnapshot).toList();
            orderSnap.put("lines", addedSnaps);
            orderSnap.put("orderLines", addedSnaps);
            orderSnap.put("order_items", addedSnaps);

            List<Map<String, Object>> removedSnaps = removedLines == null
                    ? List.of()
                    : removedLines.stream().map(this::lineSnapshot).toList();
            orderSnap.put("removed_items", removedSnaps);
            orderSnap.put("removedItems", removedSnaps);
            orderSnap.put("is_edited", true);
            orderSnap.put("isEdited", true);

            String payloadJson = objectMapper.writeValueAsString(Map.of(
                    "order", orderSnap,
                    "jobKind", "kot",
                    "reason", reason == null ? "auto" : reason
            ));

            PrintJob job = PrintJob.builder()
                    .orderId(order.getId())
                    .offlineOperationId(order.getSourceOfflineId())
                    .sourceOperationId(order.getSourceOperationId())
                    .sourceTerminalId(order.getSourceTerminalId() != null ? order.getSourceTerminalId() : order.getTerminalId())
                    .sourceDeviceId(order.getSourceDeviceId())
                    .jobKind(PrintJobKind.KOT)
                    .status(PrintJobStatus.PENDING)
                    .dedupeKey(dedupeKey)
                    .payloadJson(payloadJson)
                    .build();
            job.setClientId(clientId);
            job.setOrgId(order.getOrgId());
            return printJobRepository.save(job);
        } catch (DataIntegrityViolationException ex) {
            return printJobRepository.findByClientIdAndDedupeKey(clientId, dedupeKey)
                    .orElseThrow(() -> ex);
        } catch (Exception ex) {
            log.warn("Unable to create print job for order {}", order.getId(), ex);
            throw new BusinessException("Unable to create print job");
        }
    }

    private PrintJob getJob(UUID id) {
        UUID clientId = TenantContext.getCurrentTenant();
        return printJobRepository.findById(id)
                .filter(job -> clientId == null || clientId.equals(job.getClientId()))
                .orElseThrow(() -> new BusinessException("Print job not found"));
    }

    private void markPrintedFields(PrintJob job, LocalDateTime now) {
        job.setStatus(PrintJobStatus.PRINTED);
        job.setPrintedAt(now);
        job.setErrorMessage(null);
        job.setNextAttemptAt(null);
    }

    private Map<String, Object> orderSnapshot(Order order) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", order.getId());
        out.put("orderNo", order.getOrderNo());
        out.put("order_no", order.getOrderNo());
        out.put("invoiceNo", order.getInvoiceNo());
        out.put("invoice_no", order.getInvoiceNo());
        out.put("paymentNo", order.getPaymentNo());
        out.put("payment_no", order.getPaymentNo());
        out.put("orderStatus", order.getOrderStatus());
        out.put("order_status", order.getOrderStatus());
        out.put("paymentStatus", order.getPaymentStatus());
        out.put("payment_status", order.getPaymentStatus());
        out.put("orderType", order.getOrderType());
        out.put("order_type", order.getOrderType());
        out.put("fulfillmentType", order.getFulfillmentType());
        out.put("fulfillment_type", order.getFulfillmentType());
        out.put("tableNumber", order.getTableNumber());
        out.put("table_number", order.getTableNumber());
        out.put("tableId", order.getTableId());
        out.put("table_id", order.getTableId());
        out.put("orderDate", order.getOrderDate());
        out.put("order_date", order.getOrderDate());
        out.put("totalAmount", order.getTotalAmount());
        out.put("total_amount", order.getTotalAmount());
        out.put("grandTotal", order.getGrandTotal());
        out.put("grand_total", order.getGrandTotal());
        out.put("totalTaxAmount", order.getTotalTaxAmount());
        out.put("total_tax_amount", order.getTotalTaxAmount());
        out.put("totalDiscountAmount", order.getTotalDiscountAmount());
        out.put("total_discount_amount", order.getTotalDiscountAmount());
        List<Map<String, Object>> lines = order.getLines() == null
                ? List.of()
                : order.getLines().stream().map(this::lineSnapshot).toList();
        out.put("lines", lines);
        out.put("orderLines", lines);
        out.put("order_items", lines);
        return out;
    }

    private Map<String, Object> lineSnapshot(OrderLine line) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", line.getId());
        out.put("productId", line.getProductId());
        out.put("product_id", line.getProductId());
        out.put("variantId", line.getVariantId());
        out.put("variant_id", line.getVariantId());
        out.put("productName", line.getProductName());
        out.put("product_name", line.getProductName());
        out.put("name", line.getProductName());
        out.put("categoryName", line.getCategoryName());
        out.put("category_name", line.getCategoryName());
        out.put("category", line.getCategoryName());
        out.put("quantity", line.getQuantity());
        out.put("qty", line.getQuantity());
        out.put("unitPrice", line.getUnitPrice());
        out.put("unit_price", line.getUnitPrice());
        out.put("price", line.getUnitPrice());
        out.put("lineTotal", line.getLineTotal());
        out.put("line_total", line.getLineTotal());
        out.put("taxAmount", line.getTaxAmount());
        out.put("tax_amount", line.getTaxAmount());
        out.put("discountAmount", line.getDiscountAmount());
        out.put("discount_amount", line.getDiscountAmount());
        out.put("isPackagedGood", line.getIsPackagedGood());
        out.put("is_packaged_good", line.getIsPackagedGood());
        return out;
    }

    private String buildDedupeKey(Order order, PrintJobKind kind, String reason) {
        String revision = String.valueOf(order.getRevisionNumber() == null ? 0 : order.getRevisionNumber());
        String base = order.getId() + ":" + kind.name() + ":" + revision;
        if ("manual".equalsIgnoreCase(reason)) {
            return base + ":manual";
        }
        return base + ":auto";
    }

    private PrintJobKind parseKind(String value) {
        if (value == null || value.isBlank()) {
            return PrintJobKind.BILL;
        }
        if ("kot".equalsIgnoreCase(value)) {
            return PrintJobKind.KOT;
        }
        if ("invoice".equalsIgnoreCase(value)) {
            return PrintJobKind.INVOICE;
        }
        return PrintJobKind.BILL;
    }
}
