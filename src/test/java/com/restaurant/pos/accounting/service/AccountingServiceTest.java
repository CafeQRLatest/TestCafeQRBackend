package com.restaurant.pos.accounting.service;

import com.restaurant.pos.accounting.dto.TrialBalanceRowDto;
import com.restaurant.pos.accounting.repository.AccountingAccountRepository;
import com.restaurant.pos.accounting.repository.JournalEntryRepository;
import com.restaurant.pos.accounting.repository.PartyLedgerEntryRepository;
import com.restaurant.pos.accounting.repository.PaymentAllocationRepository;
import com.restaurant.pos.common.exception.BusinessException;
import com.restaurant.pos.common.service.BranchContextService;
import com.restaurant.pos.common.tenant.TenantContext;
import com.restaurant.pos.invoice.repository.InvoiceRepository;
import com.restaurant.pos.order.repository.OrderRepository;
import com.restaurant.pos.order.repository.PaymentRepository;
import com.restaurant.pos.order.repository.PaymentSplitRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    void trialBalanceAllowsRangesLongerThanOneYear() {
        UUID clientId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        TenantContext.setCurrentTenant(clientId);
        TenantContext.setCurrentOrg(orgId);

        AccountingAccountRepository accountRepository = mock(AccountingAccountRepository.class);
        JournalEntryRepository journalEntryRepository = mock(JournalEntryRepository.class);
        AccountingService service = service(accountRepository, journalEntryRepository);

        when(accountRepository.findByClientIdAndOrgIdOrderByCodeAsc(eq(clientId), eq(orgId))).thenReturn(List.of());
        when(journalEntryRepository.findAll(any(Specification.class))).thenReturn(List.of());

        List<TrialBalanceRowDto> rows = service.getTrialBalance(
                LocalDateTime.parse("2024-01-01T00:00:00"),
                LocalDateTime.parse("2026-05-27T23:59:00")
        );

        assertThat(rows).isEmpty();
    }

    @Test
    void trialBalanceRejectsReversedRange() {
        TenantContext.setCurrentTenant(UUID.randomUUID());
        TenantContext.setCurrentOrg(UUID.randomUUID());
        AccountingService service = service(mock(AccountingAccountRepository.class), mock(JournalEntryRepository.class));

        assertThatThrownBy(() -> service.getTrialBalance(
                LocalDateTime.parse("2026-05-27T23:59:00"),
                LocalDateTime.parse("2024-01-01T00:00:00")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("before");
    }

    private AccountingService service(
            AccountingAccountRepository accountRepository,
            JournalEntryRepository journalEntryRepository
    ) {
        return new AccountingService(
                accountRepository,
                journalEntryRepository,
                mock(PartyLedgerEntryRepository.class),
                mock(PaymentAllocationRepository.class),
                mock(PaymentRepository.class),
                mock(PaymentSplitRepository.class),
                mock(InvoiceRepository.class),
                mock(OrderRepository.class),
                mock(BranchContextService.class)
        );
    }
}
