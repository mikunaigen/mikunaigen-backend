package com.mikunaigen.backend.model.sql;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, columnDefinition = "uuid")
    private RestaurantOrder restaurantOrder;

    @Column(name = "mongo_product_id", nullable = false, length = 64)
    private String mongoProductId;

    @Column(nullable = false)
    private Integer quantity = 1;

    @Column(name = "price_at_moment", precision = 12, scale = 2, nullable = false)
    private BigDecimal priceAtMoment;
}
