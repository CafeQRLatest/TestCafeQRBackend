package com.restaurant.pos.purchasing.domain;

import com.restaurant.pos.common.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "customers")
public class Customer extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Builder.Default
    private UUID id = null;

    @Column(length = 200, nullable = false)
    private String name;

    @Column(length = 50)
    private String phone;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "order_links", columnDefinition = "jsonb", nullable = false)
    private List<OrderLink> orderLinks = new ArrayList<>();

    @Column(length = 255)
    private String email;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(name = "gst_number", length = 50)
    private String gstNumber;

    @Builder.Default
    @Column(name = "customer_category", length = 50)
    private String customerCategory = "REGULAR";

    @Builder.Default
    @Column(name = "loyalty_points")
    private Integer loyaltyPoints = 0;

    @Builder.Default
    @Column(name = "credit_limit", precision = 15, scale = 2)
    private BigDecimal creditLimit = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "opening_balance", precision = 15, scale = 2)
    private BigDecimal openingBalance = BigDecimal.ZERO;

    @Column(name = "pricelist_id")
    private UUID pricelistId;

    @Builder.Default
    @JsonProperty("isActive")
    @Column(name = "isactive", length = 1)
    private String isactive = "Y";

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderLink {
        private UUID orderId;
        private Boolean isPrimary;
        private String attachedAt;
    }
}
