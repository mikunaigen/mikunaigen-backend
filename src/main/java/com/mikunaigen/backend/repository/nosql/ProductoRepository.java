package com.mikunaigen.backend.repository.nosql;

import com.mikunaigen.backend.model.nosql.Producto;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface ProductoRepository extends MongoRepository<Producto, String> {

    @Query(value = "{ 'isDeleted': false }", fields = "{ 'name': 1, 'price': 1, 'category': 1 }")
    List<Producto> findByIsDeletedFalseLight();

    List<Producto> findByIsDeletedFalse();

    long countByIsDeletedFalse();
    
    List<Producto> findByCategoryAndIsDeletedFalse(String category);

    boolean existsByNameIgnoreCaseAndIsDeletedFalse(String name);

    boolean existsByNameIgnoreCaseAndIsDeletedFalseAndIdNot(String name, String id);
}
