package com.restaurant.pos.expense.service;

import com.restaurant.pos.common.exception.BusinessException;
import com.restaurant.pos.common.exception.ResourceNotFoundException;
import com.restaurant.pos.common.tenant.TenantContext;
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
import com.restaurant.pos.expense.domain.ExpenseStatus;
import com.restaurant.pos.expense.domain.ExpensePaymentMethod;
import com.restaurant.pos.invoice.domain.Invoice;
import com.restaurant.pos.invoice.repository.InvoiceRepository;
import com.restaurant.pos.order.domain.Order;
import com.restaurant.pos.order.domain.Payment;
import com.restaurant.pos.order.repository.PaymentRepository;
import com.restaurant.pos.order.service.OrderService;
import com.restaurant.pos.purchasing.repository.CurrencyRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    private final ExpenseCategoryRepository categoryRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseMapper expenseMapper;
    private final OrderService orderService;
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
            "expenseDate", "orderDate",
            "amount",      "grandTotal"
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
    @Transactional(timeout = 5)
    public ExpenseResponse createExpense(String idempotencyKey, CreateExpenseRequest request) {
        boolean hasIdempotencyKey = idempotencyKey != null && !idempotencyKey.isBlank();

        // Idempotency check — prevents duplicate Order+Invoice+Payment triplets on retries
        String cacheKey = TenantContext.getCurrentOrg() + ":" + idempotencyKey;
        ExpenseResponse cached = idempotencyStore.get(cacheKey);
        if (cached != null) {
            log.info("Expense idempotency hit | keyPresent={}", hasIdempotencyKey);
            return cached;
        }

        ExpenseCategory category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new BusinessException("Invalid expense category"));

        if (!category.isActive()) {
            throw new BusinessException("Cannot assign expense to an inactive category");
        }

        Expense expense = buildExpenseEntity(request, category);

        log.info(
                "Creating expense | categoryId={} | branchId={} | amount={} | paymentMethod={} | keyPresent={}",
                request.getCategoryId(),
                request.getBranchId(),
                request.getAmount(),
                request.getPaymentMethod(),
                hasIdempotencyKey
        );

        Expense savedExpense;
        try {
            // OrderService handles Sequences, Invoices, and Payments for the unified 'orders' table.
            Order createdOrder = orderService.createOrder(expense);

            if (createdOrder == null || createdOrder.getId() == null) {
                throw new BusinessException("Failed to create expense order");
            }

            log.info(
                    "Expense order created | orderId={} | categoryId={} | branchId={} | keyPresent={}",
                    createdOrder.getId(),
                    category.getId(),
                    expense.getOrgId(),
                    hasIdempotencyKey
            );

            // Refetch as Expense to avoid ClassCastException with JPA proxies/base Order returns.
            savedExpense = expenseRepository.findById(createdOrder.getId())
                    .orElseThrow(() -> new BusinessException("Failed to retrieve saved expense"));
        } catch (RuntimeException ex) {
            log.error(
                    "Expense creation failed | categoryId={} | branchId={} | amount={} | paymentMethod={} | keyPresent={}",
                    request.getCategoryId(),
                    request.getBranchId(),
                    request.getAmount(),
                    request.getPaymentMethod(),
                    hasIdempotencyKey,
                    ex
            );
            throw ex;
        }

        ExpenseResponse response = expenseMapper.toExpenseResponse(savedExpense, category.getName());

        idempotencyStore.put(cacheKey, response);

        return response;
    }

    /**
     * Updates an existing expense using an immutability/voiding pattern.
     * Voids the old record and creates a new one to maintain audit integrity.
     */
    @Transactional(timeout = 5)
    public ExpenseResponse updateExpense(UUID id, UpdateExpenseRequest request) {
        Expense oldExpense = expenseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found: " + id));

        if (!oldExpense.isActive()) {
            throw new BusinessException("Cannot modify a voided or inactive expense record");
        }

        ExpenseCategory category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new BusinessException("Invalid expense category"));

        String originalOrderNo = oldExpense.getOrderNo();
        int revision = oldExpense.getRevisionNumber() != null ? oldExpense.getRevisionNumber() : 0;
        boolean amountChanged = oldExpense.getGrandTotal().compareTo(request.getAmount()) != 0;

        log.info("Updating expense | id={} | amountChanged={}", id, amountChanged);

        // 1. VOID the old expense record
        String voidSuffix = "_VOID_" + revision;
        oldExpense.setOrderNo(originalOrderNo + voidSuffix);
        oldExpense.setOrderStatus(ExpenseStatus.VOID);
        oldExpense.setDocStatus(ExpenseStatus.VOID);
        oldExpense.deactivate();
        oldExpense.getLines().forEach(line -> line.deactivate());
        expenseRepository.saveAndFlush(oldExpense);

        // 2. VOID the linked invoices (batched)
        List<Invoice> invoicesToVoid = invoiceRepository.findByOrderId(id);
        invoicesToVoid.forEach(inv -> {
            inv.setInvoiceNo(inv.getInvoiceNo() + "_VOID_" + revision);
            inv.setStatus(ExpenseStatus.VOID);
            inv.setDocStatus(ExpenseStatus.VOID);
            inv.setIsactive("N");
        });
        invoiceRepository.saveAll(invoicesToVoid);

        // 3. Handle Payment Logic
        List<Payment> oldPayments = paymentRepository.findByOrderId(id);

        // 4. Create NEW Expense record
        Expense newExpense = buildExpenseEntity(request, category);
        newExpense.setOrderNo(originalOrderNo);
        newExpense.setRevisionNumber(revision + 1);
        newExpense.setOriginalOrderId(oldExpense.getId());

        if (!oldPayments.isEmpty() && !amountChanged) {
            newExpense.setPaymentStatus(ExpenseStatus.PENDING);
        }

        Order baseOrder = orderService.createOrder(newExpense);

        // Refetch as Expense to avoid ClassCastException with JPA proxies
        Expense savedNew = expenseRepository.findById(baseOrder.getId())
                .orElseThrow(() -> new BusinessException("Failed to retrieve saved expense"));

        List<Invoice> invs = invoiceRepository.findByOrderId(savedNew.getId());
        Invoice newInvoice = (invs != null && !invs.isEmpty()) ? invs.get(0) : null;

        if (!oldPayments.isEmpty()) {
            if (!amountChanged) {
                // REUSE: Link the first payment to the new order/invoice
                Payment oldPayment = oldPayments.get(0);
                oldPayment.setOrderId(savedNew.getId());
                oldPayment.setInvoiceId(newInvoice != null ? newInvoice.getId() : null);
                oldPayment.setExpenseCategoryId(category.getId());
                paymentRepository.saveAndFlush(oldPayment);

                savedNew.setPaymentStatus(ExpenseStatus.PAID);
                expenseRepository.save(savedNew);

                if (newInvoice != null) {
                    newInvoice.setStatus(ExpenseStatus.PAID);
                    newInvoice.setIsPaid(true);
                    newInvoice.setAmountDue(BigDecimal.ZERO);
                    invoiceRepository.save(newInvoice);
                }
            } else {
                // VOID all old payments (batched)
                oldPayments.forEach(pay -> {
                    pay.setStatus(ExpenseStatus.VOID);
                    pay.setDocStatus(ExpenseStatus.VOID);
                    pay.setIsactive("N");
                    pay.setReferenceNo(pay.getReferenceNo() + "_VOID_" + revision);
                });
                paymentRepository.saveAll(oldPayments);
                paymentRepository.flush();
            }
        }

        log.info("Expense revision finalized | oldId={} | newId={} | rev={}", id, savedNew.getId(), revision + 1);
        return expenseMapper.toExpenseResponse(savedNew, category.getName());
    }

    /**
     * Voids an expense record, marking it inactive and preserving the audit trail.
     * Returns a confirmation receipt with voided IDs for audit compliance.
     */
    @Transactional(timeout = 5)
    public VoidExpenseResponse voidExpense(UUID id) {
        log.info("Voiding expense record | id={}", id);

        Expense expense = expenseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found: " + id));

        if (!expense.isActive()) {
            throw new BusinessException("Expense is already voided");
        }

        int revision = expense.getRevisionNumber() != null ? expense.getRevisionNumber() : 0;

        // 1. Void the Expense Order
        expense.setOrderNo(expense.getOrderNo() + "_VOID_" + revision);
        expense.setOrderStatus(ExpenseStatus.VOID);
        expense.setDocStatus(ExpenseStatus.VOID);
        expense.deactivate();
        expense.getLines().forEach(line -> line.deactivate());
        expenseRepository.save(expense);

        // 2. Void linked Invoices (batched)
        List<Invoice> invoicesToVoid = invoiceRepository.findByOrderId(id);
        List<UUID> invoiceIds = new ArrayList<>();
        invoicesToVoid.forEach(inv -> {
            inv.setInvoiceNo(inv.getInvoiceNo() + "_VOID_" + revision);
            inv.setStatus(ExpenseStatus.VOID);
            inv.setDocStatus(ExpenseStatus.VOID);
            inv.setIsactive("N");
            invoiceIds.add(inv.getId());
        });
        invoiceRepository.saveAll(invoicesToVoid);

        // 3. Void linked Payments (batched)
        List<Payment> paymentsToVoid = paymentRepository.findByOrderId(id);
        List<UUID> paymentIds = new ArrayList<>();
        paymentsToVoid.forEach(pay -> {
            pay.setReferenceNo(pay.getReferenceNo() + "_VOID_" + revision);
            pay.setStatus(ExpenseStatus.VOID);
            pay.setDocStatus(ExpenseStatus.VOID);
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

        ExpenseCategory category = categoryRepository.findById(expense.getExpenseCategoryId())
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
                .map(Expense::getExpenseCategoryId)
                .collect(Collectors.toSet());

        Map<UUID, String> categoryNames = categoryRepository.findAllById(neededCategoryIds).stream()
                .collect(Collectors.toMap(ExpenseCategory::getId, ExpenseCategory::getName, (a, b) -> a));

        return expensePage.map(expense ->
            expenseMapper.toExpenseResponse(
                expense,
                categoryNames.getOrDefault(expense.getExpenseCategoryId(), "Uncategorized")
            )
        );
    }

    // ─────────────────────────────────────────────────────────────
    // Unified Builder
    // ─────────────────────────────────────────────────────────────

    /**
     * Builds an Expense entity from any request implementing ExpenseBaseRequest.
     * Single method for both create and update — eliminates duplicate builder trap.
     */
    private Expense buildExpenseEntity(ExpenseBaseRequest request, ExpenseCategory category) {
        Instant expenseDate = (request.getExpenseDate() != null) ? request.getExpenseDate() : Instant.now();
        String paymentMethod = (request.getPaymentMethod() != null && !request.getPaymentMethod().isBlank()) ? request.getPaymentMethod() : ExpensePaymentMethod.CASH;

        Expense expense = new Expense(category);
        expense.setOrderDate(expenseDate);
        expense.setReference(paymentMethod);

        // Contextual Metadata
        UUID orgId = TenantContext.getCurrentOrg();
        expense.setOrgId((request.getBranchId() != null) ? request.getBranchId() : orgId);
        expense.setClientId(TenantContext.getCurrentTenant());
        expense.setTerminalId(TenantContext.getCurrentTerminal());

        // Financials
        expense.setTotalAmount(request.getAmount() != null ? request.getAmount() : BigDecimal.ZERO);
        expense.setGrandTotal(expense.getTotalAmount());
        expense.setDescription(request.getDescription());

        // Status Initialization
        expense.setOrderStatus(ExpenseStatus.COMPLETED);
        expense.setDocStatus(ExpenseStatus.COMPLETED);
        expense.setPaymentStatus(ExpenseStatus.PAID);

        // Default Currency
        currencyRepository.findByClientIdAndIsDefaultTrue(TenantContext.getCurrentTenant())
                .stream().findFirst().ifPresent(c -> expense.setCurrencyId(c.getId()));

        return expense;
    }
}
