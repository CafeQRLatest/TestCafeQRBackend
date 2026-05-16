package com.restaurant.pos.accounting.service;

import com.restaurant.pos.accounting.domain.*;
import com.restaurant.pos.accounting.dto.*;
import com.restaurant.pos.accounting.repository.*;
import com.restaurant.pos.category.domain.ExpenseCategory;
import com.restaurant.pos.category.repository.ExpenseCategoryRepository;
import com.restaurant.pos.common.exception.BusinessException;
import com.restaurant.pos.common.exception.ResourceNotFoundException;
import com.restaurant.pos.common.tenant.TenantContext;
import com.restaurant.pos.inventory.domain.StockAdjustment;
import com.restaurant.pos.inventory.domain.StockAdjustmentLine;
import com.restaurant.pos.inventory.repository.StockAdjustmentRepository;
import com.restaurant.pos.invoice.domain.Invoice;
import com.restaurant.pos.invoice.domain.InvoiceType;
import com.restaurant.pos.invoice.repository.InvoiceRepository;
import com.restaurant.pos.order.domain.*;
import com.restaurant.pos.order.repository.OrderRepository;
import com.restaurant.pos.order.repository.PaymentRepository;
import com.restaurant.pos.order.repository.PaymentSplitRepository;
import com.restaurant.pos.product.domain.Product;
import com.restaurant.pos.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountingPostingService {

    private static final int MAX_BACKFILL_DAYS = 366;
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final AccountingService accountingService;
    private final AccountingDefaultsService defaultsService;
    private final AccountingAccountRepository accountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final AccountingPostingJobRepository postingJobRepository;
    private final OrderRepository orderRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentSplitRepository paymentSplitRepository;
    private final ProductRepository productRepository;
    private final ExpenseCategoryRepository expenseCategoryRepository;
    private final StockAdjustmentRepository stockAdjustmentRepository;

    public PostingOutcome postInvoice(Order order, Invoice invoice) {
        if (invoice == null || invoice.getId() == null || isVoid(invoice.getStatus()) || isInactive(invoice.getIsactive())) {
            return PostingOutcome.SKIPPED;
        }
        return safePost(invoiceSourceType(invoice), invoice.getId(), () -> buildInvoiceJournal(order, invoice));
    }

    public PostingOutcome postPayment(Order order, Payment payment) {
        if (payment == null || payment.getId() == null || isVoid(payment.getStatus()) || isInactive(payment.getIsactive())) {
            return PostingOutcome.SKIPPED;
        }
        return safePost(paymentSourceType(payment), payment.getId(), () -> buildPaymentJournal(order, payment));
    }

    public PostingOutcome postSaleCogs(Order order) {
        if (order == null || order.getId() == null || order.getOrderType() != OrderType.SALE
                || !"COMPLETED".equalsIgnoreCase(order.getOrderStatus())) {
            return PostingOutcome.SKIPPED;
        }
        return safePost("SALE_COGS", order.getId(), () -> buildSaleCogsJournal(order));
    }

    public PostingOutcome postStockAdjustment(StockAdjustment adjustment) {
        if (adjustment == null || adjustment.getId() == null || !"COMPLETED".equalsIgnoreCase(adjustment.getStatus())) {
            return PostingOutcome.SKIPPED;
        }
        return safePost("STOCK_ADJUSTMENT", adjustment.getId(), () -> buildStockAdjustmentJournal(adjustment));
    }

    @Transactional
    public PostingOutcome reverseSource(String sourceType, UUID sourceId, String reason) {
        if (sourceType == null || sourceId == null) {
            return PostingOutcome.SKIPPED;
        }
        UUID clientId = requireClient();
        UUID orgId = TenantContext.getCurrentOrg();
        Optional<JournalEntry> original = journalEntryRepository
                .findByClientIdAndOrgIdAndSourceTypeAndSourceId(clientId, orgId, sourceType, sourceId)
                .filter(entry -> entry.getStatus() == JournalStatus.POSTED);
        if (original.isEmpty()) {
            return PostingOutcome.SKIPPED;
        }
        String reversalSourceType = sourceType + "_REV";
        UUID reversalSourceId = original.get().getId();
        return safePost(reversalSourceType, reversalSourceId, () -> buildReversalJournal(original.get(), reason));
    }

    public PostingOutcome reverseInvoice(Invoice invoice, String reason) {
        return invoice == null ? PostingOutcome.SKIPPED : reverseSource(invoiceSourceType(invoice), invoice.getId(), reason);
    }

    public PostingOutcome reversePayment(Payment payment, String reason) {
        return payment == null ? PostingOutcome.SKIPPED : reverseSource(paymentSourceType(payment), payment.getId(), reason);
    }

    @Transactional(readOnly = true)
    public List<AccountingPostingErrorDto> getPostingErrors() {
        UUID clientId = requireClient();
        UUID orgId = TenantContext.getCurrentOrg();
        return postingJobRepository.findByClientIdAndOrgIdAndStatusOrderByUpdatedAtDesc(clientId, orgId, "FAILED").stream()
                .map(job -> AccountingPostingErrorDto.builder()
                        .id(job.getId())
                        .sourceType(job.getSourceType())
                        .sourceId(job.getSourceId())
                        .status(job.getStatus())
                        .attemptCount(job.getAttemptCount())
                        .lastError(job.getLastError())
                        .updatedAt(job.getUpdatedAt())
                        .build())
                .toList();
    }

    @Transactional
    public AccountingPostingJob retryPosting(UUID jobId) {
        AccountingPostingJob job = postingJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Accounting posting job not found"));
        if (!requireClient().equals(job.getClientId()) || !Objects.equals(TenantContext.getCurrentOrg(), job.getOrgId())) {
            throw new ResourceNotFoundException("Accounting posting job not found");
        }
        job.setStatus("PENDING");
        job.setLastError(null);
        postingJobRepository.save(job);
        retrySource(job.getSourceType(), job.getSourceId());
        return postingJobRepository.findById(jobId).orElse(job);
    }

    // NOT @Transactional: each item posts in its own independent transaction
    // to avoid statement timeout on free-tier databases during bulk backfill
    public AccountingBackfillResponse backfill(AccountingBackfillRequest request) {
        DateRange range = boundedRange(request != null ? request.getFrom() : null, request != null ? request.getTo() : null);
        Set<String> sources = normalizeSourceTypes(request != null ? request.getSourceTypes() : null);
        boolean dryRun = request != null && request.isDryRun();
        UUID clientId = requireClient();
        UUID orgId = TenantContext.getCurrentOrg();
        AccountingBackfillResponse response = AccountingBackfillResponse.builder().dryRun(dryRun).build();

        if (sources.contains("INVOICE")) {
            for (Invoice invoice : invoiceRepository.findByClientIdAndOrgIdAndInvoiceDateBetweenOrderByInvoiceDateAsc(clientId, orgId, range.from, range.to)) {
                countInvoiceBackfill(response, dryRun, invoice);
            }
        }
        if (sources.contains("PAYMENT")) {
            for (Payment payment : paymentRepository.findByClientIdAndOrgIdAndPaymentDateBetweenOrderByPaymentDateAsc(clientId, orgId, range.from, range.to)) {
                countPaymentBackfill(response, dryRun, payment);
            }
        }
        if (sources.contains("COGS")) {
            Instant fromInstant = range.from.atZone(IST).toInstant();
            Instant toInstant = range.to.atZone(IST).toInstant();
            for (Order order : orderRepository.findByClientIdAndOrgIdAndOrderDateBetweenOrderByOrderDateAsc(clientId, orgId, fromInstant, toInstant)) {
                if (order.getOrderType() == OrderType.SALE) {
                    countOutcome(response, dryRun, "SALE_COGS", order.getId(), () -> postSaleCogs(order));
                }
            }
        }
        if (sources.contains("STOCK")) {
            for (StockAdjustment adjustment : stockAdjustmentRepository.findByClientIdAndOrgIdAndAdjustmentDateBetweenOrderByAdjustmentDateAsc(clientId, orgId, range.from, range.to)) {
                countOutcome(response, dryRun, "STOCK_ADJUSTMENT", adjustment.getId(), () -> postStockAdjustment(adjustment));
            }
        }

        return response;
    }

    /**
     * Lightweight fix: finds invoices where totalAmount doesn't match the order's
     * current grandTotal (caused by discounts applied after billing), updates the
     * invoice, and re-posts the accounting entry. Only touches mismatched invoices.
     * Completes in <5 seconds even on free-tier infrastructure.
     */
    public AccountingBackfillResponse fixInvoiceDiscounts() {
        UUID clientId = requireClient();
        UUID orgId = TenantContext.getCurrentOrg();
        log.info("fixInvoiceDiscounts started | clientId={} | orgId={}", clientId, orgId);

        LocalDateTime yearStart = LocalDateTime.of(java.time.LocalDate.now().getYear(), 1, 1, 0, 0);
        LocalDateTime now = LocalDateTime.now();
        List<Invoice> allInvoices = invoiceRepository.findByClientIdAndOrgIdAndInvoiceDateBetweenOrderByInvoiceDateAsc(
                clientId, orgId, yearStart, now);

        AccountingBackfillResponse response = AccountingBackfillResponse.builder().dryRun(false).build();
        int fixed = 0;
        int scanned = 0;

        for (Invoice invoice : allInvoices) {
            scanned++;
            if (invoice.getOrderId() == null) continue;

            Optional<Order> orderOpt = resolveOrder(invoice.getOrderId());
            if (orderOpt.isEmpty()) continue;

            Order order = orderOpt.get();
            BigDecimal orderTotal = money(order.getGrandTotal());
            BigDecimal invoiceTotal = money(invoice.getTotalAmount());

            if (orderTotal.compareTo(invoiceTotal) != 0) {
                try {
                    log.info("Fixing invoice {} | was {} -> should be {} | order {}",
                            invoice.getInvoiceNo(), invoiceTotal, orderTotal, order.getOrderNo());

                    // 1. Reverse old accounting entry
                    reverseInvoice(invoice, "Invoice amount corrected: " + invoiceTotal + " -> " + orderTotal);

                    // 2. Delete the old posting job so re-post works
                    String sourceType = invoiceSourceType(invoice);
                    findPostingJob(clientId, orgId, sourceType, invoice.getId())
                            .ifPresent(postingJobRepository::delete);

                    // 3. Update invoice amount
                    invoice.setTotalAmount(orderTotal);
                    invoice.setAmountDue(BigDecimal.ZERO);
                    invoiceRepository.save(invoice);

                    // 4. Re-post with correct amount
                    postInvoice(order, invoice);
                    fixed++;
                } catch (Exception ex) {
                    log.warn("Failed to fix invoice {} | {}", invoice.getInvoiceNo(), ex.getMessage());
                }
            }
        }

        response.setScanned(scanned);
        response.setPosted(fixed);
        response.setSkipped(scanned - fixed);
        log.info("fixInvoiceDiscounts complete | scanned={} | fixed={}", scanned, fixed);
        return response;
    }

    private void retrySource(String sourceType, UUID sourceId) {
        if (sourceType == null || sourceId == null) {
            return;
        }
        if (sourceType.endsWith("_REV")) {
            journalEntryRepository.findById(sourceId).ifPresent(original -> reverseSource(original.getSourceType(), original.getSourceId(), "Retry reversal"));
            return;
        }
        if (sourceType.endsWith("INVOICE") || "VENDOR_BILL".equals(sourceType) || "EXPENSE_RECEIPT".equals(sourceType)) {
            invoiceRepository.findById(sourceId).ifPresent(invoice -> postInvoice(resolveOrder(invoice.getOrderId()).orElse(null), invoice));
            return;
        }
        if (sourceType.endsWith("PAYMENT") || "INBOUND".equals(sourceType) || "OUTBOUND".equals(sourceType)) {
            paymentRepository.findById(sourceId).ifPresent(payment -> postPayment(resolveOrder(payment.getOrderId()).orElse(null), payment));
            return;
        }
        if ("SALE_COGS".equals(sourceType)) {
            resolveOrder(sourceId).ifPresent(this::postSaleCogs);
            return;
        }
        if ("STOCK_ADJUSTMENT".equals(sourceType)) {
            stockAdjustmentRepository.findById(sourceId).ifPresent(this::postStockAdjustment);
        }
    }

    private void countInvoiceBackfill(AccountingBackfillResponse response, boolean dryRun, Invoice invoice) {
        countOutcome(response, dryRun, invoiceSourceType(invoice), invoice.getId(),
                () -> postInvoice(resolveOrder(invoice.getOrderId()).orElse(null), invoice));
    }

    private void countPaymentBackfill(AccountingBackfillResponse response, boolean dryRun, Payment payment) {
        countOutcome(response, dryRun, paymentSourceType(payment), payment.getId(),
                () -> postPayment(resolveOrder(payment.getOrderId()).orElse(null), payment));
    }

    private void countOutcome(AccountingBackfillResponse response, boolean dryRun, String sourceType, UUID sourceId, Supplier<PostingOutcome> poster) {
        response.setScanned(response.getScanned() + 1);
        if (alreadyPosted(sourceType, sourceId)) {
            response.setSkipped(response.getSkipped() + 1);
            return;
        }
        if (dryRun) {
            response.setPosted(response.getPosted() + 1);
            return;
        }
        PostingOutcome outcome = poster.get();
        if (outcome == PostingOutcome.POSTED) {
            response.setPosted(response.getPosted() + 1);
        } else if (outcome == PostingOutcome.SKIPPED) {
            response.setSkipped(response.getSkipped() + 1);
        } else if (outcome == PostingOutcome.REVERSED) {
            response.setReversed(response.getReversed() + 1);
        } else {
            response.setFailed(response.getFailed() + 1);
            response.getFailures().add(sourceType + ":" + sourceId);
        }
    }

    private PostingOutcome safePost(String sourceType, UUID sourceId, Supplier<Optional<JournalEntry>> builder) {
        if (sourceId == null || sourceType == null) {
            return PostingOutcome.SKIPPED;
        }
        UUID clientId = requireClient();
        UUID orgId = TenantContext.getCurrentOrg();
        if (alreadyPosted(sourceType, sourceId)) {
            markJobPosted(clientId, orgId, sourceType, sourceId, null);
            return PostingOutcome.SKIPPED;
        }

        AccountingPostingJob job = findPostingJob(clientId, orgId, sourceType, sourceId)
                .orElseGet(() -> {
                    AccountingPostingJob created = AccountingPostingJob.builder()
                            .sourceType(sourceType)
                            .sourceId(sourceId)
                            .status("PENDING")
                            .attemptCount(0)
                            .build();
                    created.setClientId(clientId);
                    created.setOrgId(orgId);
                    return created;
                });
        job.setAttemptCount(job.getAttemptCount() == null ? 1 : job.getAttemptCount() + 1);
        postingJobRepository.save(job);

        try {
            Optional<JournalEntry> maybeEntry = builder.get();
            if (maybeEntry.isEmpty()) {
                markJobSkipped(job);
                return PostingOutcome.SKIPPED;
            }
            JournalEntry saved = accountingService.createJournalEntry(maybeEntry.get());
            markJobPosted(clientId, orgId, sourceType, sourceId, saved.getId());
            return sourceType.endsWith("_REV") ? PostingOutcome.REVERSED : PostingOutcome.POSTED;
        } catch (Exception ex) {
            log.warn("Accounting posting failed | sourceType={} | sourceId={} | message={}", sourceType, sourceId, ex.getMessage());
            markJobFailed(job, ex);
            return PostingOutcome.FAILED;
        }
    }

    private Optional<JournalEntry> buildInvoiceJournal(Order order, Invoice invoice) {
        BigDecimal total = money(invoice.getTotalAmount());
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }
        InvoiceType type = invoice.getInvoiceType() == null ? InvoiceType.CUSTOMER_INVOICE : invoice.getInvoiceType();
        BigDecimal tax = clampTax(order != null ? order.getTotalTaxAmount() : BigDecimal.ZERO, total);
        BigDecimal base = total.subtract(tax);

        List<JournalLine> lines = new ArrayList<>();
        if (type == InvoiceType.CUSTOMER_INVOICE) {
            addLine(lines, defaultsService.resolveAccount(AccountingDefaultsService.ACCOUNTS_RECEIVABLE), total, BigDecimal.ZERO,
                    PartyType.CUSTOMER, invoice.getCustomerId(), "Customer invoice receivable");
            addLine(lines, defaultsService.resolveAccount(AccountingDefaultsService.SALES_REVENUE), BigDecimal.ZERO, base,
                    null, null, "Sales revenue");
            if (tax.compareTo(BigDecimal.ZERO) > 0) {
                addLine(lines, defaultsService.resolveAccount(AccountingDefaultsService.OUTPUT_TAX), BigDecimal.ZERO, tax,
                        null, null, "Output tax");
            }
        } else if (type == InvoiceType.VENDOR_BILL) {
            addLine(lines, defaultsService.resolveAccount(AccountingDefaultsService.INVENTORY_ASSET), base, BigDecimal.ZERO,
                    null, null, "Purchase inventory");
            if (tax.compareTo(BigDecimal.ZERO) > 0) {
                addLine(lines, defaultsService.resolveAccount(AccountingDefaultsService.INPUT_TAX), tax, BigDecimal.ZERO,
                        null, null, "Input tax");
            }
            addLine(lines, defaultsService.resolveAccount(AccountingDefaultsService.ACCOUNTS_PAYABLE), BigDecimal.ZERO, total,
                    PartyType.VENDOR, invoice.getVendorId(), "Vendor payable");
        } else {
            AccountingAccount expenseAccount = resolveExpenseAccount(invoice, order);
            addLine(lines, expenseAccount, base, BigDecimal.ZERO, null, null, "Expense");
            if (tax.compareTo(BigDecimal.ZERO) > 0) {
                addLine(lines, defaultsService.resolveAccount(AccountingDefaultsService.INPUT_TAX), tax, BigDecimal.ZERO,
                        null, null, "Input tax");
            }
            addLine(lines, defaultsService.resolveAccount(AccountingDefaultsService.ACCOUNTS_PAYABLE), BigDecimal.ZERO, total,
                    null, null, "Expense payable");
        }

        return journal(invoiceSourceType(invoice), invoice.getId(), invoice.getInvoiceDate(), invoice.getTerminalId(),
                order != null ? order.getWarehouseId() : null, order != null ? order.getCurrencyId() : null,
                "Auto posted invoice " + invoice.getInvoiceNo(), lines);
    }

    private Optional<JournalEntry> buildPaymentJournal(Order order, Payment payment) {
        BigDecimal amount = money(payment.getAmountPaid());
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }
        boolean inbound = payment.getPaymentType() == null || payment.getPaymentType() == PaymentType.INBOUND;
        List<JournalLine> lines = new ArrayList<>();
        List<PaymentSplit> splits = paymentSplitRepository.findByPaymentIdOrderByCreatedAtAsc(payment.getId());
        if (splits.isEmpty()) {
            splits = List.of(PaymentSplit.builder()
                    .paymentId(payment.getId())
                    .paymentMethod(payment.getPaymentMethod())
                    .amount(amount)
                    .build());
        }

        for (PaymentSplit split : splits) {
            BigDecimal splitAmount = money(split.getAmount());
            if (splitAmount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            AccountingAccount paymentAccount = defaultsService.resolvePaymentAccount(split.getPaymentMethod());
            if (inbound) {
                addLine(lines, paymentAccount, splitAmount, BigDecimal.ZERO, null, null, "Payment received");
            } else {
                addLine(lines, paymentAccount, BigDecimal.ZERO, splitAmount, null, null, "Payment made");
            }
        }

        if (inbound) {
            addLine(lines, defaultsService.resolveAccount(AccountingDefaultsService.ACCOUNTS_RECEIVABLE), BigDecimal.ZERO, amount,
                    PartyType.CUSTOMER, order != null ? order.getCustomerId() : null, "Receivable settled");
        } else {
            addLine(lines, defaultsService.resolveAccount(AccountingDefaultsService.ACCOUNTS_PAYABLE), amount, BigDecimal.ZERO,
                    PartyType.VENDOR, order != null ? order.getVendorId() : null, "Payable settled");
        }

        return journal(paymentSourceType(payment), payment.getId(), payment.getPaymentDate(), payment.getTerminalId(),
                order != null ? order.getWarehouseId() : null, order != null ? order.getCurrencyId() : null,
                "Auto posted payment " + payment.getReferenceNo(), lines);
    }

    private Optional<JournalEntry> buildSaleCogsJournal(Order order) {
        BigDecimal costTotal = BigDecimal.ZERO;
        if (order.getLines() != null) {
            for (OrderLine line : order.getLines()) {
                if (line == null || !line.isActive() || line.getProductId() == null) {
                    continue;
                }
                BigDecimal unitCost = resolveUnitCost(line);
                costTotal = costTotal.add(unitCost.multiply(quantity(line.getQuantity())));
            }
        }
        costTotal = money(costTotal);
        if (costTotal.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }
        List<JournalLine> lines = new ArrayList<>();
        addLine(lines, defaultsService.resolveAccount(AccountingDefaultsService.PURCHASE_COGS), costTotal, BigDecimal.ZERO,
                null, null, "Sales cost of goods sold");
        addLine(lines, defaultsService.resolveAccount(AccountingDefaultsService.INVENTORY_ASSET), BigDecimal.ZERO, costTotal,
                null, null, "Inventory consumed by sale");
        return journal("SALE_COGS", order.getId(), orderDate(order), order.getTerminalId(), order.getWarehouseId(), order.getCurrencyId(),
                "Auto posted COGS for " + order.getOrderNo(), lines);
    }

    private Optional<JournalEntry> buildStockAdjustmentJournal(StockAdjustment adjustment) {
        BigDecimal value = BigDecimal.ZERO;
        if (adjustment.getLines() != null) {
            for (StockAdjustmentLine line : adjustment.getLines()) {
                if (line == null || !"Y".equalsIgnoreCase(line.getIsactive())) {
                    continue;
                }
                value = value.add(quantity(line.getQuantityChange()).multiply(money(line.getUnitCost())));
            }
        }
        value = money(value);
        if (value.compareTo(BigDecimal.ZERO) == 0) {
            return Optional.empty();
        }
        List<JournalLine> lines = new ArrayList<>();
        BigDecimal absolute = value.abs();
        if (value.compareTo(BigDecimal.ZERO) > 0) {
            addLine(lines, defaultsService.resolveAccount(AccountingDefaultsService.INVENTORY_ASSET), absolute, BigDecimal.ZERO,
                    null, null, "Stock adjustment increase");
            addLine(lines, defaultsService.resolveAccount(AccountingDefaultsService.STOCK_ADJUSTMENT_GAIN_LOSS), BigDecimal.ZERO, absolute,
                    null, null, "Stock adjustment gain");
        } else {
            addLine(lines, defaultsService.resolveAccount(AccountingDefaultsService.STOCK_ADJUSTMENT_GAIN_LOSS), absolute, BigDecimal.ZERO,
                    null, null, "Stock adjustment loss");
            addLine(lines, defaultsService.resolveAccount(AccountingDefaultsService.INVENTORY_ASSET), BigDecimal.ZERO, absolute,
                    null, null, "Stock adjustment decrease");
        }
        return journal("STOCK_ADJUSTMENT", adjustment.getId(), adjustment.getAdjustmentDate(), null, adjustment.getWarehouseId(), null,
                "Auto posted stock adjustment " + adjustment.getAdjustmentNumber(), lines);
    }

    private Optional<JournalEntry> buildReversalJournal(JournalEntry original, String reason) {
        List<JournalLine> lines = new ArrayList<>();
        for (JournalLine originalLine : original.getLines()) {
            addLine(lines,
                    accountById(originalLine.getAccountId()),
                    money(originalLine.getCredit()),
                    money(originalLine.getDebit()),
                    originalLine.getPartyType(),
                    originalLine.getPartyId(),
                    "Reversal: " + nullToDash(originalLine.getDescription()));
        }
        return journal(original.getSourceType() + "_REV", original.getId(), LocalDateTime.now(), original.getTerminalId(),
                original.getWarehouseId(), original.getCurrencyId(),
                "Reversal for " + original.getEntryNo() + (reason == null || reason.isBlank() ? "" : " - " + reason),
                lines).map(entry -> {
                    entry.setReversalOfJournalEntryId(original.getId());
                    return entry;
                });
    }

    private Optional<JournalEntry> journal(String sourceType, UUID sourceId, LocalDateTime date, UUID terminalId, UUID warehouseId,
                                           UUID currencyId, String description, List<JournalLine> lines) {
        if (lines == null || lines.isEmpty()) {
            return Optional.empty();
        }
        JournalEntry entry = JournalEntry.builder()
                .entryNo("AUTO-" + sourceType + "-" + sourceId.toString().substring(0, 8).toUpperCase(Locale.ROOT))
                .entryDate(date != null ? date : LocalDateTime.now())
                .status(JournalStatus.POSTED)
                .sourceType(sourceType)
                .sourceId(sourceId)
                .terminalId(terminalId)
                .warehouseId(warehouseId)
                .currencyId(currencyId)
                .autoPosted(true)
                .description(description)
                .lines(new ArrayList<>())
                .build();
        lines.forEach(entry::attachLine);
        return Optional.of(entry);
    }

    private void addLine(List<JournalLine> lines, AccountingAccount account, BigDecimal debit, BigDecimal credit,
                         PartyType partyType, UUID partyId, String description) {
        BigDecimal debitAmount = money(debit);
        BigDecimal creditAmount = money(credit);
        if (debitAmount.compareTo(BigDecimal.ZERO) == 0 && creditAmount.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        lines.add(JournalLine.builder()
                .accountId(account.getId())
                .debit(debitAmount)
                .credit(creditAmount)
                .partyType(partyType)
                .partyId(partyId)
                .description(description)
                .build());
    }

    private AccountingAccount resolveExpenseAccount(Invoice invoice, Order order) {
        UUID categoryId = invoice.getExpenseCategoryId() != null
                ? invoice.getExpenseCategoryId()
                : order != null ? order.getExpenseCategoryId() : null;
        if (categoryId != null) {
            Optional<ExpenseCategory> category = expenseCategoryRepository.findById(categoryId);
            if (category.isPresent() && category.get().getAccountId() != null) {
                UUID clientId = requireClient();
                UUID orgId = TenantContext.getCurrentOrg();
                return accountRepository.findByIdAndClientIdAndOrgId(category.get().getAccountId(), clientId, orgId)
                        .orElseGet(() -> defaultsService.resolveAccount(AccountingDefaultsService.OPERATING_EXPENSES));
            }
        }
        return defaultsService.resolveAccount(AccountingDefaultsService.OPERATING_EXPENSES);
    }

    private AccountingAccount accountById(UUID accountId) {
        return accountRepository.findByIdAndClientIdAndOrgId(accountId, requireClient(), TenantContext.getCurrentOrg())
                .orElseThrow(() -> new BusinessException("Accounting account is unavailable for reversal"));
    }

    private Optional<Order> resolveOrder(UUID orderId) {
        if (orderId == null) {
            return Optional.empty();
        }
        return orderRepository.findById(orderId);
    }

    private boolean alreadyPosted(String sourceType, UUID sourceId) {
        if (sourceType == null || sourceId == null) {
            return false;
        }
        UUID clientId = requireClient();
        UUID orgId = TenantContext.getCurrentOrg();
        // Use org-agnostic check when orgId is null to avoid IS NULL mismatch
        if (orgId == null) {
            return journalEntryRepository.existsByClientIdAndSourceTypeAndSourceId(clientId, sourceType, sourceId);
        }
        return journalEntryRepository.existsByClientIdAndOrgIdAndSourceTypeAndSourceId(clientId, orgId, sourceType, sourceId);
    }

    /** Find posting job, falling back to org-agnostic query when orgId is null */
    private Optional<AccountingPostingJob> findPostingJob(UUID clientId, UUID orgId, String sourceType, UUID sourceId) {
        if (orgId != null) {
            return postingJobRepository.findByClientIdAndOrgIdAndSourceTypeAndSourceId(clientId, orgId, sourceType, sourceId);
        }
        return postingJobRepository.findByClientIdAndSourceTypeAndSourceId(clientId, sourceType, sourceId);
    }

    private void markJobPosted(UUID clientId, UUID orgId, String sourceType, UUID sourceId, UUID journalEntryId) {
        AccountingPostingJob job = findPostingJob(clientId, orgId, sourceType, sourceId)
                .orElseGet(() -> {
                    AccountingPostingJob created = AccountingPostingJob.builder()
                            .sourceType(sourceType)
                            .sourceId(sourceId)
                            .build();
                    created.setClientId(clientId);
                    created.setOrgId(orgId);
                    return created;
                });
        job.setStatus("POSTED");
        job.setLastError(null);
        job.setPostedAt(LocalDateTime.now());
        if (journalEntryId != null) {
            job.setPostedJournalEntryId(journalEntryId);
        }
        postingJobRepository.save(job);
    }

    private void markJobSkipped(AccountingPostingJob job) {
        job.setStatus("SKIPPED");
        job.setLastError(null);
        postingJobRepository.save(job);
    }

    private void markJobFailed(AccountingPostingJob job, Exception ex) {
        job.setStatus("FAILED");
        job.setLastError(ex.getMessage());
        postingJobRepository.save(job);
    }

    private String invoiceSourceType(Invoice invoice) {
        InvoiceType type = invoice.getInvoiceType() == null ? InvoiceType.CUSTOMER_INVOICE : invoice.getInvoiceType();
        return type.name();
    }

    private String paymentSourceType(Payment payment) {
        PaymentType type = payment.getPaymentType() == null ? PaymentType.INBOUND : payment.getPaymentType();
        return type == PaymentType.INBOUND ? "INBOUND_PAYMENT" : "OUTBOUND_PAYMENT";
    }

    private BigDecimal resolveUnitCost(OrderLine line) {
        return productRepository.findById(line.getProductId())
                .map(Product::getCostPrice)
                .map(this::money)
                .orElse(BigDecimal.ZERO);
    }

    private LocalDateTime orderDate(Order order) {
        Instant instant = order.getOrderDate() != null ? order.getOrderDate() : Instant.now();
        return LocalDateTime.ofInstant(instant, IST);
    }

    private BigDecimal clampTax(BigDecimal tax, BigDecimal total) {
        BigDecimal value = money(tax);
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        if (value.compareTo(total) > 0) {
            return total;
        }
        return value;
    }

    private DateRange boundedRange(LocalDateTime from, LocalDateTime to) {
        LocalDateTime resolvedTo = to != null ? to : LocalDateTime.now();
        LocalDateTime resolvedFrom = from != null ? from : resolvedTo.minusDays(31);
        if (resolvedFrom.isAfter(resolvedTo)) {
            throw new BusinessException("From date must be before to date");
        }
        if (Duration.between(resolvedFrom, resolvedTo).toDays() > MAX_BACKFILL_DAYS) {
            throw new BusinessException("Accounting backfill range cannot exceed " + MAX_BACKFILL_DAYS + " days");
        }
        return new DateRange(resolvedFrom, resolvedTo);
    }

    private Set<String> normalizeSourceTypes(Set<String> sourceTypes) {
        if (sourceTypes == null || sourceTypes.isEmpty()) {
            return new LinkedHashSet<>(List.of("INVOICE", "PAYMENT", "COGS", "STOCK"));
        }
        Set<String> normalized = new LinkedHashSet<>();
        sourceTypes.forEach(type -> {
            if (type != null && !type.isBlank()) {
                normalized.add(type.trim().toUpperCase(Locale.ROOT));
            }
        });
        return normalized.isEmpty() ? new LinkedHashSet<>(List.of("INVOICE", "PAYMENT", "COGS", "STOCK")) : normalized;
    }

    private UUID requireClient() {
        UUID clientId = TenantContext.getCurrentTenant();
        if (clientId == null) {
            throw new BusinessException("Client context is required");
        }
        return clientId;
    }

    private boolean isVoid(String status) {
        return status != null && ("VOID".equalsIgnoreCase(status) || "VOIDED".equalsIgnoreCase(status));
    }

    private boolean isInactive(String isactive) {
        return "N".equalsIgnoreCase(isactive);
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal quantity(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    public enum PostingOutcome {
        POSTED,
        SKIPPED,
        REVERSED,
        FAILED
    }

    private static final class DateRange {
        private final LocalDateTime from;
        private final LocalDateTime to;

        private DateRange(LocalDateTime from, LocalDateTime to) {
            this.from = from;
            this.to = to;
        }
    }
}
