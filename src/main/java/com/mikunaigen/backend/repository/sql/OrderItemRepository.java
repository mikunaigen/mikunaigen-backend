package com.mikunaigen.backend.repository.sql;

import com.mikunaigen.backend.model.sql.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<OrderItem, Integer> {

    List<OrderItem> findByRestaurantOrder_Id(UUID orderId);

    List<OrderItem> findByRestaurantOrder_IdIn(Collection<UUID> orderIds);

    @Query("""
            SELECT CASE WHEN COUNT(oi) > 0 THEN true ELSE false END
            FROM OrderItem oi
            JOIN oi.restaurantOrder o
            WHERE oi.mongoProductId = :mongoId
            AND o.status IN :statuses
            """)
    boolean existsByMongoProductIdAndOrderStatusIn(
            @Param("mongoId") String mongoProductId,
            @Param("statuses") Collection<String> statuses);

    @Query(value = """
            SELECT oi.mongo_product_id,
                   COALESCE(SUM(oi.quantity), 0),
                   COALESCE(SUM(oi.price_at_moment * oi.quantity), 0)
            FROM order_items oi
            JOIN orders o ON o.id = oi.order_id
            WHERE o.status = 'ENTREGADO'
            AND o.created_at >= :from
            AND o.created_at < :toEx
            GROUP BY oi.mongo_product_id
            """, nativeQuery = true)
    List<Object[]> aggregateVentasPorProductoEntregados(
            @Param("from") LocalDateTime from,
            @Param("toEx") LocalDateTime toExclusive
    );
}
