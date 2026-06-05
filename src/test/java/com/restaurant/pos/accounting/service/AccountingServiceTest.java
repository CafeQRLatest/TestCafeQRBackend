package com.restaurant.pos.accounting.service;

import com.restaurant.pos.accounting.domain.AccountType;
import com.restaurant.pos.accounting.domain.AccountingAccount;
import com.restaurant.pos.accounting.domain.JournalStatus;
import com.restaurant.pos.accounting.dto.AccountingSummaryDto;
import com.restaurant.pos.accounting.repository.AccountingAccountRepository;
import com.restaurant.pos.accounting.repository.JournalEntryRepository;
import com.restaurant.pos.accounting.repository.PartyLedgerEntryRepository;
import com.restaurant.pos.accounting.repository.PaymentAllocationRepository;
import com.restaurant.pos.common.service.BranchContextService;
import com.restaurant.pos.common.tenant.TenantContext;
import com.restaurant.pos.invoice.repository.InvoiceRepository;
import com.restaurant.pos.order.repository.OrderRepository;
import com.restaurant.pos.order.repository.PaymentRepository;
import com.restaurant.pos.order.repository.PaymentSplitRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccountingServiceTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void summaryNetsVoidedSaleJournalReversalToZero() {
        UUID clientId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        TenantContext.setCurrentTenant(clientId);
        TenantContext.setCurrentOrg(orgId);

        AccountingAccount revenue = account(AccountingDefaultsService.SALES_REVENUE, AccountType.INCOME);
        AccountingAccount outputTax = account(AccountingDefaultsService.OUTPUT_TAX, AccountType.LIABILITY);
        AccountingAccount cash = account(AccountingDefaultsService.CASH, AccountType.ASSET);
        AccountingAccount bank = account(AccountingDefaultsService.BANK_UPI_CLEARING, AccountType.ASSET);
        List<AccountingAccount> accounts = List.of(revenue, outputTax, cash, bank);

        AccountingAccountRepository accountRepository = mock(AccountingAccountRepository.class);
        JournalEntryRepository journalEntryRepository = mock(JournalEntryRepository.class);
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentSplitRepository paymentSplitRepository = mock(PaymentSplitRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);

        when(accountRepository.findByClientIdAndOrgIdOrderByCodeAsc(clientId, orgId)).thenReturn(accounts);
        when(journalEntryRepository.sumLineMovements(eq(clientId), eq(orgId), any(), any(), eq(JournalStatus.POSTED)))
                .thenReturn(List.of())
                .thenReturn(List.of(
                        movement(revenue.getId(), "100.00", "100.00"),
                        movement(outputTax.getId(), "5.00", "5.00"),
                        movement(cash.getId(), "52.50", "52.50"),
                        movement(bank.getId(), "52.50", "52.50")
                ));
        when(journalEntryRepository.countPostedActive(eq(clientId), eq(orgId), any(), any(), eq(JournalStatus.POSTED)))
                .thenReturn(8L);
        when(orderRepository.findAll(any(Specification.class))).thenReturn(List.of());
        when(paymentRepository.findActivePaymentsInPeriod(eq(clientId), eq(orgId), any(), any())).thenReturn(List.of());
        when(paymentSplitRepository.findByPaymentIdInOrderByCreatedAtAsc(any())).thenReturn(List.of());

        AccountingService service = new AccountingService(
                accountRepository,
                journalEntryRepository,
                mock(PartyLedgerEntryRepository.class),
                mock(PaymentAllocationRepository.class),
                paymentRepository,
                paymentSplitRepository,
                mock(InvoiceRepository.class),
                orderRepository,
                mock(BranchContextService.class)
        );

        AccountingSummaryDto summary = service.getSummary(
                LocalDateTime.parse("2026-06-06T00:00:00"),
                LocalDateTime.parse("2026-06-06T23:59:59")
        );

        assertThat(summary.getGrossSales()).isEqualByComparingTo("0.00");
        assertThat(summary.getNetSales()).isEqualByComparingTo("0.00");
        assertThat(summary.getBilledTotal()).isEqualByComparingTo("0.00");
        assertThat(summary.getOutputTax()).isEqualByComparingTo("0.00");
        assertThat(summary.getPaymentCollected()).isEqualByComparingTo("0.00");
        assertThat(summary.getCashCollected()).isEqualByComparingTo("0.00");
        assertThat(summary.getBankCollected()).isEqualByComparingTo("0.00");
        assertThat(summary.getProfit()).isEqualByComparingTo("0.00");
    }

    private AccountingAccount account(String systemKey, AccountType type) {
        AccountingAccount account = AccountingAccount.builder()
                .id(UUID.randomUUID())
                .code(systemKey)
                .name(systemKey)
                .accountType(type)
                .systemKey(systemKey)
                .openingBalance(BigDecimal.ZERO)
                .isactive("Y")
                .build();
        account.setClientId(UUID.randomUUID());
        account.setOrgId(UUID.randomUUID());
        return account;
    }

    private JournalEntryRepository.AccountMovementProjection movement(UUID accountId, String debit, String credit) {
        BigDecimal debitAmount = new BigDecimal(debit);
        BigDecimal creditAmount = new BigDecimal(credit);
        return new JournalEntryRepository.AccountMovementProjection() {
            @Override
            public UUID getAccountId() {
                return accountId;
            }

            @Override
            public BigDecimal getDebit() {
                return debitAmount;
            }

            @Override
            public BigDecimal getCredit() {
                return creditAmount;
            }
        };
    }
}
