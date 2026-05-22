package com.restaurant.pos.accounting.service;

import com.restaurant.pos.accounting.domain.AccountType;
import com.restaurant.pos.accounting.domain.AccountingAccount;
import com.restaurant.pos.accounting.domain.JournalEntry;
import com.restaurant.pos.accounting.domain.JournalLine;
import com.restaurant.pos.accounting.domain.JournalStatus;
import com.restaurant.pos.accounting.repository.AccountingAccountRepository;
import com.restaurant.pos.accounting.repository.AccountingPostingJobRepository;
import com.restaurant.pos.accounting.repository.JournalEntryRepository;
import com.restaurant.pos.accounting.repository.PartyLedgerEntryRepository;
import com.restaurant.pos.category.repository.ExpenseCategoryRepository;
import com.restaurant.pos.inventory.repository.StockAdjustmentRepository;
import com.restaurant.pos.invoice.domain.Invoice;
import com.restaurant.pos.invoice.domain.InvoiceType;
import com.restaurant.pos.invoice.repository.InvoiceRepository;
import com.restaurant.pos.order.domain.Order;
import com.restaurant.pos.order.domain.OrderType;
import com.restaurant.pos.order.repository.OrderRepository;
import com.restaurant.pos.order.repository.PaymentRepository;
import com.restaurant.pos.order.repository.PaymentSplitRepository;
import com.restaurant.pos.product.repository.ProductRepository;
import com.restaurant.pos.common.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountingPostingServiceTest {

    @Test
    void cleanupAutoPostedAccountingDataDeletesDependentsBeforeJournalEntries() {
        UUID clientId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();

        AccountingPostingJobRepository postingJobRepository = mock(AccountingPostingJobRepository.class);
        PartyLedgerEntryRepository partyLedgerEntryRepository = mock(PartyLedgerEntryRepository.class);
        JournalEntryRepository journalEntryRepository = mock(JournalEntryRepository.class);
        when(journalEntryRepository.bulkDeleteAutoPostedByClientIdAndOrgId(clientId, orgId)).thenReturn(3);

        AccountingPostingService service = new AccountingPostingService(
                mock(AccountingService.class),
                mock(AccountingDefaultsService.class),
                mock(AccountingAccountRepository.class),
                journalEntryRepository,
                partyLedgerEntryRepository,
                postingJobRepository,
                mock(OrderRepository.class),
                mock(InvoiceRepository.class),
                mock(PaymentRepository.class),
                mock(PaymentSplitRepository.class),
                mock(ProductRepository.class),
                mock(ExpenseCategoryRepository.class),
                mock(StockAdjustmentRepository.class),
                mock(com.restaurant.pos.expense.repository.ExpenseRepository.class),
                mock(PlatformTransactionManager.class)
        );

        int deleted = service.cleanupAutoPostedAccountingData(clientId, orgId);

        assertThat(deleted).isEqualTo(3);

        InOrder cleanupOrder = inOrder(postingJobRepository, partyLedgerEntryRepository, journalEntryRepository);
        cleanupOrder.verify(postingJobRepository).bulkDeleteByClientIdAndOrgId(clientId, orgId);
        cleanupOrder.verify(partyLedgerEntryRepository).bulkDeleteForAutoPostedJournalsByClientIdAndOrgId(clientId, orgId);
        cleanupOrder.verify(journalEntryRepository).bulkDeleteAutoPostedLinesByClientIdAndOrgId(clientId, orgId);
        cleanupOrder.verify(journalEntryRepository).bulkDeleteAutoPostedByClientIdAndOrgId(clientId, orgId);
    }

    @Test
    void internalPostingTransactionUsesRequiresNew() throws Exception {
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());

        AccountingPostingService service = new AccountingPostingService(
                mock(AccountingService.class),
                mock(AccountingDefaultsService.class),
                mock(AccountingAccountRepository.class),
                mock(JournalEntryRepository.class),
                mock(PartyLedgerEntryRepository.class),
                mock(AccountingPostingJobRepository.class),
                mock(OrderRepository.class),
                mock(InvoiceRepository.class),
                mock(PaymentRepository.class),
                mock(PaymentSplitRepository.class),
                mock(ProductRepository.class),
                mock(ExpenseCategoryRepository.class),
                mock(StockAdjustmentRepository.class),
                mock(com.restaurant.pos.expense.repository.ExpenseRepository.class),
                transactionManager
        );

        Method method = AccountingPostingService.class.getDeclaredMethod("inTransaction", Supplier.class);
        method.setAccessible(true);

        Object result = method.invoke(service, (Supplier<String>) () -> "posted");

        assertThat(result).isEqualTo("posted");
        verify(transactionManager).getTransaction(argThat(definition ->
                definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW));
    }

    @Test
    void replaceInvoiceJournalVoidsOldAutoPostedEntryAndPostsDiscountedJournal() {
        UUID clientId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        TenantContext.setCurrentTenant(clientId);
        TenantContext.setCurrentOrg(orgId);

        AccountingService accountingService = mock(AccountingService.class);
        AccountingDefaultsService defaultsService = mock(AccountingDefaultsService.class);
        AccountingAccountRepository accountRepository = mock(AccountingAccountRepository.class);
        JournalEntryRepository journalEntryRepository = mock(JournalEntryRepository.class);
        AccountingPostingJobRepository postingJobRepository = mock(AccountingPostingJobRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        InvoiceRepository invoiceRepository = mock(InvoiceRepository.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());

        AccountingPostingService service = new AccountingPostingService(
                accountingService,
                defaultsService,
                accountRepository,
                journalEntryRepository,
                mock(PartyLedgerEntryRepository.class),
                postingJobRepository,
                orderRepository,
                invoiceRepository,
                mock(PaymentRepository.class),
                mock(PaymentSplitRepository.class),
                mock(ProductRepository.class),
                mock(ExpenseCategoryRepository.class),
                mock(StockAdjustmentRepository.class),
                mock(com.restaurant.pos.expense.repository.ExpenseRepository.class),
                transactionManager
        );

        AccountingAccount receivable = account(AccountingDefaultsService.ACCOUNTS_RECEIVABLE, AccountType.ASSET);
        AccountingAccount discount = account(AccountingDefaultsService.DISCOUNT_ALLOWED, AccountType.EXPENSE);
        AccountingAccount revenue = account(AccountingDefaultsService.SALES_REVENUE, AccountType.INCOME);
        AccountingAccount tax = account(AccountingDefaultsService.OUTPUT_TAX, AccountType.LIABILITY);
        when(defaultsService.resolveAccount(AccountingDefaultsService.ACCOUNTS_RECEIVABLE)).thenReturn(receivable);
        when(defaultsService.resolveAccount(AccountingDefaultsService.DISCOUNT_ALLOWED)).thenReturn(discount);
        when(defaultsService.resolveAccount(AccountingDefaultsService.SALES_REVENUE)).thenReturn(revenue);
        when(defaultsService.resolveAccount(AccountingDefaultsService.OUTPUT_TAX)).thenReturn(tax);

        Order order = Order.builder()
                .id(orderId)
                .orderType(OrderType.SALE)
                .orderNo("SO-1")
                .orderStatus("COMPLETED")
                .grandTotal(new BigDecimal("68.00"))
                .totalTaxAmount(new BigDecimal("18.00"))
                .totalDiscountAmount(new BigDecimal("50.00"))
                .build();
        order.setClientId(clientId);
        order.setOrgId(orgId);

        Invoice invoice = Invoice.builder()
                .id(invoiceId)
                .invoiceType(InvoiceType.CUSTOMER_INVOICE)
                .orderId(orderId)
                .invoiceNo("INV-1")
                .invoiceDate(LocalDateTime.parse("2026-05-23T01:28:00"))
                .status("PAID")
                .totalAmount(new BigDecimal("68.00"))
                .amountDue(BigDecimal.ZERO)
                .build();
        invoice.setClientId(clientId);
        invoice.setOrgId(orgId);

        JournalEntry oldEntry = JournalEntry.builder()
                .id(UUID.randomUUID())
                .sourceType("CUSTOMER_INVOICE")
                .sourceId(invoiceId)
                .status(JournalStatus.POSTED)
                .autoPosted(true)
                .description("Original invoice")
                .build();
        oldEntry.setClientId(clientId);
        oldEntry.setOrgId(orgId);

        when(journalEntryRepository.findActiveBySource(clientId, orgId, "CUSTOMER_INVOICE", invoiceId, JournalStatus.POSTED))
                .thenReturn(List.of(oldEntry));
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(postingJobRepository.findByClientIdAndOrgIdAndSourceTypeAndSourceId(clientId, orgId, "CUSTOMER_INVOICE", invoiceId))
                .thenReturn(Optional.empty());
        when(accountingService.createJournalEntry(any(JournalEntry.class))).thenAnswer(invocation -> {
            JournalEntry entry = invocation.getArgument(0);
            entry.setId(UUID.randomUUID());
            return entry;
        });

        AccountingPostingService.PostingOutcome outcome = service.replaceInvoiceJournal(order, invoice, "Discount corrected");

        assertThat(outcome).isEqualTo(AccountingPostingService.PostingOutcome.POSTED);
        assertThat(oldEntry.getStatus()).isEqualTo(JournalStatus.VOID);
        assertThat(oldEntry.getIsactive()).isEqualTo("N");
        ArgumentCaptor<JournalEntry> journalCaptor = ArgumentCaptor.forClass(JournalEntry.class);
        verify(accountingService).createJournalEntry(journalCaptor.capture());
        JournalEntry corrected = journalCaptor.getValue();
        assertThat(corrected.getSourceType()).isEqualTo("CUSTOMER_INVOICE");
        assertThat(corrected.getSourceId()).isEqualTo(invoiceId);
        assertThat(debit(corrected, receivable)).isEqualByComparingTo("68.00");
        assertThat(debit(corrected, discount)).isEqualByComparingTo("50.00");
        assertThat(credit(corrected, revenue)).isEqualByComparingTo("100.00");
        assertThat(credit(corrected, tax)).isEqualByComparingTo("18.00");

        TenantContext.clear();
    }

    private AccountingAccount account(String systemKey, AccountType type) {
        AccountingAccount account = AccountingAccount.builder()
                .id(UUID.randomUUID())
                .code(systemKey)
                .name(systemKey)
                .accountType(type)
                .systemKey(systemKey)
                .build();
        account.setClientId(UUID.randomUUID());
        account.setOrgId(UUID.randomUUID());
        return account;
    }

    private BigDecimal debit(JournalEntry entry, AccountingAccount account) {
        return amount(entry, account, true);
    }

    private BigDecimal credit(JournalEntry entry, AccountingAccount account) {
        return amount(entry, account, false);
    }

    private BigDecimal amount(JournalEntry entry, AccountingAccount account, boolean debit) {
        return entry.getLines().stream()
                .filter(line -> account.getId().equals(line.getAccountId()))
                .map(line -> debit ? line.getDebit() : line.getCredit())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
