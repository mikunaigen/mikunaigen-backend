package com.mikunaigen.backend.model.nosql;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartItemMongo {
    private String productId;
    private int quantity;
}
