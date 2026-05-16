package com.restaurant.pos.order.service;

import com.restaurant.pos.accounting.service.AccountingPostingService;
import com.restaurant.pos.common.tenant.TenantContext;
import com.restaurant.pos.invoice.repository.InvoiceRepository;
import com.restaurant.pos.order.domain.Order;
import com.restaurant.pos.order.domain.OrderType;
import com.restaurant.pos.order.dto.report.OrderReportDto;
import com.restaurant.pos.order.repository.OrderRepository;
import com.restaurant.pos.order.repository.PaymentRepository;
import com.restaurant.pos.order.repository.PaymentSplitRepository;
import com.restaurant.pos.purchasing.domain.Customer;
import com.restaurant.pos.purchasing.repository.CustomerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportServiceTest {

    private OrderRepository orderRepository;
    private CustomerRepository customerRepository;
    private ReportService reportService;
    private UUID clientId;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        customerRepository = mock(CustomerRepository.class);
        reportService = new ReportService(
                orderRepository,
                mock(InvoiceRepository.class),
                mock(PaymentRepository.class),
                mock(PaymentSplitRepository.class),
                customerRepository,
                mock(AccountingPostingService.class)
        );
        clientId = UUID.randomUUID();
        TenantContext.setCurrentTenant(clientId);
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
    void salesOrdersDisplayAllCustomersLinkedFromCustomerTable() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .id(orderId)
                .orderNo("SO-1")
                .orderType(OrderType.SALE)
                .orderStatus("COMPLETED")
                .paymentStatus("PAID")
                .orderDate(Instant.parse("2026-05-14T00:00:00Z"))
                .grandTotal(new BigDecimal("118.00"))
                .build();
        order.setClientId(clientId);
        order.setCreatedAt(LocalDateTime.parse("2026-05-14T00:00:00"));

        Customer primary = Customer.builder().id(UUID.randomUUID()).name("Rahul").phone("123456").build();
        primary.setClientId(clientId);
        primary.setOrderLinks(List.of(Customer.OrderLink.builder().orderId(orderId).isPrimary(true).build()));
        Customer secondary = Customer.builder().id(UUID.randomUUID()).name("Riyas").phone("7012120844").build();
        secondary.setClientId(clientId);
        secondary.setOrderLinks(List.of(Customer.OrderLink.builder().orderId(orderId).isPrimary(false).build()));

        when(orderRepository.findAll(any(Specification.class))).thenReturn(List.of(order));
        when(customerRepository.findByClientIdAndOrderLink(eq(clientId), any(), any()))
                .thenReturn(List.of(primary, secondary));

        List<OrderReportDto> rows = reportService.getSalesOrders(null, null);

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.getCustomerName()).isEqualTo("Rahul (123456), Riyas (7012120844)");
            assertThat(row.getCustomers()).extracting("id").containsExactly(primary.getId(), secondary.getId());
        });
    }
}
