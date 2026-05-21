package com.restaurant.pos.accounting.service;

import com.restaurant.pos.accounting.domain.*;
import com.restaurant.pos.accounting.dto.AccountingAccountPeriodDto;
import com.restaurant.pos.accounting.dto.AccountingReconciliationDto;
import com.restaurant.pos.accounting.dto.AccountingSummaryDto;
import com.restaurant.pos.accounting.dto.TrialBalanceRowDto;
import com.restaurant.pos.accounting.repository.*;
import com.restaurant.pos.common.exception.BusinessException;
import com.restaurant.pos.common.exception.ResourceNotFoundException;
import com.restaurant.pos.common.service.BranchContextService;
import com.restaurant.pos.common.tenant.TenantContext;
import com.restaurant.pos.common.util.SecurityUtils;
import com.restaurant.pos.invoice.domain.Invoice;
import com.restaurant.pos.invoice.domain.InvoiceType;
import com.restaurant.pos.invoice.repository.InvoiceRepository;
import com.restaurant.pos.order.domain.Order;
import com.restaurant.pos.order.domain.OrderType;
import com.restaurant.pos.order.domain.Payment;
import com.restaurant.pos.order.domain.PaymentSplit;
import com.restaurant.pos.order.domain.PaymentType;
import com.restaurant.pos.order.repository.OrderRepository;
import com.restaurant.pos.order.repository.PaymentRepository;
import com.restaurant.pos.order.repository.PaymentSplitRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccountingService {

    private static final int DEFAULT_REPORT_DAYS = 31;
    private static final int MAX_REPORT_DAYS = 366;
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final Set<String> FINANCIAL_PAYMENT_METHODS = Set.of("CASH", "ONLINE", "UPI", "CARD", "BANK", "CHEQUE");

    private final AccountingAccountRepository accountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final PartyLedgerEntryRepository partyLedgerEntryRepository;
    private final PaymentAllocationRepository paymentAllocationRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentSplitRepository paymentSplitRepository;
    private final InvoiceRepository invoiceRepository;
    private final OrderRepository orderRepository;
    private final BranchContextService branchContext;

    public List<AccountingAccount> getAccounts(boolean includeInactive) {
        UUID clientId = requireTenant();
        UUID orgId = TenantContext.getCurrentOrg();
        List<AccountingAccount> accounts;
        if (SecurityUtils.isSuperAdmin() || orgId == null) {
            accounts = accountRepository.findByClientIdOrderByCodeAsc(clientId);
        } else {
            accounts = accountRepository.findByClientIdAndOrgIdOrderByCodeAsc(clientId, orgId);
        }
        if (includeInactive) {
            return accounts;
        }
        return accounts.stream()
                .filter(account -> !"N".equalsIgnoreCase(account.getIsactive()))
                .collect(Collectors.toList());
    }

    public AccountingAccount getAccount(UUID id) {
        return getScopedAccount(id);
    }

    @Transactional
    public AccountingAccount createAccount(AccountingAccount account) {
        UUID clientId = requireTenant();
        UUID orgId = resolveOrg(account.getOrgId());
        account.setId(null);
        account.setClientId(clientId);
        account.setOrgId(orgId);
        normalizeAccount(account);
        ensureUniqueCode(clientId, orgId, account.getCode(), null);
        return accountRepository.save(account);
    }

    @Transactional
    public AccountingAccount updateAccount(UUID id, AccountingAccount updates) {
        AccountingAccount existing = getScopedAccount(id);
        UUID orgId = resolveOrg(updates.getOrgId() != null ? updates.getOrgId() : existing.getOrgId());
        String nextCode = normalizeCode(updates.getCode());
        ensureUniqueCode(existing.getClientId(), orgId, nextCode, existing.getId());

        existing.setOrgId(orgId);
        existing.setCode(nextCode);
        existing.setName(requireText(updates.getName(), "Account name is required"));
        existing.setAccountType(requireAccountType(updates.getAccountType()));
        existing.setAccountSubType(trimToNull(updates.getAccountSubType()));
        existing.setCurrencyId(updates.getCurrencyId());
        existing.setOpeningBalance(money(updates.getOpeningBalance()));
        existing.setCurrentBalance(money(updates.getCurrentBalance()));
        existing.setSystemAccount(Boolean.TRUE.equals(updates.getSystemAccount()));
        existing.setCashAccount(Boolean.TRUE.equals(updates.getCashAccount()));
        existing.setBankAccount(Boolean.TRUE.equals(updates.getBankAccount()));
        existing.setBankName(trimToNull(updates.getBankName()));
        existing.setAccountNumber(trimToNull(updates.getAccountNumber()));
        existing.setIfscCode(trimToNull(updates.getIfscCode()));
        existing.setUpiId(trimToNull(updates.getUpiId()));
        existing.setDescription(trimToNull(updates.getDescription()));
        existing.setIsactive(isActiveFlag(updates.getIsactive()));
        return accountRepository.save(existing);
    }

    @Transactional
    public void deactivateAccount(UUID id) {
        AccountingAccount account = getScopedAccount(id);
        account.setIsactive("N");
        accountRepository.save(account);
    }

    public List<JournalEntry> getJournalEntries(LocalDateTime from, LocalDateTime to) {
        return getJournalEntries(from, to, "entryDate", "DESC");
    }

    public List<JournalEntry> getJournalEntries(LocalDateTime from, LocalDateTime to, String sortBy, String sortDir) {
        DateRange range = boundedRange(from, to);
        UUID clientId = requireTenant();
        UUID orgId = TenantContext.getCurrentOrg();
        Specification<JournalEntry> spec = journalSpec(clientId, orgId, range);
        return journalEntryRepository.findAll(spec, journalSort(sortBy, sortDir));
    }

    @Transactional
    public JournalEntry createJournalEntry(JournalEntry entry) {
        UUID clientId = requireTenant();
        UUID orgId = resolveOrg(entry.getOrgId());
        List<JournalLine> incomingLines = entry.getLines() == null ? List.of() : new ArrayList<>(entry.getLines());
        if (incomingLines.isEmpty()) {
            throw new BusinessException("Journal entry must have at least one line");
        }

        entry.setId(null);
        entry.setClientId(clientId);
        entry.setOrgId(orgId);
        entry.setTerminalId(entry.getTerminalId() != null ? entry.getTerminalId() : TenantContext.getCurrentTerminal());
        entry.setEntryNo(requireJournalNo(entry.getEntryNo()));
        entry.setEntryDate(entry.getEntryDate() != null ? entry.getEntryDate() : LocalDateTime.now());
        entry.setStatus(entry.getStatus() != null ? entry.getStatus() : JournalStatus.POSTED);
        entry.setSourceType(trimToNull(entry.getSourceType()));
        entry.setSourceId(entry.getSourceId());
        entry.setReversalOfJournalEntryId(entry.getReversalOfJournalEntryId());
        entry.setAutoPosted(Boolean.TRUE.equals(entry.getAutoPosted()));
        entry.setDescription(trimToNull(entry.getDescription()));
        entry.setIsactive(isActiveFlag(entry.getIsactive()));
        if (entry.getLines() == null) {
            entry.setLines(new ArrayList<>());
        }
        entry.getLines().clear();

        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        Map<UUID, AccountingAccount> accountsById = loadAccountsForLines(clientId, orgId, incomingLines);

        for (JournalLine line : incomingLines) {
            line.setId(null);
            line.setDebit(money(line.getDebit()));
            line.setCredit(money(line.getCredit()));
            validateJournalLine(line, accountsById);
            totalDebit = totalDebit.add(line.getDebit());
            totalCredit = totalCredit.add(line.getCredit());
            entry.attachLine(line);
        }

        if (totalDebit.compareTo(BigDecimal.ZERO) <= 0 || totalDebit.compareTo(totalCredit) != 0) {
            throw new BusinessException("Journal debits and credits must be equal and greater than zero");
        }

        entry.setTotalDebit(totalDebit);
        entry.setTotalCredit(totalCredit);
        JournalEntry saved = journalEntryRepository.save(entry);
        applyAccountBalances(saved, accountsById);
        createPartyLedgerEntries(saved, accountsById);
        return saved;
    }

    public List<PartyLedgerEntry> getPartyLedger(PartyType partyType, UUID partyId) {
        UUID clientId = requireTenant();
        UUID orgId = TenantContext.getCurrentOrg();
        if (partyType == null || partyId == null) {
            throw new BusinessException("Party type and party id are required");
        }
        if (SecurityUtils.isSuperAdmin() || orgId == null) {
            return partyLedgerEntryRepository.findByClientIdAndPartyTypeAndPartyIdOrderByEntryDateDesc(clientId, partyType, partyId);
        }
        return partyLedgerEntryRepository.findByClientIdAndOrgIdAndPartyTypeAndPartyIdOrderByEntryDateDesc(clientId, orgId, partyType, partyId);
    }

    public List<TrialBalanceRowDto> getTrialBalance(LocalDateTime from, LocalDateTime to) {
        DateRange range = boundedRange(from, to);
        UUID clientId = requireTenant();
        UUID orgId = TenantContext.getCurrentOrg();
        List<AccountingAccount> accounts = getAccounts(true);
        Map<UUID, TrialBalanceRowDto> rows = accounts.stream()
                .collect(Collectors.toMap(
                        AccountingAccount::getId,
                        account -> TrialBalanceRowDto.builder()
                                .accountId(account.getId())
                                .code(account.getCode())
                                .name(account.getName())
                                .accountType(account.getAccountType())
                                .debit(BigDecimal.ZERO)
                                .credit(BigDecimal.ZERO)
                                .balance(BigDecimal.ZERO)
                                .build(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        journalEntryRepository.findAll(journalSpec(clientId, orgId, range)).stream()
                .filter(entry -> entry.getStatus() == JournalStatus.POSTED)
                .flatMap(entry -> entry.getLines().stream())
                .forEach(line -> {
                    TrialBalanceRowDto row = rows.get(line.getAccountId());
                    if (row == null) {
                        return;
                    }
                    BigDecimal debit = row.getDebit().add(money(line.getDebit()));
                    BigDecimal credit = row.getCredit().add(money(line.getCredit()));
                    row.setDebit(debit);
                    row.setCredit(credit);
                    row.setBalance(debit.subtract(credit));
                });

        List<TrialBalanceRowDto> result = new ArrayList<>(rows.values());
        if (orgId == null) {
            Map<String, List<TrialBalanceRowDto>> systemKeyGroups = new LinkedHashMap<>();
            List<TrialBalanceRowDto> nonSystemRows = new ArrayList<>();
            Map<UUID, String> accountSystemKeys = accounts.stream()
                    .filter(a -> a.getSystemKey() != null && !a.getSystemKey().isBlank())
                    .collect(Collectors.toMap(AccountingAccount::getId, AccountingAccount::getSystemKey));
            
            for (TrialBalanceRowDto row : result) {
                String sysKey = accountSystemKeys.get(row.getAccountId());
                if (sysKey != null) {
                    systemKeyGroups.computeIfAbsent(sysKey.toUpperCase(Locale.ROOT), k -> new ArrayList<>()).add(row);
                } else {
                    nonSystemRows.add(row);
                }
            }
            
            List<TrialBalanceRowDto> mergedSystemRows = new ArrayList<>();
            for (Map.Entry<String, List<TrialBalanceRowDto>> entry : systemKeyGroups.entrySet()) {
                List<TrialBalanceRowDto> group = entry.getValue();
                TrialBalanceRowDto first = group.get(0);
                BigDecimal totalDebit = BigDecimal.ZERO;
                BigDecimal totalCredit = BigDecimal.ZERO;
                BigDecimal totalBalance = BigDecimal.ZERO;
                for (TrialBalanceRowDto r : group) {
                    totalDebit = totalDebit.add(r.getDebit());
                    totalCredit = totalCredit.add(r.getCredit());
                    totalBalance = totalBalance.add(r.getBalance());
                }
                mergedSystemRows.add(TrialBalanceRowDto.builder()
                        .accountId(first.getAccountId())
                        .code(first.getCode())
                        .name(first.getName())
                        .accountType(first.getAccountType())
                        .debit(totalDebit)
                        .credit(totalCredit)
                        .balance(totalBalance)
                        .build());
            }
            List<TrialBalanceRowDto> finalRows = new ArrayList<>();
            finalRows.addAll(mergedSystemRows);
            finalRows.addAll(nonSystemRows);
            return finalRows;
        }
        return result;
    }

    public List<AccountingAccountPeriodDto> getPeriodAccounts(LocalDateTime from, LocalDateTime to, boolean includeInactive) {
        DateRange range = boundedRange(from, to);
        UUID clientId = requireTenant();
        UUID orgId = TenantContext.getCurrentOrg();
        List<AccountingAccount> accounts = getAccounts(true);
        Set<UUID> visibleAccountIds = accounts.stream()
                .filter(account -> includeInactive || !"N".equalsIgnoreCase(account.getIsactive()))
                .map(AccountingAccount::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<UUID, BigDecimal> openingEffects = zeroAccountMap(accounts);
        Map<UUID, BigDecimal> periodDebits = zeroAccountMap(accounts);
        Map<UUID, BigDecimal> periodCredits = zeroAccountMap(accounts);
        Map<UUID, AccountingAccount> accountsById = accounts.stream()
                .collect(Collectors.toMap(AccountingAccount::getId, Function.identity(), (left, right) -> left));

        journalEntryRepository.findAll(journalSpec(clientId, orgId, null, range.from.minusNanos(1))).stream()
                .filter(this::isPostedActiveJournal)
                .flatMap(entry -> entry.getLines().stream())
                .forEach(line -> {
                    AccountingAccount account = accountsById.get(line.getAccountId());
                    if (account != null) {
                        openingEffects.merge(account.getId(), accountBalanceEffect(account, money(line.getDebit()), money(line.getCredit())), BigDecimal::add);
                    }
                });

        journalEntryRepository.findAll(journalSpec(clientId, orgId, range.from, range.to)).stream()
                .filter(this::isPostedActiveJournal)
                .flatMap(entry -> entry.getLines().stream())
                .forEach(line -> {
                    if (!accountsById.containsKey(line.getAccountId())) {
                        return;
                    }
                    periodDebits.merge(line.getAccountId(), money(line.getDebit()), BigDecimal::add);
                    periodCredits.merge(line.getAccountId(), money(line.getCredit()), BigDecimal::add);
                });

        List<AccountingAccountPeriodDto> list = accounts.stream()
                .filter(account -> visibleAccountIds.contains(account.getId()))
                .map(account -> {
                    BigDecimal opening = money(account.getOpeningBalance()).add(openingEffects.getOrDefault(account.getId(), BigDecimal.ZERO));
                    BigDecimal debit = periodDebits.getOrDefault(account.getId(), BigDecimal.ZERO);
                    BigDecimal credit = periodCredits.getOrDefault(account.getId(), BigDecimal.ZERO);
                    BigDecimal periodNet = accountBalanceEffect(account, debit, credit);
                    return AccountingAccountPeriodDto.builder()
                            .id(account.getId())
                            .code(account.getCode())
                            .name(account.getName())
                            .accountType(account.getAccountType())
                            .accountSubType(account.getAccountSubType())
                            .systemKey(account.getSystemKey())
                            .cashAccount(account.getCashAccount())
                            .bankAccount(account.getBankAccount())
                            .isActive(account.getIsactive())
                            .openingBalance(money(account.getOpeningBalance()))
                            .periodDebit(debit)
                            .periodCredit(credit)
                            .periodNet(periodNet)
                            .periodOpening(opening)
                            .periodClosing(opening.add(periodNet))
                            .build();
                })
                .collect(Collectors.toList());

        if (orgId == null) {
            Map<String, List<AccountingAccountPeriodDto>> systemKeyGroups = new LinkedHashMap<>();
            List<AccountingAccountPeriodDto> nonSystemAccounts = new ArrayList<>();
            for (AccountingAccountPeriodDto dto : list) {
                if (dto.getSystemKey() != null && !dto.getSystemKey().isBlank()) {
                    systemKeyGroups.computeIfAbsent(dto.getSystemKey().toUpperCase(Locale.ROOT), k -> new ArrayList<>()).add(dto);
                } else {
                    nonSystemAccounts.add(dto);
                }
            }
            List<AccountingAccountPeriodDto> mergedAccounts = new ArrayList<>();
            for (Map.Entry<String, List<AccountingAccountPeriodDto>> entry : systemKeyGroups.entrySet()) {
                List<AccountingAccountPeriodDto> group = entry.getValue();
                AccountingAccountPeriodDto first = group.get(0);
                BigDecimal openingBalance = BigDecimal.ZERO;
                BigDecimal periodDebit = BigDecimal.ZERO;
                BigDecimal periodCredit = BigDecimal.ZERO;
                BigDecimal periodNet = BigDecimal.ZERO;
                BigDecimal periodOpening = BigDecimal.ZERO;
                BigDecimal periodClosing = BigDecimal.ZERO;
                for (AccountingAccountPeriodDto dto : group) {
                    openingBalance = openingBalance.add(dto.getOpeningBalance());
                    periodDebit = periodDebit.add(dto.getPeriodDebit());
                    periodCredit = periodCredit.add(dto.getPeriodCredit());
                    periodNet = periodNet.add(dto.getPeriodNet());
                    periodOpening = periodOpening.add(dto.getPeriodOpening());
                    periodClosing = periodClosing.add(dto.getPeriodClosing());
                }
                mergedAccounts.add(AccountingAccountPeriodDto.builder()
                        .id(first.getId())
                        .code(first.getCode())
                        .name(first.getName())
                        .accountType(first.getAccountType())
                        .accountSubType(first.getAccountSubType())
                        .systemKey(first.getSystemKey())
                        .cashAccount(first.getCashAccount())
                        .bankAccount(first.getBankAccount())
                        .isActive(first.getIsActive())
                        .openingBalance(openingBalance)
                        .periodDebit(periodDebit)
                        .periodCredit(periodCredit)
                        .periodNet(periodNet)
                        .periodOpening(periodOpening)
                        .periodClosing(periodClosing)
                        .build());
            }
            List<AccountingAccountPeriodDto> finalResult = new ArrayList<>();
            finalResult.addAll(mergedAccounts);
            finalResult.addAll(nonSystemAccounts);
            return finalResult;
        }
        return list;
    }

    public AccountingSummaryDto getSummary(LocalDateTime from, LocalDateTime to) {
        DateRange range = boundedRange(from, to);
        UUID clientId = requireTenant();
        UUID orgId = TenantContext.getCurrentOrg();
        List<AccountingAccountPeriodDto> accounts = getPeriodAccounts(range.from, range.to, true);
        Map<String, AccountingAccountPeriodDto> accountsBySystemKey = accounts.stream()
                .filter(account -> account.getSystemKey() != null && !account.getSystemKey().isBlank())
                .collect(Collectors.toMap(
                        account -> account.getSystemKey().toUpperCase(Locale.ROOT),
                        Function.identity(),
                        (left, right) -> left
                ));

        BigDecimal grossSales = movement(accountsBySystemKey, AccountingDefaultsService.SALES_REVENUE);
        BigDecimal discounts = movement(accountsBySystemKey, AccountingDefaultsService.DISCOUNT_ALLOWED).max(BigDecimal.ZERO);
        BigDecimal netSales = grossSales.subtract(discounts);
        BigDecimal outputTax = movement(accountsBySystemKey, AccountingDefaultsService.OUTPUT_TAX);
        BigDecimal inputTax = movement(accountsBySystemKey, AccountingDefaultsService.INPUT_TAX);
        BigDecimal roundOff = movement(accountsBySystemKey, AccountingDefaultsService.ROUND_OFF);
        BigDecimal billedTotal = netSales.add(outputTax).add(roundOff);
        BigDecimal operatingExpenses = BigDecimal.ZERO;
        for (AccountingAccountPeriodDto account : accounts) {
            if (account.getAccountType() == AccountType.EXPENSE) {
                String sysKey = account.getSystemKey();
                if (sysKey != null) {
                    String normKey = sysKey.toUpperCase(Locale.ROOT);
                    if (normKey.equals(AccountingDefaultsService.PURCHASE_COGS) ||
                        normKey.equals(AccountingDefaultsService.DISCOUNT_ALLOWED) ||
                        normKey.equals(AccountingDefaultsService.ROUND_OFF) ||
                        normKey.equals(AccountingDefaultsService.STOCK_ADJUSTMENT_GAIN_LOSS)) {
                        continue;
                    }
                }
                operatingExpenses = operatingExpenses.add(money(account.getPeriodNet()));
            }
        }
        BigDecimal cogsPurchases = movement(accountsBySystemKey, AccountingDefaultsService.PURCHASE_COGS);
        BigDecimal stockAdjustment = movement(accountsBySystemKey, AccountingDefaultsService.STOCK_ADJUSTMENT_GAIN_LOSS);
        BigDecimal expenses = operatingExpenses.add(roundOff.max(BigDecimal.ZERO));
        BigDecimal profit = netSales.subtract(cogsPurchases).subtract(operatingExpenses).subtract(roundOff.max(BigDecimal.ZERO)).subtract(stockAdjustment.max(BigDecimal.ZERO));
        BigDecimal receivable = closing(accountsBySystemKey, AccountingDefaultsService.ACCOUNTS_RECEIVABLE);
        BigDecimal payable = closing(accountsBySystemKey, AccountingDefaultsService.ACCOUNTS_PAYABLE);
        BigDecimal inventoryValue = closing(accountsBySystemKey, AccountingDefaultsService.INVENTORY_ASSET);

        Map<String, BigDecimal> paymentBreakdown = paymentBreakdown(clientId, orgId, range);
        BigDecimal paymentCollected = paymentBreakdown.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        long transactions = journalEntryRepository.findAll(journalSpec(clientId, orgId, range)).stream()
                .filter(this::isPostedActiveJournal)
                .count();

        return AccountingSummaryDto.builder()
                .from(range.from)
                .to(range.to)
                .transactions(transactions)
                .grossSales(grossSales)
                .discounts(discounts)
                .netSales(netSales)
                .outputTax(outputTax)
                .inputTax(inputTax)
                .billedTotal(billedTotal)
                .paymentCollected(paymentCollected)
                .cashCollected(paymentBreakdown.getOrDefault("CASH", BigDecimal.ZERO))
                .onlineCollected(paymentBreakdown.getOrDefault("ONLINE", BigDecimal.ZERO))
                .upiCollected(paymentBreakdown.getOrDefault("UPI", BigDecimal.ZERO))
                .cardCollected(paymentBreakdown.getOrDefault("CARD", BigDecimal.ZERO))
                .bankCollected(paymentBreakdown.getOrDefault("BANK", BigDecimal.ZERO))
                .chequeCollected(paymentBreakdown.getOrDefault("CHEQUE", BigDecimal.ZERO))
                .expenses(expenses)
                .cogsPurchases(cogsPurchases)
                .profit(profit)
                .receivable(receivable)
                .payable(payable)
                .inventoryValue(inventoryValue)
                .paymentBreakdown(paymentBreakdown)
                .build();
    }

    public AccountingReconciliationDto getReconciliation(LocalDateTime from, LocalDateTime to) {
        DateRange range = boundedRange(from, to);
        UUID clientId = requireTenant();
        UUID orgId = TenantContext.getCurrentOrg();
        List<Order> salesOrders = findCompletedSaleOrdersInPeriod(clientId, orgId, range);
        List<Invoice> invoices = findInvoicesInPeriodIncludingOrderPeriod(clientId, orgId, range, salesOrders);
        List<Payment> payments = findPaymentsInPeriodIncludingOrderPeriod(clientId, orgId, range, salesOrders);
        List<JournalEntry> entries = journalEntryRepository.findAll(journalSpec(clientId, orgId, range)).stream()
                .filter(this::isPostedActiveJournal)
                .toList();

        Set<UUID> postedInvoiceIds = entries.stream()
                .filter(entry -> Set.of(InvoiceType.CUSTOMER_INVOICE.name(), InvoiceType.VENDOR_BILL.name(), InvoiceType.EXPENSE_RECEIPT.name()).contains(entry.getSourceType()))
                .map(JournalEntry::getSourceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<UUID> postedPaymentIds = entries.stream()
                .filter(entry -> "INBOUND_PAYMENT".equals(entry.getSourceType()) || "OUTBOUND_PAYMENT".equals(entry.getSourceType()))
                .map(JournalEntry::getSourceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        long missingInvoices = invoices.stream().map(Invoice::getId).filter(id -> !postedInvoiceIds.contains(id)).count();
        long missingPayments = payments.stream().map(Payment::getId).filter(id -> !postedPaymentIds.contains(id)).count();
        List<String> warnings = new ArrayList<>();
        if (missingInvoices > 0) {
            warnings.add(missingInvoices + " invoice(s) in this period do not have accounting journal entries.");
        }
        if (missingPayments > 0) {
            warnings.add(missingPayments + " payment(s) in this period do not have accounting journal entries.");
        }
        Set<UUID> completedSalesOrderIds = salesOrders.stream()
                .map(Order::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, List<PaymentSplit>> splitsByPaymentId = loadPaymentSplitsByPaymentId(payments);
        BigDecimal billedSalesTotal = salesOrders.stream()
                .map(Order::getGrandTotal)
                .map(this::money)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal linkedSalesPaymentsTotal = BigDecimal.ZERO;
        BigDecimal otherActivePaymentsTotal = BigDecimal.ZERO;
        long unmatchedPaymentCount = 0;
        for (Payment payment : payments) {
            if (!isInboundPayment(payment)) {
                continue;
            }
            BigDecimal paymentAmount = paymentFinancialAmount(payment, splitsByPaymentId);
            if (paymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            UUID paymentOrderId = payment.getOrderId();
            if (paymentOrderId != null && completedSalesOrderIds.contains(paymentOrderId)) {
                linkedSalesPaymentsTotal = linkedSalesPaymentsTotal.add(paymentAmount);
            } else {
                otherActivePaymentsTotal = otherActivePaymentsTotal.add(paymentAmount);
                unmatchedPaymentCount++;
            }
        }
        BigDecimal paymentCollectedTotal = linkedSalesPaymentsTotal.add(otherActivePaymentsTotal);
        boolean hasOtherActivePayments = otherActivePaymentsTotal.compareTo(BigDecimal.ZERO) > 0;
        if (hasOtherActivePayments) {
            warnings.add(unmatchedPaymentCount + " active payment(s) worth " + otherActivePaymentsTotal
                    + " are not linked to completed sales in this period.");
        }

        return AccountingReconciliationDto.builder()
                .from(range.from)
                .to(range.to)
                .salesOrders(salesOrders.size())
                .invoices(invoices.size())
                .payments(payments.size())
                .postedInvoices(postedInvoiceIds.size())
                .postedPayments(postedPaymentIds.size())
                .missingInvoices(missingInvoices)
                .missingPayments(missingPayments)
                .billedSalesTotal(billedSalesTotal)
                .linkedSalesPaymentsTotal(linkedSalesPaymentsTotal)
                .otherActivePaymentsTotal(otherActivePaymentsTotal)
                .paymentCollectedTotal(paymentCollectedTotal)
                .unmatchedPaymentCount(unmatchedPaymentCount)
                .outOfSync(missingInvoices > 0 || missingPayments > 0 || hasOtherActivePayments)
                .warnings(warnings)
                .build();
    }

    @Transactional
    public PaymentAllocation allocatePayment(PaymentAllocation allocation) {
        UUID clientId = requireTenant();
        UUID orgId = resolveOrg(allocation.getOrgId());
        if (allocation.getPaymentId() == null) {
            throw new BusinessException("Payment id is required");
        }
        if (money(allocation.getAllocatedAmount()).compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Allocated amount must be greater than zero");
        }
        allocation.setId(null);
        allocation.setClientId(clientId);
        allocation.setOrgId(orgId);
        allocation.setAllocatedAmount(money(allocation.getAllocatedAmount()));
        allocation.setAllocationDate(allocation.getAllocationDate() != null ? allocation.getAllocationDate() : LocalDateTime.now());
        allocation.setStatus(trimToNull(allocation.getStatus()) == null ? "POSTED" : allocation.getStatus().trim().toUpperCase(Locale.ROOT));
        allocation.setIsactive(isActiveFlag(allocation.getIsactive()));
        return paymentAllocationRepository.save(allocation);
    }

    private List<Order> findCompletedSaleOrdersInPeriod(UUID clientId, UUID orgId, DateRange range) {
        Instant instantFrom = range.from.atZone(IST).toInstant();
        Instant instantTo = range.to.atZone(IST).toInstant();
        return orderRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("clientId"), clientId));
            if (!SecurityUtils.isSuperAdmin() && orgId != null) {
                predicates.add(cb.equal(root.get("orgId"), orgId));
            }
            predicates.add(cb.equal(root.get("orderType"), OrderType.SALE));
            predicates.add(cb.equal(root.get("orderStatus"), "COMPLETED"));
            predicates.add(cb.equal(root.get("isactive"), "Y"));
            predicates.add(cb.greaterThanOrEqualTo(root.get("orderDate"), instantFrom));
            predicates.add(cb.lessThanOrEqualTo(root.get("orderDate"), instantTo));
            return cb.and(predicates.toArray(new Predicate[0]));
        });
    }

    private List<Invoice> findInvoicesInPeriodIncludingOrderPeriod(UUID clientId, UUID orgId, DateRange range, Collection<Order> orders) {
        Map<UUID, Invoice> invoicesById = new LinkedHashMap<>();
        invoiceRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("clientId"), clientId));
            if (!SecurityUtils.isSuperAdmin() && orgId != null) {
                predicates.add(cb.equal(root.get("orgId"), orgId));
            }
            predicates.add(cb.greaterThanOrEqualTo(root.get("invoiceDate"), range.from));
            predicates.add(cb.lessThanOrEqualTo(root.get("invoiceDate"), range.to));
            predicates.add(cb.or(cb.isNull(root.get("status")), cb.notEqual(cb.upper(root.get("status").as(String.class)), "VOID")));
            predicates.add(cb.or(cb.isNull(root.get("docStatus")), cb.notEqual(cb.upper(root.get("docStatus").as(String.class)), "VOIDED")));
            return cb.and(predicates.toArray(new Predicate[0]));
        }).stream()
                .filter(invoice -> isScopedInvoice(invoice, clientId, orgId))
                .filter(this::isActiveInvoice)
                .forEach(invoice -> invoicesById.put(invoice.getId(), invoice));

        for (Order order : orders) {
            invoiceRepository.findByOrderId(order.getId()).stream()
                    .filter(invoice -> isScopedInvoice(invoice, clientId, orgId))
                    .filter(this::isActiveInvoice)
                    .forEach(invoice -> invoicesById.put(invoice.getId(), invoice));
        }
        return new ArrayList<>(invoicesById.values());
    }

    private List<Payment> findPaymentsInPeriodIncludingOrderPeriod(UUID clientId, UUID orgId, DateRange range, Collection<Order> orders) {
        Map<UUID, Payment> paymentsById = new LinkedHashMap<>();
        paymentRepository.findActivePaymentsInPeriod(clientId, SecurityUtils.isSuperAdmin() ? null : orgId, range.from, range.to).stream()
                .filter(payment -> isScopedPayment(payment, clientId, orgId))
                .filter(this::isActivePayment)
                .forEach(payment -> paymentsById.put(payment.getId(), payment));

        for (Order order : orders) {
            paymentRepository.findByOrderId(order.getId()).stream()
                    .filter(payment -> isScopedPayment(payment, clientId, orgId))
                    .filter(this::isActivePayment)
                    .forEach(payment -> paymentsById.put(payment.getId(), payment));
        }
        return new ArrayList<>(paymentsById.values());
    }

    private boolean isScopedInvoice(Invoice invoice, UUID clientId, UUID orgId) {
        return invoice != null
                && clientId.equals(invoice.getClientId())
                && (SecurityUtils.isSuperAdmin() || orgId == null || orgId.equals(invoice.getOrgId()));
    }

    private boolean isScopedPayment(Payment payment, UUID clientId, UUID orgId) {
        return payment != null
                && clientId.equals(payment.getClientId())
                && (SecurityUtils.isSuperAdmin() || orgId == null || orgId.equals(payment.getOrgId()));
    }

    private boolean isActiveInvoice(Invoice invoice) {
        return invoice != null
                && !"N".equalsIgnoreCase(invoice.getIsactive())
                && !"VOID".equalsIgnoreCase(invoice.getStatus())
                && !"VOID".equalsIgnoreCase(invoice.getDocStatus())
                && !"VOIDED".equalsIgnoreCase(invoice.getDocStatus());
    }

    private boolean isActivePayment(Payment payment) {
        return payment != null
                && !"N".equalsIgnoreCase(payment.getIsactive())
                && !"VOID".equalsIgnoreCase(payment.getStatus())
                && !"VOIDED".equalsIgnoreCase(payment.getStatus())
                && !"VOID".equalsIgnoreCase(payment.getDocStatus())
                && !"VOIDED".equalsIgnoreCase(payment.getDocStatus());
    }

    private boolean isInboundPayment(Payment payment) {
        return payment != null && (payment.getPaymentType() == null || payment.getPaymentType() == PaymentType.INBOUND);
    }

    private Specification<JournalEntry> journalSpec(UUID clientId, UUID orgId, DateRange range) {
        return journalSpec(clientId, orgId, range.from, range.to);
    }

    private Specification<JournalEntry> journalSpec(UUID clientId, UUID orgId, LocalDateTime from, LocalDateTime to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("clientId"), clientId));
            if (!SecurityUtils.isSuperAdmin() && orgId != null) {
                predicates.add(cb.equal(root.get("orgId"), orgId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("entryDate"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("entryDate"), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Sort journalSort(String sortBy, String sortDir) {
        String field = switch (sortBy != null ? sortBy.trim() : "") {
            case "entryNo" -> "entryNo";
            case "totalDebit" -> "totalDebit";
            case "totalCredit" -> "totalCredit";
            case "createdAt" -> "createdAt";
            default -> "entryDate";
        };
        Sort.Direction direction = "ASC".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(new Sort.Order(direction, field), new Sort.Order(direction, "createdAt"));
    }

    private boolean isPostedActiveJournal(JournalEntry entry) {
        return entry != null
                && entry.getStatus() == JournalStatus.POSTED
                && !"N".equalsIgnoreCase(entry.getIsactive());
    }

    private Map<UUID, BigDecimal> zeroAccountMap(List<AccountingAccount> accounts) {
        Map<UUID, BigDecimal> values = new LinkedHashMap<>();
        for (AccountingAccount account : accounts) {
            values.put(account.getId(), BigDecimal.ZERO);
        }
        return values;
    }

    private BigDecimal movement(Map<String, AccountingAccountPeriodDto> accountsBySystemKey, String systemKey) {
        AccountingAccountPeriodDto account = accountsBySystemKey.get(systemKey);
        return account != null ? money(account.getPeriodNet()) : BigDecimal.ZERO;
    }

    private BigDecimal closing(Map<String, AccountingAccountPeriodDto> accountsBySystemKey, String systemKey) {
        AccountingAccountPeriodDto account = accountsBySystemKey.get(systemKey);
        return account != null ? money(account.getPeriodClosing()) : BigDecimal.ZERO;
    }

    private Map<String, BigDecimal> paymentBreakdown(UUID clientId, UUID orgId, DateRange range) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        FINANCIAL_PAYMENT_METHODS.forEach(method -> totals.put(method, BigDecimal.ZERO));
        List<Order> salesOrders = findCompletedSaleOrdersInPeriod(clientId, orgId, range);
        List<Payment> payments = findPaymentsInPeriodIncludingOrderPeriod(clientId, orgId, range, salesOrders);
        Map<UUID, List<PaymentSplit>> splitsByPaymentId = loadPaymentSplitsByPaymentId(payments);
        for (Payment payment : payments) {
            if (!isInboundPayment(payment)) {
                continue;
            }
            List<PaymentSplit> splits = splitsByPaymentId.getOrDefault(payment.getId(), List.of());
            if (splits.isEmpty()) {
                addPaymentAmount(totals, normalizeFinancialPaymentMethod(payment.getPaymentMethod()), payment.getAmountPaid());
            } else {
                for (PaymentSplit split : splits) {
                    addPaymentAmount(totals, normalizeFinancialPaymentMethod(split.getPaymentMethod()), split.getAmount());
                }
            }
        }
        return totals;
    }

    private Map<UUID, List<PaymentSplit>> loadPaymentSplitsByPaymentId(Collection<Payment> payments) {
        Set<UUID> paymentIds = payments.stream()
                .filter(this::isInboundPayment)
                .map(Payment::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (paymentIds.isEmpty()) {
            return Map.of();
        }
        return paymentSplitRepository.findByPaymentIdInOrderByCreatedAtAsc(paymentIds).stream()
                .collect(Collectors.groupingBy(PaymentSplit::getPaymentId, LinkedHashMap::new, Collectors.toList()));
    }

    private BigDecimal paymentFinancialAmount(Payment payment, Map<UUID, List<PaymentSplit>> splitsByPaymentId) {
        if (!isInboundPayment(payment)) {
            return BigDecimal.ZERO;
        }
        List<PaymentSplit> splits = splitsByPaymentId.getOrDefault(payment.getId(), List.of());
        if (splits.isEmpty()) {
            return positiveMoney(payment.getAmountPaid());
        }
        return splits.stream()
                .map(PaymentSplit::getAmount)
                .map(this::positiveMoney)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void addPaymentAmount(Map<String, BigDecimal> totals, String method, BigDecimal amount) {
        BigDecimal value = positiveMoney(amount);
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        totals.merge(method, value, BigDecimal::add);
    }

    private BigDecimal positiveMoney(BigDecimal amount) {
        BigDecimal value = money(amount);
        return value.compareTo(BigDecimal.ZERO) > 0 ? value : BigDecimal.ZERO;
    }

    private String normalizeFinancialPaymentMethod(String paymentMethod) {
        if (paymentMethod == null || paymentMethod.isBlank()) {
            return "CASH";
        }
        String method = paymentMethod.trim().toUpperCase(Locale.ROOT);
        if (FINANCIAL_PAYMENT_METHODS.contains(method)) {
            return method;
        }
        if ("MIXED".equals(method)) {
            return "ONLINE";
        }
        return "CASH";
    }

    private AccountingAccount getScopedAccount(UUID id) {
        UUID clientId = requireTenant();
        UUID orgId = TenantContext.getCurrentOrg();
        if (SecurityUtils.isSuperAdmin() || orgId == null) {
            return accountRepository.findByIdAndClientId(id, clientId)
                    .orElseThrow(() -> new ResourceNotFoundException("Accounting account not found"));
        }
        return accountRepository.findByIdAndClientIdAndOrgId(id, clientId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Accounting account not found"));
    }

    private Map<UUID, AccountingAccount> loadAccountsForLines(UUID clientId, UUID orgId, List<JournalLine> lines) {
        Set<UUID> accountIds = lines.stream()
                .map(JournalLine::getAccountId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (accountIds.isEmpty()) {
            throw new BusinessException("Every journal line must use an account");
        }
        Map<UUID, AccountingAccount> accountsById = accountRepository.findAllById(accountIds).stream()
                .filter(account -> clientId.equals(account.getClientId()))
                .filter(account -> SecurityUtils.isSuperAdmin() || orgId == null || orgId.equals(account.getOrgId()))
                .collect(Collectors.toMap(AccountingAccount::getId, Function.identity()));
        if (accountsById.size() != accountIds.size()) {
            throw new BusinessException("One or more journal accounts are invalid for this branch");
        }
        return accountsById;
    }

    private void validateJournalLine(JournalLine line, Map<UUID, AccountingAccount> accountsById) {
        if (line.getAccountId() == null || !accountsById.containsKey(line.getAccountId())) {
            throw new BusinessException("Every journal line must use a valid account");
        }
        if (line.getDebit().compareTo(BigDecimal.ZERO) < 0 || line.getCredit().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("Journal debit and credit cannot be negative");
        }
        boolean hasDebit = line.getDebit().compareTo(BigDecimal.ZERO) > 0;
        boolean hasCredit = line.getCredit().compareTo(BigDecimal.ZERO) > 0;
        if (hasDebit == hasCredit) {
            throw new BusinessException("Each journal line must have either debit or credit");
        }
    }

    private void applyAccountBalances(JournalEntry saved, Map<UUID, AccountingAccount> accountsById) {
        for (JournalLine line : saved.getLines()) {
            AccountingAccount account = accountsById.get(line.getAccountId());
            BigDecimal effect = accountBalanceEffect(account, line.getDebit(), line.getCredit());
            account.setCurrentBalance(money(account.getCurrentBalance()).add(effect));
        }
        accountRepository.saveAll(accountsById.values());
    }

    private BigDecimal accountBalanceEffect(AccountingAccount account, BigDecimal debit, BigDecimal credit) {
        boolean debitNormal = account.getAccountType() == AccountType.ASSET || account.getAccountType() == AccountType.EXPENSE;
        return debitNormal ? debit.subtract(credit) : credit.subtract(debit);
    }

    private void createPartyLedgerEntries(JournalEntry saved, Map<UUID, AccountingAccount> accountsById) {
        List<PartyLedgerEntry> entries = new ArrayList<>();
        for (JournalLine line : saved.getLines()) {
            if (line.getPartyType() == null || line.getPartyId() == null) {
                continue;
            }
            BigDecimal previousBalance = partyLedgerEntryRepository
                    .findTopByClientIdAndOrgIdAndPartyTypeAndPartyIdOrderByEntryDateDescCreatedAtDesc(
                            saved.getClientId(),
                            saved.getOrgId(),
                            line.getPartyType(),
                            line.getPartyId()
                    )
                    .map(PartyLedgerEntry::getBalanceAfter)
                    .orElse(BigDecimal.ZERO);
            BigDecimal nextBalance = previousBalance.add(line.getDebit()).subtract(line.getCredit());
            entries.add(PartyLedgerEntry.builder()
                    .partyType(line.getPartyType())
                    .partyId(line.getPartyId())
                    .accountId(accountsById.get(line.getAccountId()).getId())
                    .journalEntryId(saved.getId())
                    .sourceType(saved.getSourceType())
                    .sourceId(saved.getSourceId())
                    .entryDate(saved.getEntryDate())
                    .debit(line.getDebit())
                    .credit(line.getCredit())
                    .balanceAfter(nextBalance)
                    .description(line.getDescription())
                    .isactive("Y")
                    .build());
        }
        entries.forEach(entry -> {
            entry.setClientId(saved.getClientId());
            entry.setOrgId(saved.getOrgId());
        });
        partyLedgerEntryRepository.saveAll(entries);
    }

    private void normalizeAccount(AccountingAccount account) {
        account.setCode(normalizeCode(account.getCode()));
        account.setName(requireText(account.getName(), "Account name is required"));
        account.setAccountType(requireAccountType(account.getAccountType()));
        account.setAccountSubType(trimToNull(account.getAccountSubType()));
        account.setSystemKey(trimToNull(account.getSystemKey()));
        account.setOpeningBalance(money(account.getOpeningBalance()));
        if (account.getCurrentBalance() == null) {
            account.setCurrentBalance(account.getOpeningBalance());
        } else {
            account.setCurrentBalance(money(account.getCurrentBalance()));
        }
        account.setSystemAccount(Boolean.TRUE.equals(account.getSystemAccount()));
        account.setCashAccount(Boolean.TRUE.equals(account.getCashAccount()));
        account.setBankAccount(Boolean.TRUE.equals(account.getBankAccount()));
        account.setBankName(trimToNull(account.getBankName()));
        account.setAccountNumber(trimToNull(account.getAccountNumber()));
        account.setIfscCode(trimToNull(account.getIfscCode()));
        account.setUpiId(trimToNull(account.getUpiId()));
        account.setDescription(trimToNull(account.getDescription()));
        account.setIsactive(isActiveFlag(account.getIsactive()));
    }

    private void ensureUniqueCode(UUID clientId, UUID orgId, String code, UUID existingId) {
        boolean duplicate = existingId == null
                ? accountRepository.existsByClientIdAndOrgIdAndCodeIgnoreCase(clientId, orgId, code)
                : accountRepository.existsByClientIdAndOrgIdAndCodeIgnoreCaseAndIdNot(clientId, orgId, code, existingId);
        if (duplicate) {
            throw new BusinessException("An accounting account with this code already exists");
        }
    }

    private DateRange boundedRange(LocalDateTime from, LocalDateTime to) {
        LocalDateTime resolvedTo = to != null ? to : LocalDateTime.now();
        LocalDateTime resolvedFrom = from != null ? from : resolvedTo.minusDays(DEFAULT_REPORT_DAYS);
        if (resolvedFrom.isAfter(resolvedTo)) {
            throw new BusinessException("From date must be before to date");
        }
        if (Duration.between(resolvedFrom, resolvedTo).toDays() > MAX_REPORT_DAYS) {
            throw new BusinessException("Accounting date range cannot exceed " + MAX_REPORT_DAYS + " days");
        }
        return new DateRange(resolvedFrom, resolvedTo);
    }

    private UUID requireTenant() {
        UUID clientId = TenantContext.getCurrentTenant();
        if (clientId == null) {
            throw new BusinessException("Client context is required");
        }
        return clientId;
    }

    private UUID resolveOrg(UUID requestedOrgId) {
        return branchContext.requireWriteOrgId(requestedOrgId);
    }

    private String normalizeCode(String value) {
        return requireText(value, "Account code is required").toUpperCase(Locale.ROOT);
    }

    private String requireText(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new BusinessException(message);
        }
        return trimmed;
    }

    private AccountType requireAccountType(AccountType accountType) {
        if (accountType == null) {
            throw new BusinessException("Account type is required");
        }
        return accountType;
    }

    private String requireJournalNo(String entryNo) {
        String normalized = trimToNull(entryNo);
        return normalized != null ? normalized : "JE-" + System.currentTimeMillis();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String isActiveFlag(String value) {
        return "N".equalsIgnoreCase(value) ? "N" : "Y";
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
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
