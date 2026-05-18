package com.mikunaigen.backend.model.sql;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "inventory")
public class Inventory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    private String name;
    private Double stockQuantity;
    private String unit;
    
    private String category;
    private Double price;
    
    @Column(columnDefinition = "TEXT", name = "image_base64")
    private String imageBase64;
    
    private boolean isDeleted = false;
}