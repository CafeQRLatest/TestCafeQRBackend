package com.restaurant.pos.category.service;

import com.restaurant.pos.category.domain.ExpenseCategory;
import com.restaurant.pos.category.dto.CategoryResponse;
import com.restaurant.pos.category.dto.CreateCategoryRequest;
import com.restaurant.pos.category.mapper.CategoryMapper;
import com.restaurant.pos.category.repository.ExpenseCategoryRepository;
import com.restaurant.pos.common.exception.DuplicateResourceException;
import com.restaurant.pos.common.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

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
    private CategoryService categoryService;
    private UUID clientId;
    private UUID orgId;

    @BeforeEach
    void setUp() {
        categoryRepository = mock(ExpenseCategoryRepository.class);
        categoryService = new CategoryService(categoryRepository, new CategoryMapper());

        clientId = UUID.randomUUID();
        orgId = UUID.randomUUID();
        TenantContext.setCurrentTenant(clientId);
        TenantContext.setCurrentOrg(orgId);
        authenticate("profile-a@example.com");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCategoriesOnlyLoadsCurrentProfileCategories() {
        ExpenseCategory ownCategory = category("Wages", "profile-a@example.com");
        when(categoryRepository.findByClientIdAndOrgIdAndCreatedByOrderBySortOrderAsc(
                clientId,
                orgId,
                "profile-a@example.com"
        )).thenReturn(List.of(ownCategory));

        List<CategoryResponse> categories = categoryService.getCategories();

        assertThat(categories).hasSize(1);
        assertThat(categories.get(0).getName()).isEqualTo("Wages");
        verify(categoryRepository).findByClientIdAndOrgIdAndCreatedByOrderBySortOrderAsc(
                clientId,
                orgId,
                "profile-a@example.com"
        );
    }

    @Test
    void createCategoryAllowsSameNameForDifferentProfile() {
        authenticate("profile-b@example.com");
        when(categoryRepository.existsByNameIgnoreCaseAndClientIdAndOrgIdAndCreatedBy(
                "Wages",
                clientId,
                orgId,
                "profile-b@example.com"
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
        assertThat(saved.getCreatedBy()).isEqualTo("profile-b@example.com");
        assertThat(saved.getUpdatedBy()).isEqualTo("profile-b@example.com");
    }

    @Test
    void createCategoryRejectsDuplicateNameForSameProfile() {
        when(categoryRepository.existsByNameIgnoreCaseAndClientIdAndOrgIdAndCreatedBy(
                "Wages",
                clientId,
                orgId,
                "profile-a@example.com"
        )).thenReturn(true);

        assertThatThrownBy(() -> categoryService.createCategory(CreateCategoryRequest.builder()
                .name("Wages")
                .sortOrder(1)
                .build()))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already exists in this profile");

        verify(categoryRepository, never()).save(any(ExpenseCategory.class));
    }

    @Test
    void updateCategoryRejectsAnotherProfileCategory() {
        UUID categoryId = UUID.randomUUID();
        ExpenseCategory otherProfileCategory = category("Wages", "profile-b@example.com");
        otherProfileCategory.setId(categoryId);
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(otherProfileCategory));

        assertThatThrownBy(() -> categoryService.updateCategory(
                categoryId,
                com.restaurant.pos.category.dto.UpdateCategoryRequest.builder()
                        .name("Wages")
                        .sortOrder(1)
                        .active(true)
                        .build()
        )).isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("profile-specific category");

        verify(categoryRepository, never()).save(any(ExpenseCategory.class));
    }

    @Test
    void deleteCategoryRejectsAnotherProfileCategory() {
        UUID categoryId = UUID.randomUUID();
        ExpenseCategory otherProfileCategory = category("Wages", "profile-b@example.com");
        otherProfileCategory.setId(categoryId);
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(otherProfileCategory));

        assertThatThrownBy(() -> categoryService.deleteCategory(categoryId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("profile-specific category");

        verify(categoryRepository, never()).save(any(ExpenseCategory.class));
    }

    private ExpenseCategory category(String name, String owner) {
        ExpenseCategory category = ExpenseCategory.builder()
                .id(UUID.randomUUID())
                .name(name)
                .sortOrder(1)
                .isactive("Y")
                .build();
        category.setClientId(clientId);
        category.setOrgId(orgId);
        category.setCreatedBy(owner);
        category.setUpdatedBy(owner);
        return category;
    }

    private void authenticate(String email) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                email,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        ));
    }
}
