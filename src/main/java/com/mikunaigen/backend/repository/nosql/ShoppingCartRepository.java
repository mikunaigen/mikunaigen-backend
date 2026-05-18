package com.mikunaigen.backend.repository.nosql;

import com.mikunaigen.backend.model.nosql.ShoppingCart;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ShoppingCartRepository extends MongoRepository<ShoppingCart, String> {
}
