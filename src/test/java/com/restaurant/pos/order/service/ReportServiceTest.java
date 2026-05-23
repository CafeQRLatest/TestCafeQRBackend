package com.restaurant.pos.order.service;

import com.restaurant.pos.accounting.service.AccountingPostingService;
import com.restaurant.pos.accounting.service.AccountingService;
import com.restaurant.pos.accounting.dto.AccountingSummaryDto;
import com.restaurant.pos.client.domain.Organization;
import com.restaurant.pos.client.repository.OrganizationRepository;
import com.restaurant.pos.common.tenant.TenantContext;
import com.restaurant.pos.invoice.domain.Invoice;
import com.restaurant.pos.invoice.domain.InvoiceType;
import com.restaurant.pos.invoice.repository.InvoiceRepository;
import com.restaurant.pos.order.domain.Order;
import com.restaurant.pos.order.domain.OrderType;
import com.restaurant.pos.order.domain.Payment;
import com.restaurant.pos.order.domain.PaymentType;
import com.restaurant.pos.order.dto.report.OrderReportDto;
import com.restaurant.pos.order.dto.report.ProfitLossDto;
import com.restaurant.pos.order.dto.report.SalesInvoiceReportDto;
import com.restaurant.pos.order.repository.OrderRepository;
import com.restaurant.pos.order.repository.PaymentRepository;
import com.restaurant.pos.order.repository.PaymentSplitRepository;
import com.restaurant.pos.purchasing.domain.Customer;
import com.restaurant.pos.purchasing.repository.CustomerRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class ReportServiceTest {

    private OrderRepository orderRepository;
    private InvoiceRepository invoiceRepository;
    private PaymentRepository paymentRepository;
    private CustomerRepository customerRepository;
    private AccountingPostingService accountingPostingService;
    private AccountingService accountingService;
    private OrganizationRepository organizationRepository;
    private ReportService reportService;
    private UUID clientId;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        invoiceRepository = mock(InvoiceRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        customerRepository = mock(CustomerRepository.class);
        accountingPostingService = mock(AccountingPostingService.class);
        accountingService = mock(AccountingService.class);
        organizationRepository = mock(OrganizationRepository.class);
        reportService = new ReportService(
                orderRepository,
                invoiceRepository,
                paymentRepository,
                mock(PaymentSplitRepository.class),
                customerRepository,
                accountingPostingService,
                accountingService,
                organizationRepository
        );
        clientId = UUID.randomUUID();
        TenantContext.setCurrentTenant(clientId);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "owner@example.com",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))
        ));
    }

    @Test
    void superAdminSelectedBranchScopesOrderReportsToTenantOrg() {
        UUID selectedBranchId = UUID.randomUUID();
        TenantContext.setCurrentOrg(selectedBranchId);
        when(orderRepository.findAll(any(Specification.class))).thenReturn(List.of());

        reportService.getSalesSummary(null, null);

        Specification<Order> specification = captureOrderSpecification();
        CriteriaMocks<Order> criteria = criteriaMocks();
        specification.toPredicate(criteria.root(), criteria.query(), criteria.cb());
        verify(criteria.root()).get("orgId");
        verify(criteria.cb()).equal(any(Expression.class), eq(selectedBranchId));
    }

    @Test
    void superAdminAllBranchesDoesNotAddOrderOrgPredicate() {
        TenantContext.setCurrentOrg(null);
        when(orderRepository.findAll(any(Specification.class))).thenReturn(List.of());

        reportService.getSalesSummary(null, null);

        Specification<Order> specification = captureOrderSpecification();
        CriteriaMocks<Order> criteria = criteriaMocks();
        specification.toPredicate(criteria.root(), criteria.query(), criteria.cb());
        verify(criteria.root(), never()).get("orgId");
    }

    @Test
    void salesInvoicesIncludeBranchDetailsFromOrderBranch() {
        UUID branchId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        TenantContext.setCurrentOrg(branchId);

        Order order = Order.builder()
                .id(orderId)
                .orderNo("SO-1")
                .orderType(OrderType.SALE)
                .orderStatus("COMPLETED")
                .paymentStatus("PAID")
                .orderDate(Instant.parse("2026-05-23T00:00:00Z"))
                .grandTotal(new BigDecimal("68.00"))
                .build();
        order.setClientId(clientId);
        order.setOrgId(branchId);
        order.setCreatedAt(LocalDateTime.parse("2026-05-23T00:00:00"));

        Invoice invoice = Invoice.builder()
                .id(invoiceId)
                .orderId(orderId)
                .invoiceNo("INV-1")
                .invoiceType(InvoiceType.CUSTOMER_INVOICE)
                .status("PAID")
                .docStatus("COMPLETED")
                .totalAmount(new BigDecimal("68.00"))
                .amountDue(BigDecimal.ZERO)
                .build();
        invoice.setClientId(clientId);
        invoice.setOrgId(branchId);

        Organization branch = Organization.builder()
                .id(branchId)
                .clientId(clientId)
                .name("Thrissur")
                .branchCode("TSR")
                .build();

        when(orderRepository.findAll(any(Specification.class))).thenReturn(List.of(order));
        when(invoiceRepository.findByOrderId(orderId)).thenReturn(List.of(invoice));
        when(invoiceRepository.findAll(any(Specification.class))).thenReturn(List.of());
        when(paymentRepository.findByOrderId(orderId)).thenReturn(List.of());
        when(customerRepository.findByClientIdAndOrderLink(eq(clientId), anyString(), anyString()))
                .thenReturn(List.of());
        when(organizationRepository.findAllByClientIdAndIdIn(eq(clientId), anyCollection()))
                .thenReturn(List.of(branch));

        List<SalesInvoiceReportDto> rows = reportService.getSalesInvoices(null, null, "ALL");

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.getBranchId()).isEqualTo(branchId);
            assertThat(row.getBranchName()).isEqualTo("Thrissur");
            assertThat(row.getBranchCode()).isEqualTo("TSR");
        });
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

    @Test
    void profitLossUsesAccountingSummaryExpenses() {
        when(accountingService.getSummary(any(), any())).thenReturn(AccountingSummaryDto.builder()
                .grossSales(new BigDecimal("3750.50"))
                .discounts(new BigDecimal("525.00"))
                .netSales(new BigDecimal("3225.50"))
                .outputTax(new BigDecimal("321.80"))
                .inputTax(BigDecimal.ZERO)
                .expenses(new BigDecimal("2000.00"))
                .cogsPurchases(BigDecimal.ZERO)
                .profit(new BigDecimal("1225.50"))
                .receivable(BigDecimal.ZERO)
                .paymentCollected(new BigDecimal("3747.30"))
                .build());

        ProfitLossDto pnl = reportService.getProfitLoss(
                Instant.parse("2026-05-01T00:00:00Z"),
                Instant.parse("2026-05-22T18:29:00Z")
        );

        assertThat(pnl.getOperatingExpenses()).isEqualByComparingTo("2000.00");
        assertThat(pnl.getTotalExpenses()).isEqualByComparingTo("2000.00");
        assertThat(pnl.getNetProfit()).isEqualByComparingTo("1225.50");
        assertThat(pnl.getCashCollectedAfterExpenses()).isEqualByComparingTo("1747.30");
        assertThat(pnl.getBasis()).isEqualTo("ACCOUNTING_JOURNALS");
    }

    @SuppressWarnings("unchecked")
    private Specification<Order> captureOrderSpecification() {
        ArgumentCaptor<Specification<Order>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(orderRepository).findAll(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> CriteriaMocks<T> criteriaMocks() {
        Root<T> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path path = mock(Path.class);
        Predicate predicate = mock(Predicate.class);
        jakarta.persistence.criteria.Order order = mock(jakarta.persistence.criteria.Order.class);
        when(root.get(anyString())).thenReturn(path);
        when(cb.equal(any(Expression.class), any(Object.class))).thenReturn(predicate);
        when(cb.desc(any(Expression.class))).thenReturn(order);
        when(query.orderBy(order)).thenReturn((CriteriaQuery) query);
        when(cb.and(any(Predicate[].class))).thenReturn(predicate);
        return new CriteriaMocks<>(root, query, cb);
    }

    private record CriteriaMocks<T>(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
    }

    @Test
    void voidInvoiceVoidsPaidSalePaymentAndReversesFullAccountingChain() {
        UUID orgId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        Order order = Order.builder()
                .id(orderId)
                .orderNo("SO-1")
                .orderType(OrderType.SALE)
                .orderStatus("COMPLETED")
                .paymentStatus("PAID")
                .build();
        order.setClientId(clientId);
        order.setOrgId(orgId);

        Invoice invoice = Invoice.builder()
                .id(invoiceId)
                .orderId(orderId)
                .invoiceNo("INV-1")
                .status("PAID")
                .docStatus("COMPLETED")
                .isPaid(true)
                .totalAmount(new BigDecimal("118.00"))
                .amountDue(BigDecimal.ZERO)
                .build();
        invoice.setClientId(clientId);
        invoice.setOrgId(orgId);

        Payment payment = Payment.builder()
                .id(paymentId)
                .orderId(orderId)
                .paymentType(PaymentType.INBOUND)
                .paymentMethod("CASH")
                .amountPaid(new BigDecimal("118.00"))
                .docStatus("COMPLETED")
                .isactive("Y")
                .build();
        payment.setClientId(clientId);
        payment.setOrgId(orgId);

        when(invoiceRepository.findByIdAndClientId(invoiceId, clientId)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.findByOrderId(orderId)).thenReturn(List.of(invoice));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRepository.findByOrderId(orderId)).thenReturn(List.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Invoice voided = reportService.voidInvoice(invoiceId, "Wrong sale");

        assertThat(voided.getStatus()).isEqualTo("VOID");
        assertThat(voided.getDocStatus()).isEqualTo("VOIDED");
        assertThat(voided.getIsPaid()).isFalse();
        assertThat(voided.getAmountDue()).isEqualByComparingTo("0.00");
        assertThat(order.getOrderStatus()).isEqualTo("CANCELLED");
        assertThat(order.getPaymentStatus()).isEqualTo("VOID");
        assertThat(payment.getDocStatus()).isEqualTo("VOIDED");
        assertThat(payment.getIsactive()).isEqualTo("N");
        verify(accountingPostingService).reverseInvoice(invoice, "Invoice voided");
        verify(accountingPostingService).reversePayment(payment, "Invoice voided");
        verify(accountingPostingService).reverseSaleCogs(order, "Invoice voided");
    }
}
