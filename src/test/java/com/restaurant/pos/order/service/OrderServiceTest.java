package com.restaurant.pos.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.pos.common.tenant.TenantContext;
import com.restaurant.pos.inventory.service.InventoryService;
import com.restaurant.pos.invoice.repository.InvoiceRepository;
import com.restaurant.pos.order.domain.Order;
import com.restaurant.pos.order.repository.OrderRepository;
import com.restaurant.pos.order.repository.PaymentRepository;
import com.restaurant.pos.print.service.PrintJobService;
import com.restaurant.pos.product.repository.ProductRepository;
import com.restaurant.pos.purchasing.domain.Customer;
import com.restaurant.pos.purchasing.repository.CustomerRepository;
import com.restaurant.pos.sequence.domain.DocumentType;
import com.restaurant.pos.sequence.service.DocumentSequenceService;
import com.restaurant.pos.sequence.service.OfflineSequenceLeaseService;
import com.restaurant.pos.table.repository.RestaurantTableRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderServiceTest {

    private OrderRepository orderRepository;
    private InvoiceRepository invoiceRepository;
    private PaymentRepository paymentRepository;
    private DocumentSequenceService sequenceService;
    private CustomerRepository customerRepository;
    private OrderService orderService;

    private UUID clientId;
    private UUID orgId;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        invoiceRepository = mock(InvoiceRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        sequenceService = mock(DocumentSequenceService.class);
        customerRepository = mock(CustomerRepository.class);

        orderService = new OrderService(
                orderRepository,
                invoiceRepository,
                paymentRepository,
                mock(InventoryService.class),
                mock(RestaurantTableRepository.class),
                sequenceService,
                mock(OfflineSequenceLeaseService.class),
                mock(PrintJobService.class),
                mock(ProductRepository.class),
                customerRepository,
                new ObjectMapper()
        );

        clientId = UUID.randomUUID();
        orgId = UUID.randomUUID();
        TenantContext.setCurrentTenant(clientId);
        TenantContext.setCurrentOrg(orgId);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "staff@example.com",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        ));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void createOrderStoresMultipleCustomerLinksOnCustomersTable() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID customerAId = UUID.randomUUID();
        UUID customerBId = UUID.randomUUID();

        Customer customerA = Customer.builder().id(customerAId).name("Rahul").phone("123456").build();
        customerA.setClientId(clientId);
        customerA.setOrgId(orgId);
        Customer customerB = Customer.builder().id(customerBId).name("Riyas").phone("7012120844").build();
        customerB.setClientId(clientId);
        customerB.setOrgId(orgId);

        Order order = Order.builder()
                .id(orderId)
                .orderStatus("KITCHEN")
                .paymentStatus("PENDING")
                .customerIds(new ObjectMapper().readTree("""
                        [
                          {"id":"%s","name":"Rahul","phone":"123456"},
                          {"id":"%s","name":"Riyas","phone":"7012120844"}
                        ]
                        """.formatted(customerAId, customerBId)))
                .build();

        when(sequenceService.generateNextSequence(DocumentType.SALE_ORDER)).thenReturn("SO-1");
        when(customerRepository.findByIdAndClientId(customerAId, clientId)).thenReturn(Optional.of(customerA));
        when(customerRepository.findByIdAndClientId(customerBId, clientId)).thenReturn(Optional.of(customerB));
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(customerRepository.findByClientIdAndOrderLink(eq(clientId), any(), any()))
                .thenReturn(List.of(customerA, customerB));

        Order saved = orderService.createOrder(order);

        assertThat(saved.getCustomerId()).isEqualTo(customerAId);
        assertThat(saved.getCustomers()).extracting("id").containsExactly(customerAId, customerBId);
        assertThat(customerA.getOrderLinks()).singleElement().satisfies(link -> {
            assertThat(link.getOrderId()).isEqualTo(orderId);
            assertThat(link.getIsPrimary()).isTrue();
        });
        assertThat(customerB.getOrderLinks()).singleElement().satisfies(link -> {
            assertThat(link.getOrderId()).isEqualTo(orderId);
            assertThat(link.getIsPrimary()).isFalse();
        });
    }
}
