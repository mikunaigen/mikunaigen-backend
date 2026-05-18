package com.mikunaigen.backend.repository.sql;

import com.mikunaigen.backend.model.sql.OrderRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface OrderRatingRepository extends JpaRepository<OrderRating, Long> {

    boolean existsByOrder_Id(UUID orderId);

    @Query("SELECT AVG(r.stars) FROM OrderRating r WHERE r.order.createdAt >= :from AND r.order.createdAt < :to")
    Double avgStarsBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT r.stars, COUNT(r) FROM OrderRating r WHERE r.order.createdAt >= :from AND r.order.createdAt < :to GROUP BY r.stars ORDER BY r.stars")
    List<Object[]> countStarsGroupedBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
