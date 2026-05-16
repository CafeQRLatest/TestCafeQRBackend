package com.restaurant.pos.accounting.controller;

import com.restaurant.pos.accounting.domain.*;
import com.restaurant.pos.accounting.dto.AccountingBackfillRequest;
import com.restaurant.pos.accounting.dto.AccountingBackfillResponse;
import com.restaurant.pos.accounting.dto.AccountingMappingsDto;
import com.restaurant.pos.accounting.dto.AccountingPostingErrorDto;
import com.restaurant.pos.accounting.dto.TrialBalanceRowDto;
import com.restaurant.pos.accounting.service.AccountingDefaultsService;
import com.restaurant.pos.accounting.service.AccountingPostingService;
import com.restaurant.pos.accounting.service.AccountingService;
import com.restaurant.pos.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounting")
@RequiredArgsConstructor
public class AccountingController {

    private final AccountingService accountingService;
    private final AccountingDefaultsService defaultsService;
    private final AccountingPostingService postingService;

    @GetMapping("/accounts")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<AccountingAccount>>> getAccounts(
            @RequestParam(defaultValue = "false") boolean includeInactive
    ) {
        defaultsService.ensureDefaultAccounts();
        return ResponseEntity.ok(ApiResponse.success(accountingService.getAccounts(includeInactive)));
    }

    @GetMapping("/accounts/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<AccountingAccount>> getAccount(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(accountingService.getAccount(id)));
    }

    @PostMapping("/accounts")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<AccountingAccount>> createAccount(@RequestBody AccountingAccount account) {
        return ResponseEntity.ok(ApiResponse.success("Account created", accountingService.createAccount(account)));
    }

    @PutMapping("/accounts/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<AccountingAccount>> updateAccount(
            @PathVariable UUID id,
            @RequestBody AccountingAccount account
    ) {
        return ResponseEntity.ok(ApiResponse.success("Account updated", accountingService.updateAccount(id, account)));
    }

    @DeleteMapping("/accounts/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivateAccount(@PathVariable UUID id) {
        accountingService.deactivateAccount(id);
        return ResponseEntity.ok(ApiResponse.success("Account deactivated", null));
    }

    @GetMapping("/journals")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<JournalEntry>>> getJournalEntries(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        return ResponseEntity.ok(ApiResponse.success(accountingService.getJournalEntries(from, to)));
    }

    @PostMapping("/journals")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<JournalEntry>> createJournalEntry(@RequestBody JournalEntry entry) {
        return ResponseEntity.ok(ApiResponse.success("Journal posted", accountingService.createJournalEntry(entry)));
    }

    @GetMapping("/party-ledger")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<PartyLedgerEntry>>> getPartyLedger(
            @RequestParam PartyType partyType,
            @RequestParam UUID partyId
    ) {
        return ResponseEntity.ok(ApiResponse.success(accountingService.getPartyLedger(partyType, partyId)));
    }

    @GetMapping("/trial-balance")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<TrialBalanceRowDto>>> getTrialBalance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        return ResponseEntity.ok(ApiResponse.success(accountingService.getTrialBalance(from, to)));
    }

    @PostMapping("/payment-allocations")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<PaymentAllocation>> allocatePayment(@RequestBody PaymentAllocation allocation) {
        return ResponseEntity.ok(ApiResponse.success("Payment allocated", accountingService.allocatePayment(allocation)));
    }

    @PostMapping("/defaults/ensure")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<AccountingAccount>>> ensureDefaults() {
        return ResponseEntity.ok(ApiResponse.success("Accounting defaults ensured", defaultsService.ensureDefaultAccounts()));
    }

    @GetMapping("/mappings")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<AccountingMappingsDto>> getMappings() {
        defaultsService.ensureDefaultAccounts();
        return ResponseEntity.ok(ApiResponse.success(defaultsService.getMappings()));
    }

    @PutMapping("/mappings")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<AccountingMappingsDto>> updateMappings(@RequestBody AccountingMappingsDto mappings) {
        return ResponseEntity.ok(ApiResponse.success("Accounting mappings updated", defaultsService.updateMappings(mappings)));
    }

    @GetMapping("/posting-errors")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<AccountingPostingErrorDto>>> getPostingErrors() {
        return ResponseEntity.ok(ApiResponse.success(postingService.getPostingErrors()));
    }

    @PostMapping("/posting-errors/{id}/retry")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<AccountingPostingJob>> retryPosting(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Accounting posting retry queued", postingService.retryPosting(id)));
    }

    @PostMapping("/backfill")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<AccountingBackfillResponse>> backfill(@RequestBody AccountingBackfillRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Accounting backfill completed", postingService.backfill(request)));
    }

    @PostMapping("/resync-all")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<AccountingBackfillResponse>> resyncAll() {
        return ResponseEntity.ok(ApiResponse.success("Invoice discounts fixed", postingService.fixInvoiceDiscounts()));
    }
}
