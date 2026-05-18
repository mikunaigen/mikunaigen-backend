package com.mikunaigen.backend.service.dashboard;

import com.mikunaigen.backend.model.sql.RestaurantOrder;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

public final class RestaurantOrderSpecs {

    private RestaurantOrderSpecs() {
    }

    public static Specification<RestaurantOrder> createdBetween(LocalDateTime from, LocalDateTime toExclusive) {
        return (root, q, cb) -> cb.and(
                cb.greaterThanOrEqualTo(root.get("createdAt"), from),
                cb.lessThan(root.get("createdAt"), toExclusive)
        );
    }

    public static Specification<RestaurantOrder> statusEquals(String status) {
        if (status == null || status.isBlank()) {
            return (root, q, cb) -> cb.conjunction();
        }
        return (root, q, cb) -> cb.equal(root.get("status"), status.trim());
    }

    public static Specification<RestaurantOrder> momentOfDayEquals(String v) {
        if (v == null || v.isBlank()) {
            return (root, q, cb) -> cb.conjunction();
        }
        return (root, q, cb) -> cb.equal(root.get("momentOfDay"), v.trim());
    }

    public static Specification<RestaurantOrder> dayOfWeekEquals(String v) {
        if (v == null || v.isBlank()) {
            return (root, q, cb) -> cb.conjunction();
        }
        return (root, q, cb) -> cb.equal(root.get("dayOfWeek"), v.trim());
    }

    public static Specification<RestaurantOrder> weatherConditionEquals(String v) {
        if (v == null || v.isBlank()) {
            return (root, q, cb) -> cb.conjunction();
        }
        return (root, q, cb) -> cb.equal(root.get("weatherCondition"), v.trim());
    }
}
