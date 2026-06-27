package com.restaurant.pos.purchasing.service;

import com.restaurant.pos.common.exception.BusinessException;
import com.restaurant.pos.common.tenant.TenantContext;
import com.restaurant.pos.purchasing.domain.Customer;
import com.restaurant.pos.purchasing.repository.CurrencyRepository;
import com.restaurant.pos.purchasing.repository.CustomerRepository;
import com.restaurant.pos.purchasing.repository.PricelistRepository;
import com.restaurant.pos.purchasing.repository.VendorRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PurchasingServiceTest {

    private CustomerRepository customerRepository;
    private PurchasingService purchasingService;
    private UUID clientId;

    @BeforeEach
    void setUp() {
        customerRepository = mock(CustomerRepository.class);
        purchasingService = new PurchasingService(
                customerRepository,
                mock(VendorRepository.class),
                mock(CurrencyRepository.class),
                mock(PricelistRepository.class),
                mock(com.restaurant.pos.purchasing.repository.PaymentTypeRepository.class),
                new com.restaurant.pos.common.service.BranchContextService()
        );
        clientId = UUID.randomUUID();
        TenantContext.setCurrentTenant(clientId);
        TenantContext.setCurrentOrg(UUID.randomUUID());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "owner@example.com",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))
        ));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void saveCustomerRejectsDuplicatePhoneInClientProfile() {
        Customer customer = Customer.builder()
                .name("Rahul")
                .phone("123 456")
                .build();

        when(customerRepository.existsByClientIdAndPhone(clientId, "123456")).thenReturn(true);

        assertThatThrownBy(() -> purchasingService.saveCustomer(customer))
                .isInstanceOf(BusinessException.class)
                .hasMessage("A customer with this phone number already exists");
    }
}
