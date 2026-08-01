package com.restaurant.pos.inventory.command;

import com.restaurant.pos.accounting.service.AccountingPostingService;
import com.restaurant.pos.common.service.BranchContextService;
import com.restaurant.pos.common.tenant.TenantContext;
import com.restaurant.pos.common.util.SecurityUtils;
import com.restaurant.pos.inventory.domain.StockAdjustment;
import com.restaurant.pos.inventory.domain.StockAdjustmentLine;
import com.restaurant.pos.inventory.domain.StockLedger;
import com.restaurant.pos.inventory.domain.StockSnapshot;
import com.restaurant.pos.inventory.domain.StockTransfer;
import com.restaurant.pos.inventory.domain.StockTransferLine;
import com.restaurant.pos.inventory.repository.StockAdjustmentRepository;
import com.restaurant.pos.inventory.repository.StockLedgerRepository;
import com.restaurant.pos.inventory.repository.StockSnapshotRepository;
import com.restaurant.pos.inventory.repository.StockTransferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class InventoryCommandService {

    private final StockLedgerRepository stockLedgerRepository;
    private final StockSnapshotRepository stockSnapshotRepository;
    private final StockAdjustmentRepository stockAdjustmentRepository;
    private final StockTransferRepository stockTransferRepository;
    private final AccountingPostingService accountingPostingService;
    private final BranchContextService branchContext;

    public void updateStock(UUID warehouseId, UUID productId, UUID variantId, BigDecimal quantityChange,
                            String transactionType, UUID referenceId, BigDecimal unitCost) {
        updateStock(warehouseId, productId, variantId, quantityChange, transactionType, referenceId, unitCost, null);
    }

    public void updateStock(UUID warehouseId, UUID productId, UUID variantId, BigDecimal quantityChange,
                            String transactionType, UUID referenceId, BigDecimal unitCost, UUID explicitOrgId) {
        UUID clientId = TenantContext.getCurrentTenant();
        UUID orgId = explicitOrgId != null ? explicitOrgId
                : branchContext.requireWriteOrgId(TenantContext.getCurrentOrg());

        StockSnapshot snapshot = stockSnapshotRepository
                .findByWarehouseIdAndProductIdAndVariantId(warehouseId, productId, variantId)
                .orElseGet(() -> StockSnapshot.builder()
                        .clientId(clientId)
                        .orgId(orgId)
                        .warehouseId(warehouseId)
                        .productId(productId)
                        .variantId(variantId)
                        .currentQuantity(BigDecimal.ZERO)
                        .build());

        BigDecimal newBalance = snapshot.getCurrentQuantity().add(quantityChange);
        snapshot.setCurrentQuantity(newBalance);
        snapshot.setLastUpdated(LocalDateTime.now());
        stockSnapshotRepository.save(snapshot);

        StockLedger ledger = StockLedger.builder()
                .clientId(clientId)
                .orgId(orgId)
                .warehouseId(warehouseId)
                .productId(productId)
                .variantId(variantId)
                .transactionType(transactionType)
                .referenceId(referenceId)
                .quantityChange(quantityChange)
                .balanceAfterTransaction(newBalance)
                .unitCost(unitCost)
                .createdBy(SecurityUtils.getCurrentUserId())
                .build();
        stockLedgerRepository.save(ledger);
    }

    public StockAdjustment saveAdjustment(StockAdjustment adjustment) {
        UUID clientId = TenantContext.getCurrentTenant();
        UUID orgId = branchContext.requireWriteOrgId(adjustment.getOrgId());
        
        adjustment.setClientId(clientId);
        adjustment.setOrgId(orgId);

        if (adjustment.getAdjustmentNumber() == null) {
            adjustment.setAdjustmentNumber("ADJ-" + System.currentTimeMillis());
        }

        if ("COMPLETED".equalsIgnoreCase(adjustment.getStatus())) {
            for (StockAdjustmentLine line : adjustment.getLines()) {
                updateStock(adjustment.getWarehouseId(), line.getProductId(), line.getVariantId(), 
                        line.getQuantityChange(), "ADJUSTMENT", adjustment.getId(), line.getUnitCost());
            }
        }
        
        StockAdjustment saved = stockAdjustmentRepository.save(adjustment);
        accountingPostingService.postStockAdjustment(saved);
        return saved;
    }

    public StockTransfer saveTransfer(StockTransfer transfer) {
        UUID clientId = TenantContext.getCurrentTenant();
        UUID orgId = branchContext.requireWriteOrgId(transfer.getOrgId());
        
        transfer.setClientId(clientId);
        transfer.setOrgId(orgId);

        if (transfer.getTransferNumber() == null) {
            transfer.setTransferNumber("TRF-" + System.currentTimeMillis());
        }

        if ("COMPLETED".equalsIgnoreCase(transfer.getStatus())) {
            for (StockTransferLine line : transfer.getLines()) {
                updateStock(transfer.getSourceWarehouseId(), line.getProductId(), line.getVariantId(), 
                        line.getTransferQuantity().negate(), "TRANSFER_OUT", transfer.getId(), BigDecimal.ZERO);
                
                updateStock(transfer.getDestWarehouseId(), line.getProductId(), line.getVariantId(), 
                        line.getTransferQuantity(), "TRANSFER_IN", transfer.getId(), BigDecimal.ZERO);
            }
        }

        return stockTransferRepository.save(transfer);
    }
}
