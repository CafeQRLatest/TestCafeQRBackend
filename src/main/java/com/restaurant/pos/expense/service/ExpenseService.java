package com.restaurant.pos.expense.service;

import com.restaurant.pos.common.exception.BusinessException;
import com.restaurant.pos.common.exception.ResourceNotFoundException;
import com.restaurant.pos.common.tenant.TenantContext;
import com.restaurant.pos.common.util.SecurityUtils;
import com.restaurant.pos.accounting.service.AccountingPostingService;
import com.restaurant.pos.expense.domain.Expense;
import com.restaurant.pos.category.domain.ExpenseCategory;
import com.restaurant.pos.category.repository.ExpenseCategoryRepository;
import com.restaurant.pos.expense.dto.CreateExpenseRequest;
import com.restaurant.pos.expense.dto.ExpenseBaseRequest;
import com.restaurant.pos.expense.dto.UpdateExpenseRequest;
import com.restaurant.pos.expense.dto.ExpenseResponse;
import com.restaurant.pos.expense.dto.VoidExpenseResponse;
import com.restaurant.pos.expense.dto.ExpenseSearchCriteria;
import com.restaurant.pos.expense.idempotency.IdempotencyStore;
import com.restaurant.pos.expense.mapper.ExpenseMapper;
import com.restaurant.pos.expense.repository.ExpenseRepository;
import com.restaurant.pos.expense.spec.ExpenseSpecification;
import com.restaurant.pos.invoice.domain.Invoice;
import com.restaurant.pos.invoice.domain.InvoiceType;
import com.restaurant.pos.invoice.repository.InvoiceRepository;
import com.restaurant.pos.order.domain.Payment;
import com.restaurant.pos.order.domain.PaymentType;
import com.restaurant.pos.order.repository.PaymentRepository;
import com.restaurant.pos.purchasing.repository.CurrencyRepository;
import com.restaurant.pos.sequence.domain.DocumentType;
import com.restaurant.pos.sequence.service.DocumentSequenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing expense transactions.
 * Fully utilizing the dedicated Expense domain entity and repository
 * for FAANG-level modularity.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseService {

    private static final int EXPENSE_WRITE_TIMEOUT_SECONDS = 30;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    private final ExpenseCategoryRepository categoryRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseMapper expenseMapper;
    private final DocumentSequenceService sequenceService;
    private final AccountingPostingService accountingPostingService;
    private final CurrencyRepository currencyRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;

    private final IdempotencyStore idempotencyStore;

    // ─────────────────────────────────────────────────────────────
    // Sort Field Translation
    // ─────────────────────────────────────────────────────────────

    /**
     * Maps logical API sort fields to internal entity properties.
     * Only fields that sort meaningfully are exposed.
     */
    private static final Map<String, String> SORT_FIELD_MAP = Map.of(
            "expenseDate", "expenseDate",
            "orderDate",   "expenseDate",
            "amount",      "amount"
    );

    private Pageable translatePageable(Pageable pageable) {
        List<Sort.Order> translated = pageable.getSort().stream()
                .map(order -> {
                    String mapped = SORT_FIELD_MAP.getOrDefault(order.getProperty(), order.getProperty());
                    return order.withProperty(mapped);
                })
                .toList();
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(translated));
    }

    // ─────────────────────────────────────────────────────────────
    // CRUD Operations
    // ─────────────────────────────────────────────────────────────

    /**
     * Records a new expense transaction with idempotency protection.
     */
    @Transactional(timeout = EXPENSE_WRITE_TIMEOUT_SECONDS)
    public ExpenseResponse createExpense(String idempotencyKey, CreateExpenseRequest request) {
        boolean hasIdempotencyKey = idempotencyKey != null && !idempotencyKey.isBlank();
        ExpenseWriteScope targetScope = resolveExpenseWriteScope(request);

        // Idempotency check — prevents duplicate Expense+Invoice+Payment triplets on retries
        String cacheKey = TenantContext.getCurrentTenant() + ":" + targetScope.cacheKeyPart() + ":" + idempotencyKey;
        ExpenseResponse cached = idempotencyStore.get(cacheKey);
        if (cached != null) {
            log.info("Expense idempotency hit | keyPresent={}", hasIdempotencyKey);
            return cached;
        }

        long startedAtNanos = System.nanoTime();

        try {
            ExpenseCategory category = getOwnedExpenseCategory(request.getCategoryId(), targetScope.orgId());

            if (!category.isActive()) {
                throw new BusinessException("Cannot assign expense to an inactive category");
            }

            Expense expense = buildExpenseEntity(request, category, targetScope);

            // Generate unique sequence number for the expense
            String expenseNo = sequenceService.generateNextSequence(DocumentType.EXPENSE, targetScope.orgId());
            expense.setExpenseNo(expenseNo);

            log.info(
                    "Creating expense | categoryId={} | branchId={} | amount={} | paymentMethod={} | keyPresent={} | timeoutSeconds={}",
                    request.getCategoryId(),
                    request.getBranchId(),
                    request.getAmount(),
                    request.getPaymentMethod(),
                    hasIdempotencyKey,
                    EXPENSE_WRITE_TIMEOUT_SECONDS
            );

            // Save the standalone Expense entity
            Expense savedExpense = expenseRepository.save(expense);

            log.info(
                    "Expense created | expenseId={} | expenseNo={} | categoryId={} | branchId={} | keyPresent={}",
                    savedExpense.getId(),
                    savedExpense.getExpenseNo(),
                    category.getId(),
                    savedExpense.getOrgId(),
                    hasIdempotencyKey
            );

            // Generate unique sequence number for the invoice
            String invoiceNo = sequenceService.generateNextSequence(DocumentType.EXPENSE_RECEIPT, targetScope.orgId());

            // Construct and save the associated Invoice (with expenseId set and orderId = null)
            Invoice invoice = Invoice.builder()
                    .invoiceType(InvoiceType.EXPENSE_RECEIPT)
                    .documentKind(DocumentType.EXPENSE_RECEIPT.name())
                    .terminalId(savedExpense.getTerminalId())
                    .expenseId(savedExpense.getId())
                    .expenseCategoryId(category.getId())
                    .invoiceNo(invoiceNo)
                    .invoiceDate(toLocalDateTime(savedExpense.getExpenseDate()))
                    .totalAmount(savedExpense.getAmount())
                    .amountDue(BigDecimal.ZERO) // PAID immediately
                    .status("PAID")
                    .docStatus("COMPLETED")
                    .isPaid(true)
                    .isCredit(false)
                    .description(savedExpense.getDescription())
                    .isactive("Y")
                    .build();
            invoice.setClientId(savedExpense.getClientId());
            invoice.setOrgId(savedExpense.getOrgId());

            Invoice savedInvoice = invoiceRepository.save(invoice);

            // Generate unique sequence number for the payment
            String paymentNo = sequenceService.generateNextSequence(DocumentType.OUTBOUND_PAYMENT, targetScope.orgId());

            // Construct and save the associated Payment (with expenseId set and orderId = null)
            String normalizedMethod = normalizePaymentMethod(savedExpense.getPaymentMethod());
            Payment payment = Payment.builder()
                    .paymentType(PaymentType.OUTBOUND)
                    .documentKind(DocumentType.OUTBOUND_PAYMENT.name())
                    .terminalId(savedExpense.getTerminalId())
                    .invoiceId(savedInvoice.getId())
                    .expenseId(savedExpense.getId())
                    .expenseCategoryId(category.getId())
                    .paymentDate(toLocalDateTime(savedExpense.getExpenseDate()))
                    .paymentMethod(normalizedMethod)
                    .amountPaid(savedExpense.getAmount())
                    .changeGiven(BigDecimal.ZERO)
                    .referenceNo(paymentNo)
                    .description(savedExpense.getDescription())
                    .status("COMPLETED")
                    .docStatus("COMPLETED")
                    .isactive("Y")
                    .build();
            payment.setClientId(savedExpense.getClientId());
            payment.setOrgId(savedExpense.getOrgId());

            Payment savedPayment = paymentRepository.save(payment);

            // Post to Accounting Posting Service (null for order)
            accountingPostingService.postInvoice(null, savedInvoice);
            accountingPostingService.postPayment(null, savedPayment);

            ExpenseResponse response = expenseMapper.toExpenseResponse(savedExpense, category.getName());

            idempotencyStore.put(cacheKey, response);

            log.info(
                    "Expense created response | expenseId={} | referenceNumber={} | categoryId={} | branchId={} | amount={} | elapsedMs={} | keyPresent={}",
                    savedExpense.getId(),
                    response.getReferenceNumber(),
                    category.getId(),
                    savedExpense.getOrgId(),
                    response.getAmount(),
                    elapsedMillis(startedAtNanos),
                    hasIdempotencyKey
            );

            return response;
        } catch (RuntimeException ex) {
            log.error(
                    "Expense creation failed | categoryId={} | branchId={} | amount={} | paymentMethod={} | elapsedMs={} | keyPresent={}",
                    request.getCategoryId(),
                    request.getBranchId(),
                    request.getAmount(),
                    request.getPaymentMethod(),
                    elapsedMillis(startedAtNanos),
                    hasIdempotencyKey,
                    ex
            );
            throw ex;
        }
    }

    /**
     * Updates an existing expense using an immutability/voiding pattern.
     * Voids the old record and creates a new one to maintain audit integrity.
     */
    @Transactional(timeout = EXPENSE_WRITE_TIMEOUT_SECONDS)
    public ExpenseResponse updateExpense(UUID id, UpdateExpenseRequest request) {
        Expense oldExpense = expenseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found: " + id));
        validateExpenseAccess(oldExpense);

        if (!oldExpense.isActive()) {
            throw new BusinessException("Cannot modify a voided or inactive expense record");
        }

        ExpenseWriteScope targetScope = resolveExpenseWriteScope(request);
        ExpenseCategory category = getOwnedExpenseCategory(request.getCategoryId(), targetScope.orgId());

        String originalExpenseNo = oldExpense.getExpenseNo();
        int revision = oldExpense.getRevisionNumber() != null ? oldExpense.getRevisionNumber() : 0;
        boolean amountChanged = oldExpense.getAmount().compareTo(request.getAmount()) != 0;
        boolean scopeChanged = !Objects.equals(oldExpense.getOrgId(), targetScope.orgId());
        boolean paymentChainChanged = amountChanged || scopeChanged;

        log.info("Updating expense | id={} | amountChanged={}", id, amountChanged);

        // 1. VOID the old expense record
        String voidSuffix = "_VOID_" + revision;
        oldExpense.setExpenseNo(originalExpenseNo + voidSuffix);
        oldExpense.setStatus("VOID");
        oldExpense.setDocStatus("VOID");
        oldExpense.deactivate();
        expenseRepository.saveAndFlush(oldExpense);

        // 2. VOID the linked invoices (batched)
        List<Invoice> invoicesToVoid = invoiceRepository.findByExpenseId(id);
        invoicesToVoid.forEach(inv -> {
            accountingPostingService.reverseInvoice(inv, "Expense revised");
            inv.setInvoiceNo(inv.getInvoiceNo() + "_VOID_" + revision);
            inv.setStatus("VOID");
            inv.setDocStatus("VOID");
            inv.setIsactive("N");
        });
        invoiceRepository.saveAll(invoicesToVoid);

        // 3. Handle Payment Logic
        List<Payment> oldPayments = paymentRepository.findByExpenseId(id);

        // 4. Create NEW Expense record
        Expense newExpense = buildExpenseEntity(request, category, targetScope);
        newExpense.setExpenseNo(scopeChanged
                ? sequenceService.generateNextSequence(DocumentType.EXPENSE, targetScope.orgId())
                : originalExpenseNo);
        newExpense.setRevisionNumber(revision + 1);
        newExpense.setOriginalExpenseId(oldExpense.getId());

        if (!oldPayments.isEmpty() && !paymentChainChanged) {
            newExpense.setPaymentStatus("PENDING");
        }

        Expense savedNew = expenseRepository.save(newExpense);

        // Generate invoice number for new invoice
        String newInvoiceNo = sequenceService.generateNextSequence(DocumentType.EXPENSE_RECEIPT, targetScope.orgId());

        Invoice newInvoice = Invoice.builder()
                .invoiceType(InvoiceType.EXPENSE_RECEIPT)
                .documentKind(DocumentType.EXPENSE_RECEIPT.name())
                .terminalId(savedNew.getTerminalId())
                .expenseId(savedNew.getId())
                .expenseCategoryId(category.getId())
                .invoiceNo(newInvoiceNo)
                .invoiceDate(toLocalDateTime(savedNew.getExpenseDate()))
                .totalAmount(savedNew.getAmount())
                .amountDue(BigDecimal.ZERO)
                .status("PAID")
                .docStatus("COMPLETED")
                .isPaid(true)
                .isCredit(false)
                .description(savedNew.getDescription())
                .isactive("Y")
                .build();
        newInvoice.setClientId(savedNew.getClientId());
        newInvoice.setOrgId(savedNew.getOrgId());

        Invoice savedNewInvoice = invoiceRepository.save(newInvoice);

        if (!oldPayments.isEmpty()) {
            if (!paymentChainChanged) {
                // REUSE: Link the first payment to the new expense/invoice
                Payment oldPayment = oldPayments.get(0);
                oldPayment.setExpenseId(savedNew.getId());
                oldPayment.setInvoiceId(savedNewInvoice.getId());
                oldPayment.setExpenseCategoryId(category.getId());
                oldPayment.setOrgId(savedNew.getOrgId());
                paymentRepository.saveAndFlush(oldPayment);

                savedNew.setPaymentStatus("PAID");
                expenseRepository.save(savedNew);

                // Re-post accounting entries for new invoice and updated payment
                accountingPostingService.postInvoice(null, savedNewInvoice);
                accountingPostingService.postPayment(null, oldPayment);
            } else {
                // VOID all old payments (batched)
                oldPayments.forEach(pay -> {
                    accountingPostingService.reversePayment(pay, "Expense revised");
                    pay.setStatus("VOID");
                    pay.setDocStatus("VOID");
                    pay.setIsactive("N");
                    pay.setReferenceNo(pay.getReferenceNo() + "_VOID_" + revision);
                });
                paymentRepository.saveAll(oldPayments);
                paymentRepository.flush();

                // Create new payment
                String newPaymentNo = sequenceService.generateNextSequence(DocumentType.OUTBOUND_PAYMENT, targetScope.orgId());
                String normalizedMethod = normalizePaymentMethod(savedNew.getPaymentMethod());
                Payment newPayment = Payment.builder()
                        .paymentType(PaymentType.OUTBOUND)
                        .documentKind(DocumentType.OUTBOUND_PAYMENT.name())
                        .terminalId(savedNew.getTerminalId())
                        .invoiceId(savedNewInvoice.getId())
                        .expenseId(savedNew.getId())
                        .expenseCategoryId(category.getId())
                        .paymentDate(toLocalDateTime(savedNew.getExpenseDate()))
                        .paymentMethod(normalizedMethod)
                        .amountPaid(savedNew.getAmount())
                        .changeGiven(BigDecimal.ZERO)
                        .referenceNo(newPaymentNo)
                        .description(savedNew.getDescription())
                        .status("COMPLETED")
                        .docStatus("COMPLETED")
                        .isactive("Y")
                        .build();
                newPayment.setClientId(savedNew.getClientId());
                newPayment.setOrgId(savedNew.getOrgId());

                Payment savedNewPayment = paymentRepository.save(newPayment);

                accountingPostingService.postInvoice(null, savedNewInvoice);
                accountingPostingService.postPayment(null, savedNewPayment);
            }
        } else {
            // Create new payment
            String newPaymentNo = sequenceService.generateNextSequence(DocumentType.OUTBOUND_PAYMENT, targetScope.orgId());
            String normalizedMethod = normalizePaymentMethod(savedNew.getPaymentMethod());
            Payment newPayment = Payment.builder()
                    .paymentType(PaymentType.OUTBOUND)
                    .documentKind(DocumentType.OUTBOUND_PAYMENT.name())
                    .terminalId(savedNew.getTerminalId())
                    .invoiceId(savedNewInvoice.getId())
                    .expenseId(savedNew.getId())
                    .expenseCategoryId(category.getId())
                    .paymentDate(toLocalDateTime(savedNew.getExpenseDate()))
                    .paymentMethod(normalizedMethod)
                    .amountPaid(savedNew.getAmount())
                    .changeGiven(BigDecimal.ZERO)
                    .referenceNo(newPaymentNo)
                    .description(savedNew.getDescription())
                    .status("COMPLETED")
                    .docStatus("COMPLETED")
                    .isactive("Y")
                    .build();
            newPayment.setClientId(savedNew.getClientId());
            newPayment.setOrgId(savedNew.getOrgId());

            Payment savedNewPayment = paymentRepository.save(newPayment);

            accountingPostingService.postInvoice(null, savedNewInvoice);
            accountingPostingService.postPayment(null, savedNewPayment);
        }

        log.info("Expense revision finalized | oldId={} | newId={} | rev={}", id, savedNew.getId(), revision + 1);
        return expenseMapper.toExpenseResponse(savedNew, category.getName());
    }

    /**
     * Voids an expense record, marking it inactive and preserving the audit trail.
     * Returns a confirmation receipt with voided IDs for audit compliance.
     */
    @Transactional(timeout = EXPENSE_WRITE_TIMEOUT_SECONDS)
    public VoidExpenseResponse voidExpense(UUID id) {
        log.info("Voiding expense record | id={}", id);

        Expense expense = expenseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found: " + id));
        validateExpenseAccess(expense);

        if (!expense.isActive()) {
            throw new BusinessException("Expense is already voided");
        }

        int revision = expense.getRevisionNumber() != null ? expense.getRevisionNumber() : 0;

        // 1. Void the Expense
        expense.setExpenseNo(expense.getExpenseNo() + "_VOID_" + revision);
        expense.setStatus("VOID");
        expense.setDocStatus("VOID");
        expense.deactivate();
        expenseRepository.save(expense);

        // 2. Void linked Invoices (batched)
        List<Invoice> invoicesToVoid = invoiceRepository.findByExpenseId(id);
        List<UUID> invoiceIds = new ArrayList<>();
        invoicesToVoid.forEach(inv -> {
            accountingPostingService.reverseInvoice(inv, "Expense voided");
            inv.setInvoiceNo(inv.getInvoiceNo() + "_VOID_" + revision);
            inv.setStatus("VOID");
            inv.setDocStatus("VOID");
            inv.setIsactive("N");
            invoiceIds.add(inv.getId());
        });
        invoiceRepository.saveAll(invoicesToVoid);

        // 3. Void linked Payments (batched)
        List<Payment> paymentsToVoid = paymentRepository.findByExpenseId(id);
        List<UUID> paymentIds = new ArrayList<>();
        paymentsToVoid.forEach(pay -> {
            accountingPostingService.reversePayment(pay, "Expense voided");
            pay.setReferenceNo(pay.getReferenceNo() + "_VOID_" + revision);
            pay.setStatus("VOID");
            pay.setDocStatus("VOID");
            pay.setIsactive("N");
            paymentIds.add(pay.getId());
        });
        paymentRepository.saveAll(paymentsToVoid);
        paymentRepository.flush();

        return VoidExpenseResponse.builder()
                .expenseId(id)
                .invoiceId(invoiceIds.isEmpty() ? null : invoiceIds.get(0))
                .paymentIds(paymentIds)
                .voidedAt(Instant.now())
                .message("Expense " + id + " and its financial chain voided successfully")
                .build();
    }

    /**
     * Retrieves a single expense record by ID.
     */
    @Transactional(readOnly = true)
    public ExpenseResponse getExpenseById(UUID id) {
        Expense expense = expenseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found: " + id));
        validateExpenseAccess(expense);

        ExpenseCategory category = categoryRepository.findById(expense.getCategoryId())
                .orElse(null);

        return expenseMapper.toExpenseResponse(expense, category != null ? category.getName() : "Uncategorized");
    }

    /**
     * Fetches paginated and filtered expense records.
     * Only fetches category names for IDs present on the current page (no full-table scan).
     */
    @Transactional(readOnly = true)
    public Page<ExpenseResponse> getExpenses(ExpenseSearchCriteria criteria, Pageable pageable) {
        UUID clientId = TenantContext.getCurrentTenant();
        UUID orgId = TenantContext.getCurrentOrg();

        Pageable translatedPageable = translatePageable(pageable);

        Specification<Expense> spec = ExpenseSpecification.filterBy(criteria, clientId, orgId);
        Page<Expense> expensePage = expenseRepository.findAll(spec, translatedPageable);

        // Fetch only category names needed for the current page — no full-table read
        Set<UUID> neededCategoryIds = expensePage.getContent().stream()
                .map(Expense::getCategoryId)
                .collect(Collectors.toSet());

        Map<UUID, String> categoryNames = categoryRepository.findAllById(neededCategoryIds).stream()
                .collect(Collectors.toMap(ExpenseCategory::getId, ExpenseCategory::getName, (a, b) -> a));

        return expensePage.map(expense ->
            expenseMapper.toExpenseResponse(
                expense,
                categoryNames.getOrDefault(expense.getCategoryId(), "Uncategorized")
            )
        );
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers & Builders
    // ─────────────────────────────────────────────────────────────

    /**
     * Builds an Expense entity from any request implementing ExpenseBaseRequest.
     */
    private Expense buildExpenseEntity(ExpenseBaseRequest request, ExpenseCategory category, ExpenseWriteScope targetScope) {
        Instant expenseDate = (request.getExpenseDate() != null) ? request.getExpenseDate() : Instant.now();
        String paymentMethod = (request.getPaymentMethod() != null && !request.getPaymentMethod().isBlank()) ? request.getPaymentMethod() : "CASH";

        Expense expense = Expense.builder()
                .categoryId(category.getId())
                .expenseDate(expenseDate)
                .paymentMethod(paymentMethod)
                .amount(request.getAmount() != null ? request.getAmount() : BigDecimal.ZERO)
                .description(request.getDescription())
                .status("COMPLETED")
                .docStatus("COMPLETED")
                .paymentStatus("PAID")
                .isactive("Y")
                .revisionNumber(0)
                .build();

        // Contextual Metadata
        expense.setOrgId(targetScope.orgId());
        expense.setClientId(TenantContext.getCurrentTenant());
        expense.setTerminalId(TenantContext.getCurrentTerminal());

        // Default Currency
        currencyRepository.findByClientIdAndIsDefaultTrue(TenantContext.getCurrentTenant())
                .stream().findFirst().ifPresent(c -> expense.setCurrencyId(c.getId()));

        return expense;
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        if (instant == null) {
            return LocalDateTime.now();
        }
        return LocalDateTime.ofInstant(instant, BUSINESS_ZONE);
    }

    private String normalizePaymentMethod(String paymentMethod) {
        if (paymentMethod == null || paymentMethod.isBlank()) {
            return "CASH";
        }
        String normalized = paymentMethod.trim().toUpperCase(java.util.Locale.ROOT).replace(' ', '_');
        List<String> validMethods = List.of("CASH", "ONLINE", "UPI", "CARD", "BANK", "CHEQUE", "MIXED");
        return validMethods.contains(normalized) ? normalized : "CASH";
    }

    private long elapsedMillis(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000L;
    }

    private ExpenseCategory getOwnedExpenseCategory(UUID categoryId, UUID targetOrgId) {
        String profileOwner = currentProfileOwner();
        return categoryRepository.findByIdAndClientIdAndOrgIdAndCreatedBy(
                        categoryId,
                        TenantContext.getCurrentTenant(),
                        targetOrgId,
                        profileOwner
                )
                .orElseThrow(() -> new BusinessException("Invalid expense category"));
    }

    private ExpenseWriteScope resolveExpenseWriteScope(ExpenseBaseRequest request) {
        if (request == null) {
            throw new BusinessException("Expense request is required");
        }
        UUID currentOrgId = TenantContext.getCurrentOrg();
        String requestedScope = normalizeScope(request.getScope(), request.getBranchId(), currentOrgId);
        if ("GLOBAL".equals(requestedScope)) {
            if (!canUseOrganizationScope()) {
                throw new BusinessException("Only organization admins can record organization-level expenses.");
            }
            return new ExpenseWriteScope(null, "GLOBAL");
        }
        if ("ALL".equals(requestedScope)) {
            throw new BusinessException("Select Organization or a branch before recording an expense.");
        }
        UUID targetOrgId = request.getBranchId() != null ? request.getBranchId() : currentOrgId;
        if (targetOrgId == null) {
            throw new BusinessException("Select Organization or a branch before recording an expense.");
        }
        if (!canUseOrganizationScope() && !targetOrgId.equals(currentOrgId)) {
            throw new BusinessException("You can record expenses only for your assigned branch.");
        }
        return new ExpenseWriteScope(targetOrgId, "BRANCH");
    }

    private String normalizeScope(String scope, UUID branchId, UUID currentOrgId) {
        if (scope != null && !scope.isBlank()) {
            String normalized = scope.trim().toUpperCase(Locale.ROOT);
            return switch (normalized) {
                case "ALL", "GLOBAL", "BRANCH" -> normalized;
                default -> throw new BusinessException("Invalid expense scope.");
            };
        }
        if (branchId != null || currentOrgId != null) {
            return "BRANCH";
        }
        return "ALL";
    }

    private boolean canUseOrganizationScope() {
        return SecurityUtils.isSuperAdmin() || SecurityUtils.hasRole("ADMIN");
    }

    private void validateExpenseAccess(Expense expense) {
        UUID currentOrgId = TenantContext.getCurrentOrg();
        if (canUseOrganizationScope()) {
            return;
        }
        if (currentOrgId == null || !Objects.equals(currentOrgId, expense.getOrgId())) {
            throw new ResourceNotFoundException("Expense not found: " + expense.getId());
        }
    }

    private String currentProfileOwner() {
        String owner = SecurityUtils.getCurrentUserEmail();
        if (owner == null || owner.isBlank()) {
            throw new BusinessException("Authenticated profile is required for expense categories");
        }
        return owner.trim();
    }

    private record ExpenseWriteScope(UUID orgId, String scope) {
        private String cacheKeyPart() {
            return orgId == null ? "GLOBAL" : orgId.toString();
        }
    }
}
