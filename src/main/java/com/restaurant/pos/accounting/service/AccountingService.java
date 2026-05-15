package com.restaurant.pos.accounting.service;

import com.restaurant.pos.accounting.domain.*;
import com.restaurant.pos.accounting.dto.TrialBalanceRowDto;
import com.restaurant.pos.accounting.repository.*;
import com.restaurant.pos.common.exception.BusinessException;
import com.restaurant.pos.common.exception.ResourceNotFoundException;
import com.restaurant.pos.common.tenant.TenantContext;
import com.restaurant.pos.common.util.SecurityUtils;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccountingService {

    private static final int DEFAULT_REPORT_DAYS = 31;
    private static final int MAX_REPORT_DAYS = 366;

    private final AccountingAccountRepository accountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final PartyLedgerEntryRepository partyLedgerEntryRepository;
    private final PaymentAllocationRepository paymentAllocationRepository;

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
        DateRange range = boundedRange(from, to);
        UUID clientId = requireTenant();
        UUID orgId = TenantContext.getCurrentOrg();
        Specification<JournalEntry> spec = journalSpec(clientId, orgId, range);
        return journalEntryRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "entryDate", "createdAt"));
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

        return new ArrayList<>(rows.values());
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

    private Specification<JournalEntry> journalSpec(UUID clientId, UUID orgId, DateRange range) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("clientId"), clientId));
            if (!SecurityUtils.isSuperAdmin() && orgId != null) {
                predicates.add(cb.equal(root.get("orgId"), orgId));
            }
            predicates.add(cb.greaterThanOrEqualTo(root.get("entryDate"), range.from));
            predicates.add(cb.lessThanOrEqualTo(root.get("entryDate"), range.to));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
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
        if (SecurityUtils.isSuperAdmin() && requestedOrgId != null) {
            return requestedOrgId;
        }
        UUID currentOrg = TenantContext.getCurrentOrg();
        return currentOrg != null ? currentOrg : requestedOrgId;
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
