package com.restaurant.pos.category.service;

import com.restaurant.pos.common.context.ContextProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pure unit test suite for ExpenseCategoryPolicy.
 * Runs instantly without requiring any heavyweight Spring container contexts.
 */
class ExpenseCategoryPolicyTest {

    private ExpenseCategoryPolicy policy;
    private ContextProvider context;

    @BeforeEach
    void setUp() {
        policy = new ExpenseCategoryPolicy();
        context = mock(ContextProvider.class);
    }

    @Test
    void resolveReadScope_asAdmin_withNoScope_returnsAll() {
        when(context.isSuperAdmin()).thenReturn(true);
        when(context.hasRole("ADMIN")).thenReturn(false);
        when(context.getCurrentOrg()).thenReturn(null);

        ExpenseCategoryPolicy.CategoryScope scope = policy.resolveReadScope(null, null, context);

        assertThat(scope.scope()).isEqualTo("ALL");
        assertThat(scope.all()).isTrue();
    }

    @Test
    void resolveReadScope_asStaff_withNoOrg_throws() {
        when(context.isSuperAdmin()).thenReturn(false);
        when(context.hasRole("ADMIN")).thenReturn(false);
        when(context.getCurrentOrg()).thenReturn(null);

        assertThatThrownBy(() -> policy.resolveReadScope(null, null, context))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("A branch must be selected");
    }

    @Test
    void resolveReadScope_asStaff_withCurrentOrg_returnsBranchScope() {
        UUID branchId = UUID.randomUUID();
        when(context.isSuperAdmin()).thenReturn(false);
        when(context.hasRole("ADMIN")).thenReturn(false);
        when(context.getCurrentOrg()).thenReturn(branchId);

        ExpenseCategoryPolicy.CategoryScope scope = policy.resolveReadScope(null, null, context);

        assertThat(scope.scope()).isEqualTo("BRANCH");
        assertThat(scope.orgId()).isEqualTo(branchId);
        assertThat(scope.all()).isFalse();
    }

    @Test
    void resolveReadScope_requestGlobal_asStaff_throws() {
        when(context.isSuperAdmin()).thenReturn(false);
        when(context.hasRole("ADMIN")).thenReturn(false);
        when(context.getCurrentOrg()).thenReturn(UUID.randomUUID());

        assertThatThrownBy(() -> policy.resolveReadScope("GLOBAL", null, context))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only organization admins");
    }

    @Test
    void resolveReadScope_requestGlobal_asAdmin_returnsGlobalScope() {
        when(context.isSuperAdmin()).thenReturn(false);
        when(context.hasRole("ADMIN")).thenReturn(true);
        when(context.getCurrentOrg()).thenReturn(UUID.randomUUID());

        ExpenseCategoryPolicy.CategoryScope scope = policy.resolveReadScope("GLOBAL", null, context);

        assertThat(scope.scope()).isEqualTo("GLOBAL");
        assertThat(scope.orgId()).isNull();
        assertThat(scope.all()).isFalse();
    }

    @Test
    void resolveWriteScope_requestAll_throws() {
        when(context.isSuperAdmin()).thenReturn(false);
        when(context.hasRole("ADMIN")).thenReturn(true);
        when(context.getCurrentOrg()).thenReturn(UUID.randomUUID());

        assertThatThrownBy(() -> policy.resolveWriteScope("ALL", null, context))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Select Organization or a branch");
    }

    @Test
    void resolveWriteScope_requestBranch_asStaffForDifferentBranch_throws() {
        UUID myBranch = UUID.randomUUID();
        UUID otherBranch = UUID.randomUUID();
        when(context.isSuperAdmin()).thenReturn(false);
        when(context.hasRole("ADMIN")).thenReturn(false);
        when(context.getCurrentOrg()).thenReturn(myBranch);

        assertThatThrownBy(() -> policy.resolveWriteScope("BRANCH", otherBranch, context))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Unauthorized access");
    }

    @Test
    void normalizeScope_invalidScopeName_throwsIllegalArgumentException() {
        when(context.isSuperAdmin()).thenReturn(false);
        when(context.hasRole("ADMIN")).thenReturn(false);
        when(context.getCurrentOrg()).thenReturn(UUID.randomUUID());

        assertThatThrownBy(() -> policy.resolveReadScope("INVALID_SCOPE_NAME", null, context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid scope");
    }
}
