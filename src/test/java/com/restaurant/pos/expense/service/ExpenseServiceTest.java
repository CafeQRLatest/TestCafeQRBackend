package com.restaurant.pos.expense.service;

import com.restaurant.pos.accounting.service.AccountingPostingService;
import com.restaurant.pos.category.domain.ExpenseCategory;
import com.restaurant.pos.category.repository.ExpenseCategoryRepository;
import com.restaurant.pos.common.exception.BusinessException;
import com.restaurant.pos.common.tenant.TenantContext;
import com.restaurant.pos.expense.domain.Expense;
import com.restaurant.pos.expense.dto.CreateExpenseRequest;
import com.restaurant.pos.expense.dto.ExpenseResponse;
import com.restaurant.pos.expense.idempotency.IdempotencyStore;
import com.restaurant.pos.expense.mapper.ExpenseMapper;
import com.restaurant.pos.expense.repository.ExpenseRepository;
import com.restaurant.pos.invoice.domain.Invoice;
import com.restaurant.pos.invoice.repository.InvoiceRepository;
import com.restaurant.pos.order.domain.Payment;
import com.restaurant.pos.order.repository.PaymentRepository;
import com.restaurant.pos.purchasing.repository.CurrencyRepository;
import com.restaurant.pos.sequence.domain.DocumentType;
import com.restaurant.pos.sequence.service.DocumentSequenceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExpenseServiceTest {

    private ExpenseCategoryRepository categoryRepository;
    private ExpenseRepository expenseRepository;
    private DocumentSequenceService sequenceService;
    private CurrencyRepository currencyRepository;
    private InvoiceRepository invoiceRepository;
    private PaymentRepository paymentRepository;
    private IdempotencyStore idempotencyStore;
    private ExpenseService expenseService;

    private UUID clientId;
    private UUID orgId;
    private String profileOwner;

    @BeforeEach
    void setUp() {
        categoryRepository = mock(ExpenseCategoryRepository.class);
        expenseRepository = mock(ExpenseRepository.class);
        sequenceService = mock(DocumentSequenceService.class);
        currencyRepository = mock(CurrencyRepository.class);
        invoiceRepository = mock(InvoiceRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        idempotencyStore = mock(IdempotencyStore.class);

        expenseService = new ExpenseService(
                categoryRepository,
                expenseRepository,
                new ExpenseMapper(),
                sequenceService,
                mock(AccountingPostingService.class),
                currencyRepository,
                invoiceRepository,
                paymentRepository,
                idempotencyStore
        );

        clientId = UUID.randomUUID();
        orgId = UUID.randomUUID();
        profileOwner = "profile-a@example.com";
        TenantContext.setCurrentTenant(clientId);
        TenantContext.setCurrentOrg(orgId);
        TenantContext.setCurrentTerminal(UUID.randomUUID());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                profileOwner,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        ));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void createExpenseSavesExpenseInvoiceAndPayment() {
        UUID categoryId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        Instant expenseDate = Instant.parse("2026-05-13T18:29:00Z");
        String idempotencyKey = "expense-create-key";
        String cacheKey = orgId + ":" + idempotencyKey;

        ExpenseCategory category = ExpenseCategory.builder()
                .id(categoryId)
                .name("Wages")
                .isactive("Y")
                .build();

        CreateExpenseRequest request = CreateExpenseRequest.builder()
                .categoryId(categoryId)
                .branchId(branchId)
                .expenseDate(expenseDate)
                .amount(new BigDecimal("1000.00"))
                .paymentMethod("BANK_TRANSFER")
                .description("Staff wages")
                .build();

        when(idempotencyStore.get(cacheKey)).thenReturn(null);
        when(categoryRepository.findByIdAndClientIdAndOrgIdAndCreatedBy(categoryId, clientId, orgId, profileOwner))
                .thenReturn(Optional.of(category));
        when(currencyRepository.findByClientIdAndIsDefaultTrue(clientId)).thenReturn(List.of());

        when(sequenceService.generateNextSequence(DocumentType.EXPENSE)).thenReturn("EXP-001");
        when(sequenceService.generateNextSequence(DocumentType.EXPENSE_RECEIPT)).thenReturn("EXR-001");
        when(sequenceService.generateNextSequence(DocumentType.OUTBOUND_PAYMENT)).thenReturn("PAY-001");

        UUID expenseId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        when(expenseRepository.save(any(Expense.class))).thenAnswer(invocation -> {
            Expense e = invocation.getArgument(0);
            Expense saved = e.toBuilder().id(expenseId).build();
            saved.setClientId(e.getClientId());
            saved.setOrgId(e.getOrgId());
            return saved;
        });
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> {
            Invoice inv = invocation.getArgument(0);
            Invoice saved = inv.toBuilder().id(invoiceId).build();
            saved.setClientId(inv.getClientId());
            saved.setOrgId(inv.getOrgId());
            return saved;
        });
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            Payment saved = p.toBuilder().id(paymentId).build();
            saved.setClientId(p.getClientId());
            saved.setOrgId(p.getOrgId());
            return saved;
        });

        ExpenseResponse response = expenseService.createExpense(idempotencyKey, request);

        assertThat(response.getId()).isEqualTo(expenseId);
        assertThat(response.getReferenceNumber()).isEqualTo("EXP-001");
        assertThat(response.getCategoryName()).isEqualTo("Wages");
        assertThat(response.getAmount()).isEqualByComparingTo("1000.00");
        assertThat(response.getPaymentMethod()).isEqualTo("BANK_TRANSFER");
        assertThat(response.getOrgId()).isEqualTo(branchId);

        ArgumentCaptor<Expense> expenseCaptor = ArgumentCaptor.forClass(Expense.class);
        verify(expenseRepository).save(expenseCaptor.capture());
        Expense submittedExpense = expenseCaptor.getValue();
        assertThat(submittedExpense.getCategoryId()).isEqualTo(categoryId);
        assertThat(submittedExpense.getAmount()).isEqualByComparingTo("1000.00");
        assertThat(submittedExpense.getPaymentMethod()).isEqualTo("BANK_TRANSFER");
        assertThat(submittedExpense.getOrgId()).isEqualTo(branchId);

        verify(idempotencyStore).put(cacheKey, response);
    }

    @Test
    void createExpenseRejectsCategoryOwnedByAnotherProfile() {
        UUID categoryId = UUID.randomUUID();
        String idempotencyKey = "wrong-profile-category-key";
        String cacheKey = orgId + ":" + idempotencyKey;

        CreateExpenseRequest request = CreateExpenseRequest.builder()
                .categoryId(categoryId)
                .expenseDate(Instant.parse("2026-05-13T18:29:00Z"))
                .amount(new BigDecimal("1000.00"))
                .paymentMethod("CASH")
                .build();

        when(idempotencyStore.get(cacheKey)).thenReturn(null);
        when(categoryRepository.findByIdAndClientIdAndOrgIdAndCreatedBy(categoryId, clientId, orgId, profileOwner))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> expenseService.createExpense(idempotencyKey, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Invalid expense category");
    }

    @Test
    void getExpensesTranslatesOrderDateToExpenseDate() {
        com.restaurant.pos.expense.dto.ExpenseSearchCriteria criteria = com.restaurant.pos.expense.dto.ExpenseSearchCriteria.builder().build();
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "orderDate"));

        when(expenseRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(org.springframework.data.domain.Page.empty());

        expenseService.getExpenses(criteria, pageable);

        ArgumentCaptor<org.springframework.data.domain.Pageable> pageableCaptor = ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        verify(expenseRepository).findAll(any(org.springframework.data.jpa.domain.Specification.class), pageableCaptor.capture());

        org.springframework.data.domain.Pageable capturedPageable = pageableCaptor.getValue();
        org.springframework.data.domain.Sort.Order order = capturedPageable.getSort().getOrderFor("expenseDate");
        assertThat(order).isNotNull();
        assertThat(order.getProperty()).isEqualTo("expenseDate");
        assertThat(order.getDirection()).isEqualTo(org.springframework.data.domain.Sort.Direction.DESC);
        assertThat(capturedPageable.getSort().getOrderFor("orderDate")).isNull();
    }
}
