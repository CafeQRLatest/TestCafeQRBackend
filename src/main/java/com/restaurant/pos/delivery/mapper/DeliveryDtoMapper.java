package com.restaurant.pos.delivery.mapper;

import com.restaurant.pos.delivery.dto.DeliveryOrderResponseDto;
import com.restaurant.pos.order.domain.Order;
import com.restaurant.pos.order.domain.OrderLine;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class DeliveryDtoMapper {

    public DeliveryOrderResponseDto toResponseDto(Order order) {
        if (order == null) return null;

        List<Map<String, Object>> itemMaps = Collections.emptyList();
        if (order.getLines() != null) {
            itemMaps = order.getLines().stream().map(this::toLineMap).collect(Collectors.toList());
        }

        Map<String, String> parsedDesc = parseDescription(order.getDescription());

        return DeliveryOrderResponseDto.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .clientId(order.getClientId())
                .orgId(order.getOrgId())
                .orderStatus(order.getOrderStatus())
                .paymentStatus(order.getPaymentStatus())
                .orderSource(order.getOrderSource())
                .fulfillmentType(order.getFulfillmentType())
                .description(order.getDescription())
                .remarks(order.getRemarks())
                .reference(order.getReference())
                .orderDate(order.getOrderDate())
                .totalTaxableAmount(order.getGrossAmount())
                .totalTaxAmount(order.getTotalTaxAmount())
                .totalGrossAmount(order.getGrossAmount())
                .grandTotal(order.getGrandTotal())
                .customerName(parsedDesc.get("name"))
                .customerEmail(parsedDesc.get("email"))
                .customerPhone(parsedDesc.get("phone"))
                .deliveryAddress(parsedDesc.get("address"))
                .latitude(order.getLatitude())
                .longitude(order.getLongitude())
                .items(itemMaps)
                .build();
    }

    public Map<String, Object> toResponseMap(Order order) {
        if (order == null) return Collections.emptyMap();

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", order.getId());
        map.put("orderId", order.getId());
        map.put("orderNo", order.getOrderNo());
        map.put("orderStatus", order.getOrderStatus());
        map.put("paymentStatus", order.getPaymentStatus());
        map.put("fulfillmentType", order.getFulfillmentType() != null ? order.getFulfillmentType() : "DELIVERY");
        map.put("orderDate", order.getOrderDate() != null ? order.getOrderDate().toString() : null);
        map.put("totalTaxableAmount", order.getGrossAmount());
        map.put("totalTaxAmount", order.getTotalTaxAmount());
        map.put("totalGrossAmount", order.getGrossAmount());
        map.put("grandTotal", order.getGrandTotal());
        map.put("totalAmount", order.getGrandTotal());
        map.put("description", order.getDescription());
        map.put("remarks", order.getRemarks());
        map.put("reference", order.getReference());

        Map<String, String> parsedDesc = parseDescription(order.getDescription());
        map.put("customerName", parsedDesc.getOrDefault("name", ""));
        map.put("customerEmail", parsedDesc.getOrDefault("email", ""));
        map.put("customerPhone", parsedDesc.getOrDefault("phone", ""));
        map.put("deliveryAddress", parsedDesc.getOrDefault("address", ""));
        map.put("note", parsedDesc.getOrDefault("note", ""));

        if (order.getLines() != null) {
            List<Map<String, Object>> lineMaps = order.getLines().stream()
                    .map(this::toLineMap)
                    .collect(Collectors.toList());
            map.put("lines", lineMaps);
            map.put("items", lineMaps);
        } else {
            map.put("lines", Collections.emptyList());
            map.put("items", Collections.emptyList());
        }

        return map;
    }

    private Map<String, Object> toLineMap(OrderLine line) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", line.getId());
        item.put("productId", line.getProductId());
        item.put("productName", line.getProductName());
        item.put("quantity", line.getQuantity());
        item.put("unitPrice", line.getUnitPrice());
        item.put("lineTotal", line.getLineTotal());
        item.put("variantName", line.getProductName());
        item.put("variantId", line.getVariantId());
        return item;
    }

    private Map<String, String> parseDescription(String description) {
        Map<String, String> result = new HashMap<>();
        if (description == null || description.isBlank()) return result;

        String[] parts = description.split(" \\| ");
        for (String part : parts) {
            if (part.startsWith("Cust: ")) {
                result.put("name", part.substring(6).trim());
            } else if (part.startsWith("Email: ")) {
                result.put("email", part.substring(7).trim());
            } else if (part.startsWith("Phone: ")) {
                result.put("phone", part.substring(7).trim());
            } else if (part.startsWith("Addr: ")) {
                result.put("address", part.substring(6).trim());
            } else if (part.startsWith("Note: ")) {
                result.put("note", part.substring(6).trim());
            }
        }
        return result;
    }
}
