package com.restaurant.pos.expense.controller;

import com.restaurant.pos.common.dto.ApiResponse;
import com.restaurant.pos.common.security.AdminAccess;
import com.restaurant.pos.common.security.StaffAccess;
import com.restaurant.pos.expense.dto.CreateExpenseRequest;
import com.restaurant.pos.expense.dto.UpdateExpenseRequest;
import com.restaurant.pos.expense.dto.ExpenseResponse;
import com.restaurant.pos.expense.dto.VoidExpenseResponse;
import com.restaurant.pos.expense.dto.ExpenseSearchCriteria;
import com.restaurant.pos.expense.service.ExpenseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/expenses")
@RequiredArgsConstructor
@Tag(
        name = "Expense Management",
        description = "APIs for expense categories and expense transaction management"
)
public class ExpenseController {

    private final ExpenseService expenseService;

    @GetMapping
    @StaffAccess
    @Operation(
            summary = "Fetch expenses",
            description = "Returns paginated expense records with filtering support for audit, reporting, and operational review. Use 'expenseDate' for sorting — it maps to the internal date field automatically."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully retrieved paginated list"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<ApiResponse<Page<ExpenseResponse>>> getExpenses(
            @ParameterObject ExpenseSearchCriteria criteria,

            @PageableDefault(
                    size = 20,
                    sort = "orderDate",
                    direction = Sort.Direction.DESC
            )
            Pageable pageable
    ) {
        Page<ExpenseResponse> response =
                expenseService.getExpenses(criteria, pageable);

        log.info("Fetched expenses | count={} | totalPages={}",
                response.getNumberOfElements(),
                response.getTotalPages());

        return ResponseEntity.ok(
                ApiResponse.success(response)
        );
    }

    @GetMapping("/{id}")
    @StaffAccess
    @Operation(
            summary = "Get expense by ID",
            description = "Retrieves the full details of a specific expense record, including its associated category and document status."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Found the expense"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Expense not found")
    })
    public ResponseEntity<ApiResponse<ExpenseResponse>> getExpenseById(
            @PathVariable UUID id
    ) {
        try (var mdc = MDC.putCloseable("expenseId", String.valueOf(id))) {
            ExpenseResponse response = expenseService.getExpenseById(id);
            log.info("Fetched expense | id={}", id);
            return ResponseEntity.ok(ApiResponse.success(response));
        }
    }

    @PostMapping
    @StaffAccess
    @Operation(
            summary = "Create expense",
            description = "Atomically creates a financial triplet: Order + Invoice + Payment. All records are created in a single transaction with a 5-second timeout. Pass an 'Idempotency-Key' header to prevent duplicate transactions on network retries."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Expense created successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid input data or missing Idempotency-Key"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Idempotency conflict - duplicate request detected"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Validation failure")
    })
    public ResponseEntity<ApiResponse<ExpenseResponse>> createExpense(
            @Parameter(description = "Unique key to ensure request idempotency")
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,

            @Valid @RequestBody CreateExpenseRequest request
    ) {
        try (
            var mdcBranch   = MDC.putCloseable("branchId",   String.valueOf(request.getBranchId()));
            var mdcCategory = MDC.putCloseable("categoryId", String.valueOf(request.getCategoryId()));
            var mdcAmount   = MDC.putCloseable("amount",     String.valueOf(request.getAmount()))
        ) {
            ExpenseResponse response = expenseService.createExpense(idempotencyKey, request);

            log.info("Expense created successfully | id={} | ref={}", response.getId(), response.getReferenceNumber());

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(ApiResponse.success(response));
        }
    }

    @PutMapping("/{id}")
    @AdminAccess
    @Operation(
            summary = "Update expense",
            description = "Updates an existing expense by voiding the old record and creating a new revision for audit consistency. Payment records are re-linked if the amount remains identical."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Revision created successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Original expense not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Business rule violation")
    })
    public ResponseEntity<ApiResponse<ExpenseResponse>> updateExpense(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateExpenseRequest request
    ) {
        try (
            var mdcExpense = MDC.putCloseable("expenseId", String.valueOf(id));
            var mdcAmount  = MDC.putCloseable("amount",    String.valueOf(request.getAmount()))
        ) {
            ExpenseResponse response = expenseService.updateExpense(id, request);

            log.info("Expense revision finalized | oldId={} | newId={}", id, response.getId());

            return ResponseEntity.ok(ApiResponse.success(response));
        }
    }

    @DeleteMapping("/{id}")
    @AdminAccess
    @Operation(
            summary = "Void expense",
            description = "Soft-deletes an expense and its associated financial chain (Invoices, Payments). Marks all records as VOID and inactive to preserve the audit trail."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Audit receipt for voided transaction"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Expense not found")
    })
    public ResponseEntity<ApiResponse<VoidExpenseResponse>> deleteExpense(@PathVariable UUID id) {
        VoidExpenseResponse receipt = expenseService.voidExpense(id);

        log.info("Expense and financial chain voided | id={} | paymentsCount={}",
                id,
                receipt.getPaymentIds().size());

        return ResponseEntity.ok(ApiResponse.success(receipt));
    }
}