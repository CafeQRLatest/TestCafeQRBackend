package com.restaurant.pos.purchase.listener;

import com.restaurant.pos.invoice.domain.Invoice;
import com.restaurant.pos.invoice.domain.InvoiceLine;
import com.restaurant.pos.invoice.domain.InvoiceType;
import com.restaurant.pos.invoice.repository.InvoiceRepository;
import com.restaurant.pos.order.domain.Order;
import com.restaurant.pos.order.domain.OrderLine;
import com.restaurant.pos.purchase.event.PurchaseOrderCompletedEvent;
import com.restaurant.pos.sequence.domain.DocumentType;
import com.restaurant.pos.sequence.service.DocumentSequenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Event Listener for Purchase Order completion.
 * Generates the corresponding Vendor Bill (c_invoice / c_invoiceline) event-driven.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseOrderVendorBillListener {

    private final InvoiceRepository invoiceRepository;
    private final DocumentSequenceService documentSequenceService;

    @EventListener
    @Transactional
    public void onPurchaseOrderCompleted(PurchaseOrderCompletedEvent event) {
        Order purchaseOrder = event.purchaseOrder();

        try {
            if (invoiceRepository.findByOrderIdAndClientId(purchaseOrder.getId(), purchaseOrder.getClientId()).isPresent()) {
                return;
            }

            String billNo = documentSequenceService.generateNextSequence(DocumentType.VENDOR_BILL, purchaseOrder.getOrgId());
            boolean isPaid = "PAID".equalsIgnoreCase(purchaseOrder.getPaymentStatus());
            BigDecimal grandTotal = purchaseOrder.getGrandTotal() != null ? purchaseOrder.getGrandTotal() : BigDecimal.ZERO;

            Invoice vendorBill = Invoice.builder()
                    .invoiceType(InvoiceType.VENDOR_BILL)
                    .orderId(purchaseOrder.getId())
                    .vendorId(purchaseOrder.getVendorId())
                    .invoiceNo(billNo)
                    .invoiceDate(LocalDateTime.now())
                    .totalAmount(grandTotal)
                    .amountDue(isPaid ? BigDecimal.ZERO : grandTotal)
                    .status(isPaid ? "PAID" : "COMPLETED")
                    .isPaid(isPaid)
                    .grossAmount(purchaseOrder.getGrandTotal())
                    .totalTaxAmount(purchaseOrder.getTotalTaxAmount())
                    .totalDiscountAmount(purchaseOrder.getTotalDiscountAmount())
                    .build();

            vendorBill.setClientId(purchaseOrder.getClientId());
            vendorBill.setOrgId(purchaseOrder.getOrgId());

            if (purchaseOrder.getLines() != null) {
                List<InvoiceLine> invoiceLines = new ArrayList<>();
                for (OrderLine ol : purchaseOrder.getLines()) {
                    InvoiceLine il = InvoiceLine.builder()
                            .productId(ol.getProductId())
                            .variantId(ol.getVariantId())
                            .productName(ol.getProductName())
                            .quantity(ol.getQuantity())
                            .unitPrice(ol.getUnitPrice())
                            .taxRate(ol.getTaxRate())
                            .taxAmount(ol.getTaxAmount())
                            .discountAmount(ol.getDiscountAmount())
                            .lineTotal(ol.getLineTotal())
                            .unitOfMeasure(ol.getUnitOfMeasure())
                            .build();
                    il.setInvoice(vendorBill);
                    invoiceLines.add(il);
                }
                vendorBill.setLines(invoiceLines);
            }

            invoiceRepository.save(vendorBill);
            log.info("EventListener: Generated Vendor Bill | billNo={} | orderId={}", billNo, purchaseOrder.getId());
        } catch (Exception e) {
            log.error("EventListener: Failed to generate Vendor Bill for PO {}: {}", purchaseOrder.getOrderNo(), e.getMessage(), e);
        }
    }
}
