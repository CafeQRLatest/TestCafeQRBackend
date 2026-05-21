package com.restaurant.pos.accounting.service;

import com.restaurant.pos.accounting.domain.AccountType;
import com.restaurant.pos.accounting.domain.AccountingAccount;
import com.restaurant.pos.accounting.domain.AccountingAccountMapping;
import com.restaurant.pos.accounting.domain.AccountingPaymentMethodMapping;
import com.restaurant.pos.accounting.repository.AccountingAccountMappingRepository;
import com.restaurant.pos.accounting.repository.AccountingAccountRepository;
import com.restaurant.pos.accounting.repository.AccountingPaymentMethodMappingRepository;
import com.restaurant.pos.common.service.BranchContextService;
import com.restaurant.pos.common.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountingDefaultsServiceTest {

    private AccountingAccountRepository accountRepository;
    private AccountingAccountMappingRepository mappingRepository;
    private AccountingPaymentMethodMappingRepository paymentMappingRepository;
    private AccountingDefaultsService service;
    private UUID clientId;
    private UUID orgId;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountingAccountRepository.class);
        mappingRepository = mock(AccountingAccountMappingRepository.class);
        paymentMappingRepository = mock(AccountingPaymentMethodMappingRepository.class);
        service = new AccountingDefaultsService(
                accountRepository,
                mappingRepository,
                paymentMappingRepository,
                mock(BranchContextService.class)
        );
        clientId = UUID.randomUUID();
        orgId = UUID.randomUUID();
        TenantContext.setCurrentTenant(clientId);
        TenantContext.setCurrentOrg(orgId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void ensureDefaultAccountsRecoversLegacySysAccountsAndMappings() {
        Map<String, AccountingAccount> accountsByCode = new LinkedHashMap<>();
        defaultAccounts().forEach(template -> accountsByCode.put(
                template.code(),
                legacyAccount(template.code(), template.name(), template.type())
        ));

        when(accountRepository.findByClientIdAndOrgIdAndSystemKeyIgnoreCase(any(), any(), any()))
                .thenAnswer(invocation -> {
                    String systemKey = invocation.getArgument(2);
                    return accountsByCode.values().stream()
                            .filter(account -> systemKey.equalsIgnoreCase(account.getSystemKey()))
                            .findFirst();
                });
        when(accountRepository.findByClientIdAndOrgIdAndCodeIgnoreCase(any(), any(), any()))
                .thenAnswer(invocation -> Optional.ofNullable(accountsByCode.get(((String) invocation.getArgument(2)).toUpperCase())));
        when(accountRepository.save(any(AccountingAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mappingRepository.findByClientIdAndOrgIdAndMappingKeyIgnoreCase(any(), any(), any())).thenReturn(Optional.empty());
        when(mappingRepository.save(any(AccountingAccountMapping.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentMappingRepository.findByClientIdAndOrgIdAndPaymentMethodIgnoreCase(any(), any(), any())).thenReturn(Optional.empty());
        when(paymentMappingRepository.save(any(AccountingPaymentMethodMapping.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<AccountingAccount> recovered = service.ensureDefaultAccounts();

        assertThat(recovered).hasSize(defaultAccounts().size());

        AccountingAccount operatingExpenses = accountsByCode.get("SYS-5100");
        assertThat(operatingExpenses.getSystemKey()).isEqualTo(AccountingDefaultsService.OPERATING_EXPENSES);
        assertThat(operatingExpenses.getSystemAccount()).isTrue();
        assertThat(operatingExpenses.getAccountType()).isEqualTo(AccountType.EXPENSE);
        assertThat(operatingExpenses.getAccountSubType()).isEqualTo("Expense");

        AccountingAccount cash = accountsByCode.get("SYS-1000");
        AccountingAccount bank = accountsByCode.get("SYS-1010");
        assertThat(cash.getSystemKey()).isEqualTo(AccountingDefaultsService.CASH);
        assertThat(cash.getCashAccount()).isTrue();
        assertThat(cash.getBankAccount()).isFalse();
        assertThat(bank.getSystemKey()).isEqualTo(AccountingDefaultsService.BANK_UPI_CLEARING);
        assertThat(bank.getCashAccount()).isFalse();
        assertThat(bank.getBankAccount()).isTrue();

        ArgumentCaptor<AccountingAccountMapping> accountMappingCaptor = ArgumentCaptor.forClass(AccountingAccountMapping.class);
        verify(mappingRepository, org.mockito.Mockito.times(defaultAccounts().size())).save(accountMappingCaptor.capture());
        assertThat(accountMappingCaptor.getAllValues())
                .extracting(AccountingAccountMapping::getMappingKey)
                .contains(
                        AccountingDefaultsService.OPERATING_EXPENSES,
                        AccountingDefaultsService.SALES_REVENUE,
                        AccountingDefaultsService.CASH
                );

        ArgumentCaptor<AccountingPaymentMethodMapping> paymentMappingCaptor = ArgumentCaptor.forClass(AccountingPaymentMethodMapping.class);
        verify(paymentMappingRepository, org.mockito.Mockito.times(7)).save(paymentMappingCaptor.capture());
        assertThat(paymentMappingCaptor.getAllValues())
                .anySatisfy(mapping -> {
                    assertThat(mapping.getPaymentMethod()).isEqualTo("CASH");
                    assertThat(mapping.getAccountId()).isEqualTo(cash.getId());
                })
                .anySatisfy(mapping -> {
                    assertThat(mapping.getPaymentMethod()).isEqualTo("UPI");
                    assertThat(mapping.getAccountId()).isEqualTo(bank.getId());
                });
    }

    private AccountingAccount legacyAccount(String code, String name, AccountType type) {
        AccountingAccount account = AccountingAccount.builder()
                .id(UUID.randomUUID())
                .code(code)
                .name(name)
                .accountType(type)
                .openingBalance(BigDecimal.ZERO)
                .currentBalance(BigDecimal.ZERO)
                .systemAccount(false)
                .cashAccount(false)
                .bankAccount(false)
                .isactive("Y")
                .build();
        account.setClientId(clientId);
        account.setOrgId(orgId);
        return account;
    }

    private List<DefaultAccount> defaultAccounts() {
        return List.of(
                new DefaultAccount("SYS-1000", "Cash in Hand", AccountType.ASSET),
                new DefaultAccount("SYS-1010", "Bank / UPI Clearing", AccountType.ASSET),
                new DefaultAccount("SYS-1100", "Accounts Receivable", AccountType.ASSET),
                new DefaultAccount("SYS-1200", "Inventory Asset", AccountType.ASSET),
                new DefaultAccount("SYS-1300", "Input Tax", AccountType.ASSET),
                new DefaultAccount("SYS-2000", "Accounts Payable", AccountType.LIABILITY),
                new DefaultAccount("SYS-2100", "Output Tax", AccountType.LIABILITY),
                new DefaultAccount("SYS-4000", "Sales Revenue", AccountType.INCOME),
                new DefaultAccount("SYS-5000", "Purchase / Cost of Goods Sold", AccountType.EXPENSE),
                new DefaultAccount("SYS-5100", "Operating Expenses", AccountType.EXPENSE),
                new DefaultAccount("SYS-5200", "Discount Allowed", AccountType.EXPENSE),
                new DefaultAccount("SYS-5300", "Round Off", AccountType.EXPENSE),
                new DefaultAccount("SYS-5400", "Stock Adjustment Gain/Loss", AccountType.EXPENSE)
        );
    }

    private record DefaultAccount(String code, String name, AccountType type) {
    }
}
