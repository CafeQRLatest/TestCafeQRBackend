package com.restaurant.pos.order.service;

import com.restaurant.pos.common.tenant.TenantContext;
import com.restaurant.pos.common.util.SecurityUtils;
import com.restaurant.pos.invoice.domain.Invoice;
import com.restaurant.pos.invoice.repository.InvoiceRepository;
import com.restaurant.pos.order.domain.Order;
import com.restaurant.pos.order.domain.OrderLine;
import com.restaurant.pos.order.domain.OrderType;
import com.restaurant.pos.order.domain.Payment;
import com.restaurant.pos.order.dto.report.*;
import com.restaurant.pos.order.repository.OrderRepository;
import com.restaurant.pos.order.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private final OrderRepository orderRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    // ─── Sales Summary ──────────────────────────────────────────────────────

    public SalesSummaryDto getSalesSummary(Instant from, Instant to) {
        List<Order> orders = fetchSaleOrders(from, to);

        long totalOrders = orders.size();
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;
        BigDecimal grandTotal = BigDecimal.ZERO;
        long itemsSold = 0;

        for (Order o : orders) {
            totalRevenue = totalRevenue.add(safe(o.getTotalAmount()));
            totalTax = totalTax.add(safe(o.getTotalTaxAmount()));
            totalDiscount = totalDiscount.add(safe(o.getTotalDiscountAmount()));
            grandTotal = grandTotal.add(safe(o.getGrandTotal()));

            if (o.getLines() != null) {
                for (OrderLine line : o.getLines()) {
                    if (line.isActive()) {
                        itemsSold += safe(line.getQuantity()).longValue();
                    }
                }
            }
        }

        BigDecimal avg = totalOrders > 0
                ? grandTotal.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return SalesSummaryDto.builder()
                .totalOrders(totalOrders)
                .totalRevenue(totalRevenue)
                .avgOrderValue(avg)
                .itemsSold(itemsSold)
                .totalTax(totalTax)
                .totalDiscount(totalDiscount)
                .grandTotal(grandTotal)
                .build();
    }

    // ─── Sales Orders (with lines) ──────────────────────────────────────────

    public List<OrderReportDto> getSalesOrders(Instant from, Instant to) {
        List<Order> orders = fetchSaleOrders(from, to);

        return orders.stream().map(o -> {
            List<OrderReportDto.OrderLineReportDto> lines = (o.getLines() == null) ? List.of()
                    : o.getLines().stream()
                        .filter(OrderLine::isActive)
                        .map(l -> OrderReportDto.OrderLineReportDto.builder()
                                .productId(l.getProductId())
                                .productName(l.getProductName())
                                .categoryName(l.getCategoryName())
                                .quantity(safe(l.getQuantity()))
                                .unitPrice(safe(l.getUnitPrice()))
                                .taxRate(safe(l.getTaxRate()))
                                .taxAmount(safe(l.getTaxAmount()))
                                .discountAmount(safe(l.getDiscountAmount()))
                                .lineTotal(safe(l.getLineTotal()))
                                .build())
                        .collect(Collectors.toList());

            Instant od = o.getOrderDate();
            return OrderReportDto.builder()
                    .id(o.getId())
                    .orderNo(o.getOrderNo())
                    .invoiceNo(o.getInvoiceNo())
                    .paymentNo(o.getPaymentNo())
                    .orderStatus(o.getOrderStatus())
                    .paymentStatus(o.getPaymentStatus())
                    .fulfillmentType(o.getFulfillmentType())
                    .tableNumber(o.getTableNumber())
                    .customerName(o.getCustomerName())
                    .customerPhone(o.getCustomerPhone())
                    .totalAmount(safe(o.getTotalAmount()))
                    .totalTaxAmount(safe(o.getTotalTaxAmount()))
                    .totalDiscountAmount(safe(o.getTotalDiscountAmount()))
                    .grandTotal(safe(o.getGrandTotal()))
                    .orderDate(od != null ? LocalDateTime.ofInstant(od, IST) : null)
                    .createdAt(o.getCreatedAt())
                    .lines(lines)
                    .build();
        }).collect(Collectors.toList());
    }

    // ─── Item-wise Sales ────────────────────────────────────────────────────

    public List<ItemSalesDto> getItemWiseSales(Instant from, Instant to) {
        List<Order> orders = fetchSaleOrders(from, to);

        Map<String, BigDecimal[]> itemMap = new LinkedHashMap<>();

        for (Order o : orders) {
            if (o.getLines() == null) continue;
            for (OrderLine line : o.getLines()) {
                if (!line.isActive()) continue;
                String name = line.getProductName() != null ? line.getProductName() : "Unknown Item";
                String cat = line.getCategoryName() != null ? line.getCategoryName() : "Uncategorized";
                String key = name + "|||" + cat;

                itemMap.computeIfAbsent(key, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                BigDecimal[] vals = itemMap.get(key);
                vals[0] = vals[0].add(safe(line.getQuantity()));
                vals[1] = vals[1].add(safe(line.getLineTotal()));
            }
        }

        return itemMap.entrySet().stream()
                .map(e -> {
                    String[] parts = e.getKey().split("\\|\\|\\|", 2);
                    return ItemSalesDto.builder()
                            .productName(parts[0])
                            .categoryName(parts.length > 1 ? parts[1] : "Uncategorized")
                            .quantitySold(e.getValue()[0])
                            .revenue(e.getValue()[1])
                            .build();
                })
                .sorted((a, b) -> b.getRevenue().compareTo(a.getRevenue()))
                .collect(Collectors.toList());
    }

    // ─── Payment Breakdown ──────────────────────────────────────────────────

    public List<PaymentBreakdownDto> getPaymentBreakdown(Instant from, Instant to) {
        List<Order> orders = fetchSaleOrders(from, to);

        BigDecimal totalRevenue = BigDecimal.ZERO;
        Map<String, BigDecimal[]> payMethodMap = new LinkedHashMap<>();

        for (Order o : orders) {
            totalRevenue = totalRevenue.add(safe(o.getGrandTotal()));
            List<Payment> payments = paymentRepository.findByOrderId(o.getId());
            if (payments.isEmpty()) {
                String fallback = "UNASSIGNED";
                payMethodMap.computeIfAbsent(fallback, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                payMethodMap.get(fallback)[0] = payMethodMap.get(fallback)[0].add(BigDecimal.ONE);
                payMethodMap.get(fallback)[1] = payMethodMap.get(fallback)[1].add(safe(o.getGrandTotal()));
            } else {
                for (Payment p : payments) {
                    String pm = p.getPaymentMethod() != null ? p.getPaymentMethod().toUpperCase() : "UNKNOWN";
                    payMethodMap.computeIfAbsent(pm, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                    payMethodMap.get(pm)[0] = payMethodMap.get(pm)[0].add(BigDecimal.ONE);
                    payMethodMap.get(pm)[1] = payMethodMap.get(pm)[1].add(safe(p.getAmountPaid()));
                }
            }
        }

        BigDecimal finalTotal = totalRevenue;
        return payMethodMap.entrySet().stream()
                .map(e -> PaymentBreakdownDto.builder()
                        .paymentMethod(e.getKey())
                        .orderCount(e.getValue()[0].longValue())
                        .totalAmount(e.getValue()[1])
                        .percentage(finalTotal.compareTo(BigDecimal.ZERO) > 0
                                ? e.getValue()[1].multiply(BigDecimal.valueOf(100)).divide(finalTotal, 1, RoundingMode.HALF_UP)
                                : BigDecimal.ZERO)
                        .build())
                .sorted((a, b) -> b.getTotalAmount().compareTo(a.getTotalAmount()))
                .collect(Collectors.toList());
    }

    // ─── Tax Summary ────────────────────────────────────────────────────────

    public List<TaxSummaryDto> getTaxSummary(Instant from, Instant to) {
        List<Order> orders = fetchSaleOrders(from, to);

        Map<BigDecimal, BigDecimal[]> taxMap = new TreeMap<>();

        for (Order o : orders) {
            if (o.getLines() == null) continue;
            for (OrderLine line : o.getLines()) {
                if (!line.isActive()) continue;
                BigDecimal rate = safe(line.getTaxRate());
                BigDecimal taxable = safe(line.getLineTotal()).subtract(safe(line.getTaxAmount()));
                BigDecimal tax = safe(line.getTaxAmount());

                taxMap.computeIfAbsent(rate, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
                BigDecimal[] vals = taxMap.get(rate);
                vals[0] = vals[0].add(taxable);
                vals[1] = vals[1].add(tax);
                vals[2] = vals[2].add(BigDecimal.ONE);
            }
        }

        return taxMap.entrySet().stream()
                .map(e -> {
                    BigDecimal halfTax = e.getValue()[1].divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
                    return TaxSummaryDto.builder()
                            .taxRate(e.getKey())
                            .taxableAmount(e.getValue()[0])
                            .cgst(halfTax)
                            .sgst(halfTax)
                            .totalTax(e.getValue()[1])
                            .lineCount(e.getValue()[2].longValue())
                            .build();
                })
                .collect(Collectors.toList());
    }

    // ─── Hourly Sales ───────────────────────────────────────────────────────

    public List<HourlySalesDto> getHourlySales(Instant from, Instant to) {
        List<Order> orders = fetchSaleOrders(from, to);

        Map<Integer, BigDecimal[]> hourlyMap = new TreeMap<>();

        for (Order o : orders) {
            Instant orderTime = o.getOrderDate() != null ? o.getOrderDate() : o.getCreatedAt().atZone(IST).toInstant();
            int hour = LocalDateTime.ofInstant(orderTime, IST).getHour();
            hourlyMap.computeIfAbsent(hour, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal[] vals = hourlyMap.get(hour);
            vals[0] = vals[0].add(BigDecimal.ONE);
            vals[1] = vals[1].add(safe(o.getGrandTotal()));
        }

        return hourlyMap.entrySet().stream()
                .map(e -> HourlySalesDto.builder()
                        .hour(e.getKey())
                        .hourLabel(String.format("%02d:00", e.getKey()))
                        .orderCount(e.getValue()[0].longValue())
                        .totalAmount(e.getValue()[1])
                        .build())
                .collect(Collectors.toList());
    }

    // ─── Invoices (with filter) ─────────────────────────────────────────────

    public List<InvoiceReportDto> getInvoices(Instant from, Instant to, String filterType) {
        UUID clientId = TenantContext.getCurrentTenant();
        UUID orgId = SecurityUtils.isSuperAdmin() ? null : TenantContext.getCurrentOrg();

        // invoiceDate is LocalDateTime — convert Instant to LocalDateTime for comparison
        LocalDateTime ldFrom = from != null ? LocalDateTime.ofInstant(from, IST) : null;
        LocalDateTime ldTo = to != null ? LocalDateTime.ofInstant(to, IST) : null;

        List<Invoice> allInvoices = invoiceRepository.findAll((root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get("clientId"), clientId));
            if (orgId != null) {
                predicates.add(cb.equal(root.get("orgId"), orgId));
            }
            if (ldFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("invoiceDate"), ldFrom));
            }
            if (ldTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("invoiceDate"), ldTo));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        });

        // Apply filter
        List<Invoice> filtered;
        String ft = filterType != null ? filterType.toUpperCase() : "ALL";
        if ("PAID".equals(ft)) {
            filtered = allInvoices.stream()
                    .filter(i -> "PAID".equalsIgnoreCase(i.getStatus()) && !Boolean.TRUE.equals(i.getIsCredit()))
                    .collect(Collectors.toList());
        } else if ("CREDIT".equals(ft)) {
            filtered = allInvoices.stream()
                    .filter(i -> Boolean.TRUE.equals(i.getIsCredit()) || "UNPAID".equalsIgnoreCase(i.getStatus()))
                    .filter(i -> !"VOID".equalsIgnoreCase(i.getStatus()))
                    .collect(Collectors.toList());
        } else if ("VOIDED".equals(ft)) {
            filtered = allInvoices.stream()
                    .filter(i -> "VOID".equalsIgnoreCase(i.getStatus()))
                    .collect(Collectors.toList());
        } else {
            filtered = allInvoices;
        }

        return filtered.stream().map(inv -> {
            String paymentMethod = null;
            String paymentNo = null;
            String customerName = null;

            if (inv.getOrderId() != null) {
                List<Payment> payments = paymentRepository.findByOrderId(inv.getOrderId());
                if (!payments.isEmpty()) {
                    Payment latest = payments.get(payments.size() - 1);
                    paymentMethod = latest.getPaymentMethod();
                    paymentNo = latest.getReferenceNo();
                }
                orderRepository.findById(inv.getOrderId()).ifPresent(order -> {
                    // can't assign to local var directly in lambda, handled below
                });
                Optional<Order> linkedOrder = orderRepository.findById(inv.getOrderId());
                if (linkedOrder.isPresent()) {
                    customerName = linkedOrder.get().getCustomerName();
                }
            }

            return InvoiceReportDto.builder()
                    .id(inv.getId())
                    .orderId(inv.getOrderId())
                    .invoiceNo(inv.getInvoiceNo())
                    .invoiceType(inv.getInvoiceType() != null ? inv.getInvoiceType().name() : null)
                    .status(inv.getStatus())
                    .docStatus(inv.getDocStatus())
                    .totalAmount(safe(inv.getTotalAmount()))
                    .amountDue(safe(inv.getAmountDue()))
                    .customerName(customerName)
                    .paymentMethod(paymentMethod)
                    .paymentNo(paymentNo)
                    .invoiceDate(inv.getInvoiceDate())
                    .description(inv.getDescription())
                    .reference(inv.getReference())
                    .originalInvoiceId(inv.getOriginalInvoiceId())
                    .build();
        }).collect(Collectors.toList());
    }

    // ─── Profit & Loss ──────────────────────────────────────────────────────

    public ProfitLossDto getProfitLoss(Instant from, Instant to) {
        SalesSummaryDto sales = getSalesSummary(from, to);

        UUID clientId = TenantContext.getCurrentTenant();
        UUID orgId = SecurityUtils.isSuperAdmin() ? null : TenantContext.getCurrentOrg();

        // Fetch expense orders — orderDate is Instant
        List<Order> expenseOrders = orderRepository.findAll((root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get("clientId"), clientId));
            if (orgId != null) {
                predicates.add(cb.equal(root.get("orgId"), orgId));
            }
            predicates.add(cb.equal(root.get("orderType"), OrderType.EXPENSE));
            predicates.add(cb.notEqual(root.get("orderStatus"), "CANCELLED"));
            predicates.add(cb.equal(root.get("isactive"), "Y"));
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("orderDate"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("orderDate"), to));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        });

        BigDecimal totalExpenses = BigDecimal.ZERO;
        for (Order exp : expenseOrders) {
            totalExpenses = totalExpenses.add(safe(exp.getGrandTotal()));
        }

        // Credit outstanding — isCredit is Boolean, no date filter needed
        List<Invoice> creditInvoices = invoiceRepository.findAll((root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get("clientId"), clientId));
            if (orgId != null) {
                predicates.add(cb.equal(root.get("orgId"), orgId));
            }
            predicates.add(cb.equal(root.get("isCredit"), true));
            predicates.add(cb.notEqual(root.get("status"), "VOID"));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        });

        BigDecimal creditOutstanding = BigDecimal.ZERO;
        for (Invoice ci : creditInvoices) {
            creditOutstanding = creditOutstanding.add(safe(ci.getAmountDue()));
        }

        BigDecimal netProfit = sales.getGrandTotal().subtract(totalExpenses);
        BigDecimal netCashProfit = netProfit.subtract(creditOutstanding);

        return ProfitLossDto.builder()
                .grossSales(sales.getGrandTotal())
                .totalTax(sales.getTotalTax())
                .totalExpenses(totalExpenses)
                .netProfit(netProfit)
                .creditOutstanding(creditOutstanding)
                .netCashProfit(netCashProfit)
                .build();
    }

    // ─── Void Invoice ───────────────────────────────────────────────────────

    @Transactional
    public Invoice voidInvoice(UUID invoiceId, String reason) {
        UUID clientId = TenantContext.getCurrentTenant();
        Invoice invoice;
        if (SecurityUtils.isSuperAdmin()) {
            invoice = invoiceRepository.findByIdAndClientId(invoiceId, clientId)
                    .orElseThrow(() -> new com.restaurant.pos.common.exception.ResourceNotFoundException("Invoice not found"));
        } else {
            invoice = invoiceRepository.findByIdAndClientIdAndOrgId(invoiceId, clientId, TenantContext.getCurrentOrg())
                    .orElseThrow(() -> new com.restaurant.pos.common.exception.ResourceNotFoundException("Invoice not found"));
        }

        if ("VOID".equalsIgnoreCase(invoice.getStatus())) {
            throw new IllegalStateException("Invoice is already voided");
        }

        invoice.setStatus("VOID");
        invoice.setDocStatus("VOIDED");
        if (reason != null && !reason.isBlank()) {
            invoice.setDescription("void: " + reason + (invoice.getDescription() != null ? " | " + invoice.getDescription() : ""));
        }

        if (invoice.getOrderId() != null) {
            orderRepository.findById(invoice.getOrderId()).ifPresent(order -> {
                order.setOrderStatus("CANCELLED");
                order.setDescription("Voided via invoice: " + (reason != null ? reason : "No reason provided"));
                orderRepository.save(order);
            });
        }

        return invoiceRepository.save(invoice);
    }

    // ─── Private Helpers ────────────────────────────────────────────────────

    private List<Order> fetchSaleOrders(Instant from, Instant to) {
        UUID clientId = TenantContext.getCurrentTenant();
        UUID orgId = SecurityUtils.isSuperAdmin() ? null : TenantContext.getCurrentOrg();

        return orderRepository.findAll((root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get("clientId"), clientId));
            if (orgId != null) {
                predicates.add(cb.equal(root.get("orgId"), orgId));
            }
            predicates.add(cb.equal(root.get("orderType"), OrderType.SALE));
            predicates.add(cb.notEqual(root.get("orderStatus"), "CANCELLED"));
            predicates.add(cb.equal(root.get("isactive"), "Y"));
            // orderDate is Instant — compare directly with Instant params
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("orderDate"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("orderDate"), to));
            }
            query.orderBy(cb.desc(root.get("orderDate")));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        });
    }

    private BigDecimal safe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
