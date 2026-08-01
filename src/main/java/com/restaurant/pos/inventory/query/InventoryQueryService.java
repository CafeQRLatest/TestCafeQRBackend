package com.restaurant.pos.inventory.query;

import com.restaurant.pos.common.service.BranchContextService;
import com.restaurant.pos.common.tenant.TenantContext;
import com.restaurant.pos.inventory.domain.StockAdjustment;
import com.restaurant.pos.inventory.domain.StockSnapshot;
import com.restaurant.pos.inventory.domain.StockTransfer;
import com.restaurant.pos.inventory.repository.StockAdjustmentRepository;
import com.restaurant.pos.inventory.repository.StockSnapshotRepository;
import com.restaurant.pos.inventory.repository.StockTransferRepository;
import com.restaurant.pos.product.repository.ProductRepository;
import com.restaurant.pos.warehouse.domain.Warehouse;
import com.restaurant.pos.warehouse.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.restaurant.pos.auth.repository.UserRepository;
import com.restaurant.pos.inventory.domain.StockAdjustmentLine;
import com.restaurant.pos.inventory.domain.StockTransferLine;
import com.restaurant.pos.product.domain.Product;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryQueryService {

    private final StockSnapshotRepository stockSnapshotRepository;
    private final StockAdjustmentRepository stockAdjustmentRepository;
    private final StockTransferRepository stockTransferRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final BranchContextService branchContext;

    private String resolveUserName(String userIdStr) {
        if (userIdStr == null || userIdStr.isBlank() || "SYSTEM".equalsIgnoreCase(userIdStr)) {
            return "System";
        }
        try {
            UUID uid = UUID.fromString(userIdStr);
            return userRepository.findById(uid)
                    .map(u -> {
                        String fn = u.getFirstName() != null ? u.getFirstName() : "";
                        String ln = u.getLastName() != null ? u.getLastName() : "";
                        String full = (fn + " " + ln).trim();
                        return full.isEmpty() ? (u.getEmail() != null ? u.getEmail() : userIdStr) : full;
                    })
                    .orElse(userIdStr);
        } catch (Exception e) {
            return userIdStr;
        }
    }

    public List<StockSnapshot> getStockOverview(UUID warehouseId) {
        List<StockSnapshot> snapshots = stockSnapshotRepository.findByWarehouseId(warehouseId);
        Warehouse warehouse = warehouseRepository.findById(warehouseId).orElse(null);
        if (warehouse == null) {
            return snapshots;
        }
        return appendRecipeProductsStock(snapshots, warehouse.getClientId(), warehouse.getOrgId(), warehouseId, false);
    }

    public List<StockSnapshot> getConsolidatedStockOverview(UUID orgId, UUID warehouseId) {
        UUID clientId = TenantContext.getCurrentTenant();
        UUID effectiveOrgId = orgId != null ? orgId : TenantContext.getCurrentOrg();
        
        if (warehouseId != null) {
            List<StockSnapshot> raw = stockSnapshotRepository.findByClientIdAndWarehouseId(clientId, warehouseId);
            return appendRecipeProductsStock(raw, clientId, effectiveOrgId, warehouseId, false);
        }
        
        List<StockSnapshot> rawSnapshots;
        if (orgId != null) {
            rawSnapshots = stockSnapshotRepository.findByClientIdAndOrgId(clientId, orgId);
        } else {
            rawSnapshots = stockSnapshotRepository.findByClientId(clientId);
        }
        
        Map<String, StockSnapshot> consolidatedMap = new HashMap<>();
        for (StockSnapshot snap : rawSnapshots) {
            String key = snap.getProductId().toString() + "_" + (snap.getVariantId() != null ? snap.getVariantId().toString() : "null");
            if (consolidatedMap.containsKey(key)) {
                StockSnapshot existing = consolidatedMap.get(key);
                existing.setCurrentQuantity(existing.getCurrentQuantity().add(snap.getCurrentQuantity()));
            } else {
                StockSnapshot copy = StockSnapshot.builder()
                        .id(null)
                        .clientId(snap.getClientId())
                        .orgId(snap.getOrgId())
                        .warehouseId(null)
                        .productId(snap.getProductId())
                        .variantId(snap.getVariantId())
                        .currentQuantity(snap.getCurrentQuantity())
                        .lastUpdated(snap.getLastUpdated())
                        .build();
                consolidatedMap.put(key, copy);
            }
        }
        
        List<StockSnapshot> consolidatedSnapshots = new ArrayList<>(consolidatedMap.values());
        return appendRecipeProductsStock(consolidatedSnapshots, clientId, effectiveOrgId, null, true);
    }

    public Page<StockAdjustment> getAdjustments(UUID orgId, Pageable pageable) {
        UUID clientId = TenantContext.getCurrentTenant();
        UUID effectiveOrgId = branchContext.getReadOrgId(orgId);
        Page<StockAdjustment> page;
        if (effectiveOrgId != null) {
            page = stockAdjustmentRepository.findByClientIdAndOrgIdOrderByAdjustmentDateDesc(clientId, effectiveOrgId, pageable);
        } else {
            page = stockAdjustmentRepository.findByClientIdOrderByAdjustmentDateDesc(clientId, pageable);
        }
        page.getContent().forEach(adj -> {
            adj.setCreatedByName(resolveUserName(adj.getCreatedBy()));
            adj.setUpdatedByName(resolveUserName(adj.getUpdatedBy()));
            if (adj.getLines() != null) {
                for (StockAdjustmentLine line : adj.getLines()) {
                    if (line.getProductId() != null) {
                        productRepository.findById(line.getProductId()).ifPresent(p -> {
                            line.setProductName(p.getName());
                            line.setSku(p.getProductCode());
                            if (p.getCategory() != null) {
                                line.setCategoryName(p.getCategory().getName());
                            }
                        });
                    }
                }
            }
        });
        return page;
    }


    public Page<StockTransfer> getTransfers(UUID orgId, Pageable pageable) {
        UUID clientId = TenantContext.getCurrentTenant();
        UUID effectiveOrgId = branchContext.getReadOrgId(orgId);
        Page<StockTransfer> page;
        if (effectiveOrgId != null) {
            page = stockTransferRepository.findByClientIdAndOrgIdOrDestOrgId(clientId, effectiveOrgId, pageable);
        } else {
            page = stockTransferRepository.findByClientIdOrderByTransferDateDesc(clientId, pageable);
        }
        page.getContent().forEach(t -> {
            t.setCreatedByName(resolveUserName(t.getCreatedBy()));
            t.setUpdatedByName(resolveUserName(t.getUpdatedBy()));
        });
        return page;
    }

    public StockTransfer getTransferById(UUID id) {
        StockTransfer transfer = stockTransferRepository.findById(id)
                .orElseThrow(() -> new com.restaurant.pos.common.exception.ResourceNotFoundException("Stock transfer not found with ID: " + id));

        transfer.setCreatedByName(resolveUserName(transfer.getCreatedBy()));
        transfer.setUpdatedByName(resolveUserName(transfer.getUpdatedBy()));

        if (transfer.getLines() != null) {
            for (StockTransferLine line : transfer.getLines()) {
                if (line.getProductId() != null) {
                    productRepository.findById(line.getProductId()).ifPresent(p -> {
                        line.setProductName(p.getName());
                        line.setSku(p.getProductCode());
                        if (p.getCategory() != null) {
                            line.setCategoryName(p.getCategory().getName());
                        }
                    });
                }
            }
        }
        return transfer;
    }

    public StockAdjustment getAdjustmentById(UUID id) {
        StockAdjustment adjustment = stockAdjustmentRepository.findById(id)
                .orElseThrow(() -> new com.restaurant.pos.common.exception.ResourceNotFoundException("Stock adjustment not found with ID: " + id));

        adjustment.setCreatedByName(resolveUserName(adjustment.getCreatedBy()));
        adjustment.setUpdatedByName(resolveUserName(adjustment.getUpdatedBy()));

        if (adjustment.getLines() != null) {
            for (StockAdjustmentLine line : adjustment.getLines()) {
                if (line.getProductId() != null) {
                    productRepository.findById(line.getProductId()).ifPresent(p -> {
                        line.setProductName(p.getName());
                        line.setSku(p.getProductCode());
                        if (p.getCategory() != null) {
                            line.setCategoryName(p.getCategory().getName());
                        }
                    });
                }
            }
        }

        return adjustment;
    }

    private List<StockSnapshot> appendRecipeProductsStock(List<StockSnapshot> snapshots, UUID clientId, UUID orgId, UUID warehouseId, boolean isConsolidated) {
        List<StockSnapshot> resultList = new ArrayList<>(snapshots);
        
        Map<UUID, BigDecimal> ingredientStockMap = new HashMap<>();
        for (StockSnapshot snap : resultList) {
            UUID prodId = snap.getProductId();
            BigDecimal qty = snap.getCurrentQuantity();
            if (prodId != null && qty != null) {
                ingredientStockMap.merge(prodId, qty, BigDecimal::add);
            }
        }
        
        List<com.restaurant.pos.product.domain.Product> products = productRepository.findByClientIdAndOrgIdOrGlobalAndIsActiveTrue(clientId, orgId);
        
        for (com.restaurant.pos.product.domain.Product p : products) {
            if (p.getRecipeLines() != null && !p.getRecipeLines().isEmpty()) {
                List<com.restaurant.pos.product.domain.ProductRecipe> activeLines = p.getRecipeLines().stream()
                        .filter(com.restaurant.pos.product.domain.ProductRecipe::isActive)
                        .collect(Collectors.toList());
                
                if (activeLines.isEmpty()) {
                    continue;
                }
                
                BigDecimal minAvailable = null;
                for (com.restaurant.pos.product.domain.ProductRecipe line : activeLines) {
                    if (line.getIngredient() == null || line.getQuantity() == null || line.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                        continue;
                    }
                    
                    UUID ingId = line.getIngredient().getId();
                    BigDecimal ingStock = ingredientStockMap.getOrDefault(ingId, BigDecimal.ZERO);
                    
                    BigDecimal possible = ingStock.divide(line.getQuantity(), 3, java.math.RoundingMode.DOWN);
                    if (minAvailable == null || possible.compareTo(minAvailable) < 0) {
                        minAvailable = possible;
                    }
                }
                
                if (minAvailable == null || minAvailable.compareTo(BigDecimal.ZERO) < 0) {
                    minAvailable = BigDecimal.ZERO;
                }
                
                boolean found = false;
                for (StockSnapshot existing : resultList) {
                    if (existing.getProductId().equals(p.getId())) {
                        existing.setCurrentQuantity(minAvailable);
                        existing.setLastUpdated(LocalDateTime.now());
                        found = true;
                        break;
                    }
                }
                
                if (!found) {
                    StockSnapshot virtualSnap = StockSnapshot.builder()
                            .id(null)
                            .clientId(clientId)
                            .orgId(orgId)
                            .warehouseId(isConsolidated ? null : warehouseId)
                            .productId(p.getId())
                            .variantId(null)
                            .currentQuantity(minAvailable)
                            .lastUpdated(LocalDateTime.now())
                            .build();
                    resultList.add(virtualSnap);
                }
            }
        }
        
        return resultList;
    }
}
