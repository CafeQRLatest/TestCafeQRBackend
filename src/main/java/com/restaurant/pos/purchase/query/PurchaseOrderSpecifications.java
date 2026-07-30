package com.restaurant.pos.purchase.query;

import com.restaurant.pos.order.domain.Order;
import com.restaurant.pos.order.domain.OrderType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Reusable Specification builder for Purchase Order queries.
 */
public class PurchaseOrderSpecifications {

    public static Specification<Order> withFilters(PurchaseOrderSearchRequest request, UUID clientId, UUID orgId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("clientId"), clientId));
            predicates.add(cb.equal(root.get("orderType"), OrderType.PURCHASE));

            if (orgId != null) {
                predicates.add(cb.equal(root.get("orgId"), orgId));
            }
            if (request.getVendorId() != null) {
                predicates.add(cb.equal(root.get("vendorId"), request.getVendorId()));
            }
            if (request.getWarehouseId() != null) {
                predicates.add(cb.equal(root.get("warehouseId"), request.getWarehouseId()));
            }
            if (request.getStatus() != null) {
                predicates.add(cb.equal(cb.upper(root.get("orderStatus")), request.getStatus().name()));
            }
            if (StringUtils.hasText(request.getPaymentMethod())) {
                predicates.add(cb.equal(cb.upper(root.get("paymentMethod")), request.getPaymentMethod().toUpperCase()));
            }
            if (request.getFromDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("orderDate"), request.getFromDate()));
            }
            if (request.getToDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("orderDate"), request.getToDate()));
            }
            if (StringUtils.hasText(request.getSearchTerm())) {
                String pattern = "%" + request.getSearchTerm().trim().toLowerCase() + "%";
                Predicate matchOrderNo = cb.like(cb.lower(root.get("orderNo")), pattern);
                Predicate matchRef = cb.like(cb.lower(root.get("reference")), pattern);
                predicates.add(cb.or(matchOrderNo, matchRef));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
