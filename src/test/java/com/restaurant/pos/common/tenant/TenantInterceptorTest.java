package com.restaurant.pos.common.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TenantInterceptorTest {

    private final TenantInterceptor interceptor = new TenantInterceptor();

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void superAdminGlobalOrgHeaderClearsJwtBranchContext() {
        UUID jwtOrgId = UUID.randomUUID();
        TenantContext.setCurrentOrg(jwtOrgId);
        authenticateAs("ROLE_SUPER_ADMIN");

        MockHttpServletRequest request = requestWithOrgHeader("0");

        boolean proceed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(proceed).isTrue();
        assertThat(TenantContext.getCurrentOrg()).isNull();
        assertThat(UserContext.getContext().getOrgId()).isNull();
    }

    @Test
    void realOrgHeaderOverridesJwtBranchContext() {
        UUID jwtOrgId = UUID.randomUUID();
        UUID selectedOrgId = UUID.randomUUID();
        TenantContext.setCurrentOrg(jwtOrgId);
        authenticateAs("ROLE_SUPER_ADMIN");

        MockHttpServletRequest request = requestWithOrgHeader(selectedOrgId.toString());

        boolean proceed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(proceed).isTrue();
        assertThat(TenantContext.getCurrentOrg()).isEqualTo(selectedOrgId);
        assertThat(UserContext.getContext().getOrgId()).isEqualTo(selectedOrgId);
    }

    @Test
    void nonSuperAdminGlobalOrgHeaderKeepsJwtBranchContext() {
        UUID jwtOrgId = UUID.randomUUID();
        TenantContext.setCurrentOrg(jwtOrgId);
        authenticateAs("ROLE_ADMIN");

        MockHttpServletRequest request = requestWithOrgHeader("0");

        boolean proceed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(proceed).isTrue();
        assertThat(TenantContext.getCurrentOrg()).isEqualTo(jwtOrgId);
        assertThat(UserContext.getContext().getOrgId()).isEqualTo(jwtOrgId);
    }

    private MockHttpServletRequest requestWithOrgHeader(String orgId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Org-ID", orgId);
        return request;
    }

    private void authenticateAs(String authority) {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "test-user",
                "n/a",
                List.of(new SimpleGrantedAuthority(authority))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
