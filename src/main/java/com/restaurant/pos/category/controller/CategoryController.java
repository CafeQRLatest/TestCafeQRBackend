package com.restaurant.pos.category.controller;

import com.restaurant.pos.common.dto.ApiResponse;
import com.restaurant.pos.common.security.StaffAccess;
import com.restaurant.pos.category.dto.CategoryResponse;
import com.restaurant.pos.category.dto.CreateCategoryRequest;
import com.restaurant.pos.category.dto.UpdateCategoryRequest;
import com.restaurant.pos.category.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/expense-categories")
@RequiredArgsConstructor
@Tag(
        name = "Expense Category Management",
        description = "APIs for expense categories classification"
)
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @StaffAccess
    @Operation(
            summary = "Fetch expense categories",
            description = "Returns all expense categories available for the current authenticated profile, including active/inactive records ordered by sort priority."
    )
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getCategories(
            @RequestParam(value = "scope", required = false) String scope,
            @RequestParam(value = "branchId", required = false) UUID branchId
    ) {

        log.info("Fetching expense categories | scope={} | branchId={}", scope, branchId);

        List<CategoryResponse> response = categoryService.getCategories(scope, branchId);

        return ResponseEntity.ok(
                ApiResponse.success(response)
        );
    }

    @PostMapping
    @StaffAccess
    @Operation(
            summary = "Create expense category",
            description = "Creates a new profile-specific expense category for expense classification and reporting."
    )
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
            @Valid @RequestBody CreateCategoryRequest request
    ) {

        log.info(
                "Creating expense category | name={} | sortOrder={}",
                request.getName(),
                request.getSortOrder()
        );

        CategoryResponse response = categoryService.createCategory(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        ApiResponse.success(response)
                );
    }

    @PutMapping("/{id}")
    @StaffAccess
    @Operation(
            summary = "Update expense category",
            description = "Updates an existing expense category."
    )
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCategoryRequest request
    ) {

        log.info(
                "Updating expense category | categoryId={} | newName={}",
                id,
                request.getName()
        );

        CategoryResponse response = categoryService.updateCategory(id, request);

        return ResponseEntity.ok(
                ApiResponse.success(response)
        );
    }

    @DeleteMapping("/{id}")
    @StaffAccess
    @Operation(
            summary = "Deactivate expense category",
            description = "Soft deletes/deactivates an expense category by marking it inactive."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Category deactivated successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Category not found")
    })
    public ResponseEntity<Void> deactivateCategory(
            @PathVariable UUID id
    ) {

        log.info(
                "Deactivating expense category | categoryId={}",
                id
        );

        categoryService.deactivateCategory(id);

        return ResponseEntity.noContent().build();
    }
}
