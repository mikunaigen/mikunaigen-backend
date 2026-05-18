package com.mikunaigen.backend.repository.sql;

import com.mikunaigen.backend.model.sql.RestaurantOrder;
import com.mikunaigen.backend.model.sql.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
public class DashboardOrderTupleSupport {

    @PersistenceContext
    private EntityManager em;

    public List<OrderDashRow> fetch(Specification<RestaurantOrder> spec) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<RestaurantOrder> root = cq.from(RestaurantOrder.class);
        Join<RestaurantOrder, User> pb = root.join("processedBy", JoinType.LEFT);
        Join<RestaurantOrder, User> dp = root.join("deliveryPerson", JoinType.LEFT);
        cq.multiselect(
                root.get("status"),
                root.get("totalPrice"),
                root.get("createdAt"),
                root.get("deliveredAt"),
                root.get("deliveryAssignedAt"),
                root.get("processedAt"),
                root.get("dayOfWeek"),
                root.get("weatherTempC"),
                root.get("momentOfDay"),
                root.get("weatherCondition"),
                root.get("isRated"),
                pb.get("id"),
                pb.get("fullName"),
                dp.get("id"),
                dp.get("fullName")
        );
        Predicate pred = spec.toPredicate(root, cq, cb);
        if (pred != null) {
            cq.where(pred);
        }
        List<Tuple> tuples = em.createQuery(cq).getResultList();
        List<OrderDashRow> out = new ArrayList<>(tuples.size());
        for (Tuple t : tuples) {
            out.add(new OrderDashRow(
                    t.get(0, String.class),
                    t.get(1, BigDecimal.class),
                    t.get(2, LocalDateTime.class),
                    t.get(3, LocalDateTime.class),
                    t.get(4, LocalDateTime.class),
                    t.get(5, LocalDateTime.class),
                    t.get(6, String.class),
                    t.get(7, Double.class),
                    t.get(8, String.class),
                    t.get(9, String.class),
                    t.get(10, Boolean.class),
                    t.get(11, UUID.class),
                    t.get(12, String.class),
                    t.get(13, UUID.class),
                    t.get(14, String.class)
            ));
        }
        return out;
    }

    public record OrderDashRow(
            String status,
            BigDecimal totalPrice,
            LocalDateTime createdAt,
            LocalDateTime deliveredAt,
            LocalDateTime deliveryAssignedAt,
            LocalDateTime processedAt,
            String dayOfWeek,
            Double weatherTempC,
            String momentOfDay,
            String weatherCondition,
            Boolean isRated,
            UUID processedById,
            String processedByName,
            UUID deliveryPersonId,
            String deliveryPersonName
    ) {
    }
}
