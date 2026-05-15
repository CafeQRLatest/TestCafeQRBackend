package com.restaurant.pos.accounting.controller;

import com.restaurant.pos.accounting.domain.*;
import com.restaurant.pos.accounting.dto.TrialBalanceRowDto;
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

    @GetMapping("/accounts")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<AccountingAccount>>> getAccounts(
            @RequestParam(defaultValue = "false") boolean includeInactive
    ) {
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
}
