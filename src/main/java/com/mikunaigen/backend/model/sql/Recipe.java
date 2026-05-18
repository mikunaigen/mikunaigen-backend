package com.mikunaigen.backend.model.sql;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "recipes")
public class Recipe {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "mongo_product_id")
    private String mongoProductId;

    @ManyToOne
    @JoinColumn(name = "ingredient_id")
    private Inventory ingredient;

    private Double quantityToSubtract;
    private boolean isDeleted = false;
}