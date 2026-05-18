package com.mikunaigen.backend.repository.sql;

import com.mikunaigen.backend.model.sql.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RecipeRepository extends JpaRepository<Recipe, Integer> {

    List<Recipe> findByMongoProductId(String mongoProductId);

    List<Recipe> findByMongoProductIdAndIsDeletedFalse(String mongoProductId);

    @Query("SELECT DISTINCT r.mongoProductId FROM Recipe r WHERE r.ingredient.id = :invId AND r.isDeleted = false")
    List<String> findDistinctActiveMongoProductIdsByIngredientId(@Param("invId") Integer inventoryId);

    @Query(value = """
            SELECT r.mongo_product_id,
                   COALESCE(SUM(r.quantity_to_subtract * i.price), 0)
            FROM recipes r
            JOIN inventory i ON i.id = r.ingredient_id
            WHERE r.is_deleted = false
            GROUP BY r.mongo_product_id
            """, nativeQuery = true)
    List<Object[]> sumCostoRecetaActivaPorProducto();
}
