package com.restaurant.pos.order.controller;

import com.restaurant.pos.common.dto.ApiResponse;
import com.restaurant.pos.order.dto.report.SalesSummaryDto;
import com.restaurant.pos.order.service.ReportService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportControllerTest {

    @Test
    void salesSummaryAllowsRangesLongerThanThirtyOneDays() {
        ReportService reportService = mock(ReportService.class);
        ReportController controller = new ReportController(reportService);
        Instant from = Instant.parse("2025-12-31T18:30:00Z");
        Instant to = Instant.parse("2026-05-27T18:29:00Z");
        SalesSummaryDto summary = SalesSummaryDto.builder()
                .totalOrders(1)
                .grandTotal(BigDecimal.TEN)
                .build();
        when(reportService.getSalesSummary(from, to)).thenReturn(summary);

        ResponseEntity<ApiResponse<SalesSummaryDto>> response = controller.getSalesSummary(from, to);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isSameAs(summary);
        verify(reportService).getSalesSummary(from, to);
    }

    @Test
    void salesSummaryRejectsReversedRange() {
        ReportController controller = new ReportController(mock(ReportService.class));

        assertThatThrownBy(() -> controller.getSalesSummary(
                Instant.parse("2026-05-27T18:29:00Z"),
                Instant.parse("2025-12-31T18:30:00Z")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("before");
    }
}
