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
import com.restaurant.pos.invoice.repository.InvoiceRepository;
import com.restaurant.pos.order.domain.Order;
import com.restaurant.pos.order.repository.PaymentRepository;
import com.restaurant.pos.order.service.OrderService;
import com.restaurant.pos.purchasing.repository.CurrencyRepository;
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
    private OrderService orderService;
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
        orderService = mock(OrderService.class);
        currencyRepository = mock(CurrencyRepository.class);
        invoiceRepository = mock(InvoiceRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        idempotencyStore = mock(IdempotencyStore.class);

        expenseService = new ExpenseService(
                categoryRepository,
                expenseRepository,
                new ExpenseMapper(),
                orderService,
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
    void createExpenseRefetchesExpenseWhenOrderServiceReturnsBaseOrder() {
        UUID categoryId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        UUID createdOrderId = UUID.randomUUID();
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

        Order createdOrder = new Order();
        createdOrder.setId(createdOrderId);

        Expense persistedExpense = new Expense(category);
        persistedExpense.setId(createdOrderId);
        persistedExpense.setOrderNo("EX-00001");
        persistedExpense.setOrderDate(expenseDate);
        persistedExpense.setGrandTotal(new BigDecimal("1000.00"));
        persistedExpense.setDescription("Staff wages");
        persistedExpense.setReference("BANK_TRANSFER");
        persistedExpense.setOrgId(branchId);

        when(idempotencyStore.get(cacheKey)).thenReturn(null);
        when(categoryRepository.findByIdAndClientIdAndOrgIdAndCreatedBy(categoryId, clientId, orgId, profileOwner))
                .thenReturn(Optional.of(category));
        when(currencyRepository.findByClientIdAndIsDefaultTrue(clientId)).thenReturn(List.of());
        when(orderService.createOrder(any(Expense.class))).thenReturn(createdOrder);
        when(expenseRepository.findById(createdOrderId)).thenReturn(Optional.of(persistedExpense));

        ExpenseResponse response = expenseService.createExpense(idempotencyKey, request);

        assertThat(response.getId()).isEqualTo(createdOrderId);
        assertThat(response.getReferenceNumber()).isEqualTo("EX-00001");
        assertThat(response.getCategoryName()).isEqualTo("Wages");
        assertThat(response.getAmount()).isEqualByComparingTo("1000.00");
        assertThat(response.getPaymentMethod()).isEqualTo("BANK_TRANSFER");
        assertThat(response.getOrgId()).isEqualTo(branchId);

        ArgumentCaptor<Expense> expenseCaptor = ArgumentCaptor.forClass(Expense.class);
        verify(orderService).createOrder(expenseCaptor.capture());
        Expense submittedExpense = expenseCaptor.getValue();
        assertThat(submittedExpense.getExpenseCategoryId()).isEqualTo(categoryId);
        assertThat(submittedExpense.getGrandTotal()).isEqualByComparingTo("1000.00");
        assertThat(submittedExpense.getReference()).isEqualTo("BANK_TRANSFER");
        assertThat(submittedExpense.getOrgId()).isEqualTo(branchId);

        verify(expenseRepository).findById(createdOrderId);
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
}
