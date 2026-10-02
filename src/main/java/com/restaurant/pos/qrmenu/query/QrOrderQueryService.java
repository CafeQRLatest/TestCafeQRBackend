package com.restaurant.pos.qrmenu.query;

import com.restaurant.pos.client.domain.Client;
import com.restaurant.pos.client.repository.ClientRepository;
import com.restaurant.pos.client.repository.OrganizationRepository;
import com.restaurant.pos.common.dto.ConfigurationDto;
import com.restaurant.pos.common.exception.BusinessException;
import com.restaurant.pos.common.service.SystemConfigurationService;
import com.restaurant.pos.order.domain.Order;
import com.restaurant.pos.order.domain.OrderLine;
import com.restaurant.pos.product.domain.Product;
import com.restaurant.pos.product.repository.ProductRepository;
import com.restaurant.pos.qrmenu.repository.QrOrderRepository;
import com.restaurant.pos.subscription.domain.ModuleName;
import com.restaurant.pos.table.domain.RestaurantTable;
import com.restaurant.pos.table.repository.RestaurantTableRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * QR Order Query Service — the "Read Side" of in-process CQRS for QR Menu.
 * <p>
 * Handles all read-only operations:
 * <ul>
 *   <li>Menu retrieval (products, variants, categories)</li>
 *   <li>Table session info (metadata, active orders)</li>
 *   <li>Client/Org ID resolution from slugs</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QrOrderQueryService {

    private final ProductRepository productRepository;
    private final RestaurantTableRepository tableRepository;
    private final QrOrderRepository qrOrderRepository;
    private final ClientRepository clientRepository;
    private final OrganizationRepository organizationRepository;
    private final SystemConfigurationService systemConfigurationService;
    private final com.restaurant.pos.common.context.TimezoneResolver timezoneResolver;

    /**
     * Resolves client UUID from either a raw UUID string or a human-readable slug string.
     */
    public UUID resolveClientId(String identifier) {
        if (identifier == null || identifier.isBlank() || "null".equalsIgnoreCase(identifier)) {
            return null;
        }
        try {
            return UUID.fromString(identifier);
        } catch (IllegalArgumentException ex) {
            return clientRepository.findBySlugIgnoreCase(identifier)
                    .map(Client::getId)
                    .orElseThrow(() -> new BusinessException("Client not found for identifier: " + identifier));
        }
    }

    /**
     * Resolves organization UUID from either a raw UUID string or a human-readable slug / branch code.
     */
    public UUID resolveOrgId(UUID clientId, String identifier) {
        if (identifier == null || identifier.isBlank() || "null".equalsIgnoreCase(identifier)) {
            return null;
        }
        try {
            return UUID.fromString(identifier);
        } catch (IllegalArgumentException ex) {
            if (clientId == null) return null;
            return organizationRepository.findByClientIdAndSlugIgnoreCase(clientId, identifier)
                    .or(() -> organizationRepository.findByClientIdAndBranchCodeIgnoreCase(clientId, identifier))
                    .map(com.restaurant.pos.client.domain.Organization::getId)
                    .orElse(null);
        }
    }

    /**
     * Retrieves table metadata along with any active open order (Table Session) on that table.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getTableSessionInfo(String clientIdentifier, String orgIdentifier, String tableIdentifier) {
        UUID clientId = resolveClientId(clientIdentifier);
        UUID orgUuid = resolveOrgId(clientId, orgIdentifier);

        validateSubscription(clientId, orgUuid);

        RestaurantTable table = findTable(clientId, orgUuid, tableIdentifier);
        if (table == null) {
            return Map.of(
                    "found", false,
                    "message", "Table not found"
            );
        }

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("found", true);
        info.put("id", table.getId());
        info.put("tableNumber", table.getTableNumber());
        info.put("name", table.getName());
        info.put("status", table.getStatus());
        info.put("seatingCapacity", table.getSeatingCapacity());
        info.put("floor", table.getFloor());
        info.put("section", table.getSection());

        UUID effectiveClientId = table.getClientId() != null ? table.getClientId() : clientId;
        UUID effectiveOrgId = table.getOrgId() != null ? table.getOrgId() : orgUuid;

        ConfigurationDto qrConfig = systemConfigurationService
                .getConfigurationForClientAndBranch(effectiveClientId, effectiveOrgId);
        boolean onlinePayActive = qrConfig.isOnlinePaymentEnabled()
                && qrConfig.isOnlineDeliveryEnabled()
                && qrConfig.getRazorpayKeyId() != null
                && !qrConfig.getRazorpayKeyId().isBlank();
        info.put("onlinePaymentEnabled", onlinePayActive);

        boolean taxEnabled = qrConfig.isTaxEnabled();
        boolean pricesIncludeTax = qrConfig.isPricesIncludeTax();
        String taxLabelGlobal = (qrConfig.getTaxLabelGlobal() != null && !qrConfig.getTaxLabelGlobal().isBlank())
                ? qrConfig.getTaxLabelGlobal()
                : "GST";
        boolean taxSplitEnabled = qrConfig.isTaxSplitEnabled();
        BigDecimal defaultTaxRate = BigDecimal.ZERO;
        String defaultTaxName = taxLabelGlobal;

        if (taxEnabled && qrConfig.getTaxRates() != null && !qrConfig.getTaxRates().isEmpty()) {
            List<Object> rates = qrConfig.getTaxRates();
            String defaultTaxId = qrConfig.getTaxDefaultId();
            Map<String, Object> defaultRateMap = null;
            for (Object rObj : rates) {
                if (rObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> rMap = (Map<String, Object>) rObj;
                    if (defaultTaxId != null && defaultTaxId.equals(String.valueOf(rMap.get("id")))) {
                        defaultRateMap = rMap;
                        break;
                    }
                }
            }
            if (defaultRateMap == null && !rates.isEmpty() && rates.get(0) instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> rMap = (Map<String, Object>) rates.get(0);
                defaultRateMap = rMap;
            }
            if (defaultRateMap != null) {
                if (defaultRateMap.get("value") != null) {
                    try {
                        defaultTaxRate = new BigDecimal(String.valueOf(defaultRateMap.get("value")));
                    } catch (Exception ignored) {}
                }
                if (defaultRateMap.get("name") != null) {
                    defaultTaxName = String.valueOf(defaultRateMap.get("name"));
                }
            }
        }

        info.put("taxEnabled", taxEnabled);
        info.put("pricesIncludeTax", pricesIncludeTax);
        info.put("taxLabelGlobal", taxLabelGlobal);
        info.put("taxSplitEnabled", taxSplitEnabled);
        info.put("taxRate", defaultTaxRate);
        info.put("taxName", defaultTaxName);
        info.put("taxRates", qrConfig.getTaxRates());
        info.put("taxDefaultId", qrConfig.getTaxDefaultId());
        info.put("currencySymbol", (qrConfig.getCurrencySymbol() != null && !qrConfig.getCurrencySymbol().isBlank())
                ? qrConfig.getCurrencySymbol()
                : "₹");
        info.put("currencyDecimalPlaces", qrConfig.getCurrencyDecimalPlaces() != null ? qrConfig.getCurrencyDecimalPlaces() : 2);

        // Inventory & Loyalty module status (for frontend to conditionally display features)
        info.put("inventoryEnabled", qrConfig.isInventoryEnabled());
        info.put("loyaltyEnabled", qrConfig.isLoyaltyEnabled());

        clientRepository.findById(effectiveClientId).ifPresent(client -> {
            info.put("brandColor", client.getBrandColor() != null ? client.getBrandColor() : "#f97316");
            info.put("clientName", client.getName() != null ? client.getName() : "Our Restaurant");
            info.put("restaurantName", client.getName() != null ? client.getName() : "Our Restaurant");
            info.put("logoUrl", client.getLogoUrl());
            info.put("clientSlug", client.getSlug());
        });

        if (effectiveOrgId != null) {
            organizationRepository.findById(effectiveOrgId).ifPresent(org -> {
                if (org.getLogoUrl() != null && !org.getLogoUrl().isBlank()) {
                    info.put("logoUrl", org.getLogoUrl());
                }
                String bName = (org.getName() != null && !org.getName().isBlank()) ? org.getName() : org.getBranchCode();
                info.put("branchName", bName);
                info.put("branchCode", org.getBranchCode());
                info.put("branchSlug", org.getSlug() != null ? org.getSlug() : org.getBranchCode());
            });
        }

        // Check for active un-settled order on this table.
        // If table is explicitly AVAILABLE, previous tab has been cleared/settled.
        boolean isTableAvailable = "AVAILABLE".equalsIgnoreCase(String.valueOf(table.getStatus()));
        ZoneId branchZone = timezoneResolver.resolveTimezone(effectiveClientId, effectiveOrgId);
        LocalDateTime sessionCutoff = LocalDateTime.now(branchZone).minusHours(16);
        List<Order> activeOrders = isTableAvailable
                ? Collections.emptyList()
                : qrOrderRepository.findActiveOrdersByTable(clientId, orgUuid, table.getId(), table.getTableNumber(), sessionCutoff);

        if (!activeOrders.isEmpty()) {
            Order activeOrder = activeOrders.get(0);
            Map<String, Object> activeOrderMap = new LinkedHashMap<>();
            activeOrderMap.put("id", activeOrder.getId());
            activeOrderMap.put("orderNo", activeOrder.getOrderNo());
            activeOrderMap.put("invoiceNo", activeOrder.getInvoiceNo());
            activeOrderMap.put("dailyBillNo", activeOrder.getDailyBillNo());
            activeOrderMap.put("orderStatus", activeOrder.getOrderStatus());
            activeOrderMap.put("paymentStatus", activeOrder.getPaymentStatus());
            activeOrderMap.put("grandTotal", activeOrder.getGrandTotal());
            activeOrderMap.put("totalAmount", activeOrder.getTotalAmount());
            activeOrderMap.put("totalTaxAmount", activeOrder.getTotalTaxAmount());
            activeOrderMap.put("totalDiscountAmount", activeOrder.getTotalDiscountAmount());
            activeOrderMap.put("tableNumber", activeOrder.getTableNumber());
            activeOrderMap.put("revisionNumber", activeOrder.getRevisionNumber());
            activeOrderMap.put("createdAt", activeOrder.getCreatedAt());
            activeOrderMap.put("updatedAt", activeOrder.getUpdatedAt());
            activeOrderMap.put("isStockDeducted", activeOrder.getIsStockDeducted());
            activeOrderMap.put("currencyId", activeOrder.getCurrencyId());

            List<Map<String, Object>> linesList = new ArrayList<>();
            if (activeOrder.getLines() != null) {
                for (OrderLine line : activeOrder.getLines()) {
                    if (line.getIsactive() != null && !"Y".equalsIgnoreCase(line.getIsactive())) {
                        continue;
                    }
                    Map<String, Object> lm = new LinkedHashMap<>();
                    lm.put("id", line.getId());
                    lm.put("productId", line.getProductId());
                    lm.put("variantId", line.getVariantId());
                    lm.put("productName", line.getProductName());
                    lm.put("categoryName", line.getCategoryName());
                    lm.put("quantity", line.getQuantity());
                    lm.put("unitPrice", line.getUnitPrice());
                    lm.put("lineTotal", line.getLineTotal());
                    lm.put("taxRate", line.getTaxRate());
                    lm.put("description", line.getDescription());
                    linesList.add(lm);
                }
            }
            activeOrderMap.put("lines", linesList);
            info.put("activeOrder", activeOrderMap);
        } else {
            info.put("activeOrder", null);
        }

        return info;
    }

    /**
     * Retrieves the full active menu for a given restaurant.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getMenu(UUID clientId, UUID orgId) {
        validateSubscription(clientId, orgId);
        List<Product> products = productRepository
                .findByClientIdAndOrgIdOrGlobalAndIsActiveTrue(clientId, orgId);

        return products.stream()
                .filter(Product::isAvailable)
                .filter(p -> !p.isIngredient())
                .filter(p -> p.getProductType() == null || !"INGREDIENT".equalsIgnoreCase(p.getProductType().trim()))
                .map(p -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", p.getId());
                    item.put("name", p.getName());
                    item.put("description", p.getDescription());
                    item.put("price", p.getPrice());
                    item.put("imageUrl", p.getImageUrl());
                    item.put("category", p.getCategory() != null ? p.getCategory().getName() : "Others");
                    item.put("isVeg", !p.isPackagedGood());
                    item.put("isAvailable", p.isAvailable());
                    item.put("productType", p.getProductType());
                    item.put("taxRate", p.getTaxRate());
                    item.put("taxCode", p.getTaxCode());

                    List<Map<String, Object>> variants = new ArrayList<>();
                    try {
                        if (p.getVariantMappings() != null) {
                            for (com.restaurant.pos.product.domain.ProductVariantMapping mapping : p.getVariantMappings()) {
                                if (mapping.getVariantGroup() != null && mapping.getVariantGroup().getOptions() != null) {
                                    for (com.restaurant.pos.product.domain.VariantOption opt : mapping.getVariantGroup().getOptions()) {
                                        if (opt.isActive()) {
                                            Map<String, Object> vMap = new LinkedHashMap<>();
                                            vMap.put("id", opt.getId().toString());
                                            vMap.put("name", opt.getName());
                                            java.math.BigDecimal addPrice = opt.getAdditionalPrice() != null ? opt.getAdditionalPrice() : java.math.BigDecimal.ZERO;
                                            java.math.BigDecimal finalPrice = p.getPrice() != null ? p.getPrice().add(addPrice) : addPrice;
                                            if (p.getVariantPricings() != null) {
                                                for (com.restaurant.pos.product.domain.VariantPricing vp : p.getVariantPricings()) {
                                                    if (vp.getVariantOption() != null && vp.getVariantOption().getId().equals(opt.getId()) && vp.getOverridePrice() != null) {
                                                        finalPrice = vp.getOverridePrice();
                                                        break;
                                                    }
                                                }
                                            }
                                            vMap.put("price", finalPrice);
                                            vMap.put("groupName", mapping.getVariantGroup().getName());
                                            variants.add(vMap);
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Exception ignored) {
                    }
                    if (!variants.isEmpty()) {
                        item.put("variants", variants);
                    }

                    return item;
                })
                .collect(Collectors.toList());
    }

    /**
     * Returns the default organization for a given client (for legacy QR code redirects).
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getDefaultOrg(String clientIdentifier) {
        UUID clientUuid = resolveClientId(clientIdentifier);
        List<com.restaurant.pos.client.domain.Organization> orgs = organizationRepository.findAllByClientId(clientUuid);

        if (orgs == null || orgs.isEmpty()) {
            return Map.of("orgId", clientUuid);
        }

        com.restaurant.pos.client.domain.Organization defaultOrg = orgs.stream()
                .filter(o -> "HQ".equals(o.getBranchCode()))
                .findFirst()
                .orElse(orgs.get(0));

        return Map.of(
                "orgId", defaultOrg.getId(),
                "slug", defaultOrg.getSlug() != null ? defaultOrg.getSlug() : defaultOrg.getBranchCode()
        );
    }

    // ── Private helpers ──

    private RestaurantTable findTable(UUID clientId, UUID orgId, String tableIdentifier) {
        if (tableIdentifier == null || tableIdentifier.isBlank()) return null;
        try {
            UUID tid = UUID.fromString(tableIdentifier);
            return tableRepository.findById(tid).orElse(null);
        } catch (IllegalArgumentException ex) {
            List<RestaurantTable> tables;
            if (orgId != null) {
                tables = tableRepository.findByClientIdAndOrgIdOrderByDisplayOrderAscTableNumberAsc(clientId, orgId);
            } else {
                tables = tableRepository.findByClientIdOrderByDisplayOrderAscTableNumberAsc(clientId);
            }
            return tables.stream()
                    .filter(t -> tableIdentifier.equalsIgnoreCase(t.getTableNumber()) || tableIdentifier.equalsIgnoreCase(t.getName()))
                    .findFirst()
                    .orElse(null);
        }
    }

    private void validateSubscription(UUID clientId, UUID orgId) {
        if (clientId != null) {
            Client client = clientRepository.findById(clientId).orElse(null);
            if (client == null || !client.isSubscriptionActive()) {
                throw new BusinessException("The restaurant's subscription has expired. Online menu and ordering are currently unavailable.");
            }

            boolean tableQrActive = false;
            String status = client.getSubscriptionStatus();
            if (status != null && "TRIAL".equalsIgnoreCase(status.trim()) && client.getSubscriptionExpiryDate() != null && client.getSubscriptionExpiryDate().isAfter(java.time.LocalDateTime.now())) {
                tableQrActive = true;
            } else {
                tableQrActive = systemConfigurationService.isModuleActive(clientId, orgId, ModuleName.TABLE_QR);
            }

            if (!tableQrActive) {
                throw new BusinessException("Online Ordering is not active for this branch. Please contact restaurant administration.");
            }
        }
    }
}
