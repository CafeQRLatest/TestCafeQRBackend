package com.restaurant.pos.accounting.service;

import com.restaurant.pos.accounting.repository.AccountingAccountRepository;
import com.restaurant.pos.accounting.repository.AccountingPostingJobRepository;
import com.restaurant.pos.accounting.repository.JournalEntryRepository;
import com.restaurant.pos.accounting.repository.PartyLedgerEntryRepository;
import com.restaurant.pos.category.repository.ExpenseCategoryRepository;
import com.restaurant.pos.inventory.repository.StockAdjustmentRepository;
import com.restaurant.pos.invoice.repository.InvoiceRepository;
import com.restaurant.pos.order.repository.OrderRepository;
import com.restaurant.pos.order.repository.PaymentRepository;
import com.restaurant.pos.order.repository.PaymentSplitRepository;
import com.restaurant.pos.product.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
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
}
