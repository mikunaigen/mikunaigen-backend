package com.mikunaigen.backend.model.nosql;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Data
@Document(collection = "shopping_carts")
public class ShoppingCart {
    @Id
    private String userId;

    private List<CartItemMongo> items = new ArrayList<>();
}
