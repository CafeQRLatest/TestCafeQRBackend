package com.restaurant.pos.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Transient print options for order lifecycle actions")
public class OrderPrintOptionsRequest {
    @Schema(description = "Print kinds that this terminal will print locally, e.g. BILL")
    private List<String> skipAutoPrintKinds;
}
