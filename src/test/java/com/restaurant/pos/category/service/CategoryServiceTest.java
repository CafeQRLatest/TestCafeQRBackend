package com.restaurant.pos.category.service;

import com.restaurant.pos.category.domain.ExpenseCategory;
import com.restaurant.pos.category.dto.CategoryResponse;
import com.restaurant.pos.category.dto.CreateCategoryRequest;
import com.restaurant.pos.category.mapper.CategoryMapper;
import com.restaurant.pos.category.repository.ExpenseCategoryRepository;
import com.restaurant.pos.common.context.ContextProvider;
import com.restaurant.pos.common.exception.DuplicateResourceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CategoryServiceTest {

    private ExpenseCategoryRepository categoryRepository;
    private ContextProvider contextProvider;
    private ExpenseCategoryPolicy categoryPolicy;
    private CategoryService categoryService;
    
    private UUID clientId;
    private UUID orgId;
    private UUID currentUserId;

    @BeforeEach
    void setUp() {
        categoryRepository = mock(ExpenseCategoryRepository.class);
        contextProvider = mock(ContextProvider.class);
        categoryPolicy = new ExpenseCategoryPolicy(); // Pure, stateless - use real policy!
        categoryService = new CategoryService(categoryRepository, new CategoryMapper(), contextProvider, categoryPolicy);

        clientId = UUID.randomUUID();
        orgId = UUID.randomUUID();
        
        when(contextProvider.getCurrentTenant()).thenReturn(clientId);
        when(contextProvider.getCurrentOrg()).thenReturn(orgId);
        when(contextProvider.getCurrentTerminal()).thenReturn(UUID.randomUUID());
        when(contextProvider.getCurrentTimezone()).thenReturn(java.time.ZoneId.of("Asia/Kolkata"));
        authenticate("profile-a@example.com");
    }

    @Test
    void getCategoriesLoadsBranchCategories() {
        ExpenseCategory ownCategory = category("Wages", UUID.randomUUID().toString());
        when(categoryRepository.findByClientIdAndOrgIdOrderBySortOrderAsc(
                clientId,
                orgId
        )).thenReturn(List.of(ownCategory));

        List<CategoryResponse> categories = categoryService.getCategories(null, null);

        assertThat(categories).hasSize(1);
        assertThat(categories.get(0).getName()).isEqualTo("Wages");
        verify(categoryRepository).findByClientIdAndOrgIdOrderBySortOrderAsc(
                clientId,
                orgId
        );
    }

    @Test
    void createCategoryAllowsSameNameForDifferentBranch() {
        when(categoryRepository.existsByNameIgnoreCaseAndClientIdAndOrgId(
                "Wages",
                clientId,
                orgId
        )).thenReturn(false);
        when(categoryRepository.save(any(ExpenseCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CategoryResponse response = categoryService.createCategory(CreateCategoryRequest.builder()
                .name("Wages")
                .sortOrder(9)
                .build());

        assertThat(response.getName()).isEqualTo("Wages");

        ArgumentCaptor<ExpenseCategory> categoryCaptor = ArgumentCaptor.forClass(ExpenseCategory.class);
        verify(categoryRepository).save(categoryCaptor.capture());
        ExpenseCategory saved = categoryCaptor.getValue();
        assertThat(saved.getClientId()).isEqualTo(clientId);
        assertThat(saved.getOrgId()).isEqualTo(orgId);
        assertThat(saved.getCreatedBy()).isEqualTo(currentUserId.toString());
        assertThat(saved.getUpdatedBy()).isEqualTo(currentUserId.toString());
    }

    @Test
    void createCategoryRejectsDuplicateNameForSameBranch() {
        when(categoryRepository.existsByNameIgnoreCaseAndClientIdAndOrgId(
                "Wages",
                clientId,
                orgId
        )).thenReturn(true);

        assertThatThrownBy(() -> categoryService.createCategory(CreateCategoryRequest.builder()
                .name("Wages")
                .sortOrder(1)
                .build()))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already exists in this branch");

        verify(categoryRepository, never()).save(any(ExpenseCategory.class));
    }

    @Test
    void updateCategoryRejectsAnotherBranchCategory() {
        UUID categoryId = UUID.randomUUID();
        ExpenseCategory otherBranchCategory = category("Wages", UUID.randomUUID().toString());
        otherBranchCategory.setId(categoryId);
        otherBranchCategory.setOrgId(UUID.randomUUID());
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(otherBranchCategory));

        // Stub role checking for validateOwnership
        when(contextProvider.hasRole("ADMIN")).thenReturn(false);
        when(contextProvider.isSuperAdmin()).thenReturn(false);

        assertThatThrownBy(() -> categoryService.updateCategory(
                categoryId,
                com.restaurant.pos.category.dto.UpdateCategoryRequest.builder()
                        .name("Wages")
                        .sortOrder(1)
                        .active(true)
                        .build()
        )).isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("branch-specific category");

        verify(categoryRepository, never()).save(any(ExpenseCategory.class));
    }

    @Test
    void deactivateCategoryRejectsAnotherBranchCategory() {
        UUID categoryId = UUID.randomUUID();
        ExpenseCategory otherBranchCategory = category("Wages", UUID.randomUUID().toString());
        otherBranchCategory.setId(categoryId);
        otherBranchCategory.setOrgId(UUID.randomUUID());
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(otherBranchCategory));

        // Stub role checking for validateOwnership
        when(contextProvider.hasRole("ADMIN")).thenReturn(false);
        when(contextProvider.isSuperAdmin()).thenReturn(false);

        assertThatThrownBy(() -> categoryService.deactivateCategory(categoryId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("branch-specific category");

        verify(categoryRepository, never()).save(any(ExpenseCategory.class));
    }

    private ExpenseCategory category(String name, String owner) {
        ExpenseCategory category = ExpenseCategory.builder()
                .id(UUID.randomUUID())
                .name(name)
                .sortOrder(1)
                .isActive("Y")
                .build();
        category.setClientId(clientId);
        category.setOrgId(orgId);
        category.setCreatedBy(owner);
        category.setUpdatedBy(owner);
        return category;
    }

    private void authenticate(String email) {
        currentUserId = UUID.randomUUID();
        when(contextProvider.getCurrentUserId()).thenReturn(currentUserId);
        when(contextProvider.getCurrentUserEmail()).thenReturn(email);
        when(contextProvider.hasRole("ADMIN")).thenReturn(true);
    }
}
