package com.restaurant.pos.purchase.listener;

import com.restaurant.pos.inventory.service.InventoryService;
import com.restaurant.pos.order.domain.Order;
import com.restaurant.pos.order.domain.OrderLine;
import com.restaurant.pos.purchase.event.PurchaseOrderCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Event Listener for Purchase Order completion.
 * Receives stock into the destination warehouse asynchronously / event-driven.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseOrderInventoryListener {

    private final InventoryService inventoryService;

    @EventListener
    @Transactional
    public void onPurchaseOrderCompleted(PurchaseOrderCompletedEvent event) {
        Order purchaseOrder = event.purchaseOrder();
        if (purchaseOrder.getWarehouseId() == null || purchaseOrder.getLines() == null) {
            return;
        }

        log.info("EventListener: Processing stock intake | orderNo={} | warehouseId={} | lines={}",
                purchaseOrder.getOrderNo(), purchaseOrder.getWarehouseId(), purchaseOrder.getLines().size());

        for (OrderLine line : purchaseOrder.getLines()) {
            if (line.getProductId() != null && line.getQuantity() != null && line.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
                try {
                    inventoryService.updateStock(
                            purchaseOrder.getWarehouseId(),
                            line.getProductId(),
                            line.getVariantId(),
                            line.getQuantity(),           // Positive = Stock IN
                            "PURCHASE_RECEIPT",           // Transaction type
                            purchaseOrder.getId(),
                            line.getUnitPrice(),
                            purchaseOrder.getOrgId()
                    );
                } catch (Exception e) {
                    log.error("Failed stock intake for item {} in PO {}: {}",
                            line.getProductId(), purchaseOrder.getOrderNo(), e.getMessage(), e);
                }
            }
        }
    }
}
