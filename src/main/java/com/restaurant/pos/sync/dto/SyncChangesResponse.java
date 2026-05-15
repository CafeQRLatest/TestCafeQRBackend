package com.restaurant.pos.sync.dto;

import com.restaurant.pos.common.dto.ConfigurationDto;
import com.restaurant.pos.order.dto.OrderSummaryDto;
import com.restaurant.pos.product.domain.Category;
import com.restaurant.pos.product.domain.Uom;
import com.restaurant.pos.product.dto.ProductListDto;
import com.restaurant.pos.product.dto.VariantGroupDto;
import com.restaurant.pos.table.domain.RestaurantTable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncChangesResponse {
    private Instant since;
    private Instant serverTime;
    private SyncBootstrapResponse snapshot;
    private List<ProductListDto> products;
    private List<Category> categories;
    private List<Uom> uoms;
    private List<VariantGroupDto> variantGroups;
    private List<RestaurantTable> tables;
    private List<OrderSummaryDto> orders;
    private ConfigurationDto configuration;
}
