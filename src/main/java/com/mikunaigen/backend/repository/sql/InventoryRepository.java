package com.mikunaigen.backend.repository.sql;

import com.mikunaigen.backend.model.sql.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Integer> {

    List<Inventory> findAllByIsDeletedFalse();

    boolean existsByNameAndIsDeletedFalse(String name);

    boolean existsByNameIgnoreCaseAndIsDeletedFalse(String name);

    boolean existsByNameIgnoreCaseAndIsDeletedFalseAndIdNot(String name, Integer id);

    Optional<Inventory> findByIdAndIsDeletedFalse(Integer id);

    @Query("SELECT i.id, i.name, i.stockQuantity, i.price, i.category FROM Inventory i WHERE i.isDeleted = false")
    List<Object[]> findAllStockProjection();
}
