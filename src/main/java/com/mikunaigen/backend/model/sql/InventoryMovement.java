package com.mikunaigen.backend.model.sql;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "inventory_movements")
public class InventoryMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "inventory_id", nullable = false)
    private Integer inventoryId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity;

    @Column(name = "previous_stock", nullable = false, precision = 10, scale = 2)
    private BigDecimal previousStock;

    @Column(name = "new_stock", nullable = false, precision = 10, scale = 2)
    private BigDecimal newStock;

    @Column(name = "unit_cost", precision = 10, scale = 3)
    private BigDecimal unitCost;

    @Column(name = "movement_type", nullable = false, length = 50)
    private String movementType;

    @Column(length = 255)
    private String reason;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
