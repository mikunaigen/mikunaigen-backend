package com.mikunaigen.backend.repository.sql;

import com.mikunaigen.backend.model.sql.InventoryMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Integer> {
    List<InventoryMovement> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to);

    @Query(value = """
            SELECT COALESCE(SUM(m.quantity * COALESCE(m.unit_cost, 0)), 0)
            FROM inventory_movements m
            WHERE m.created_at >= :from AND m.created_at < :toEx
            AND UPPER(m.movement_type) = 'SALIDA'
            AND (:tipo IS NULL OR m.movement_type ILIKE :tipo)
            """, nativeQuery = true)
    BigDecimal sumCostoSalida(@Param("from") LocalDateTime from, @Param("toEx") LocalDateTime toExclusive, @Param("tipo") String tipoMovimiento);

    @Query(value = """
            SELECT COALESCE(SUM(m.quantity * COALESCE(m.unit_cost, 0)), 0)
            FROM inventory_movements m
            WHERE m.created_at >= :from AND m.created_at < :toEx
            AND UPPER(m.movement_type) = 'ABASTECIMIENTO'
            AND (:tipo IS NULL OR m.movement_type ILIKE :tipo)
            """, nativeQuery = true)
    BigDecimal sumCostoAbastecimiento(@Param("from") LocalDateTime from, @Param("toEx") LocalDateTime toExclusive, @Param("tipo") String tipoMovimiento);

    @Query(value = """
            SELECT COALESCE(SUM(m.quantity), 0)
            FROM inventory_movements m
            WHERE m.created_at >= :from AND m.created_at < :toEx
            AND UPPER(m.movement_type) = 'SALIDA'
            AND (:tipo IS NULL OR m.movement_type ILIKE :tipo)
            """, nativeQuery = true)
    BigDecimal sumCantidadSalida(@Param("from") LocalDateTime from, @Param("toEx") LocalDateTime toExclusive, @Param("tipo") String tipoMovimiento);

    @Query(value = """
            SELECT DISTINCT m.inventory_id
            FROM inventory_movements m
            WHERE m.created_at >= :from AND m.created_at < :toEx
            AND (:tipo IS NULL OR m.movement_type ILIKE :tipo)
            """, nativeQuery = true)
    List<Integer> findDistinctInventoryIdsMovedInRange(@Param("from") LocalDateTime from, @Param("toEx") LocalDateTime toExclusive, @Param("tipo") String tipoMovimiento);

    @Query(value = """
            SELECT CAST(m.inventory_id AS varchar), SUM(m.quantity)
            FROM inventory_movements m
            WHERE m.created_at >= :from AND m.created_at < :toEx
            AND UPPER(m.movement_type) = 'SALIDA'
            AND (:tipo IS NULL OR m.movement_type ILIKE :tipo)
            GROUP BY m.inventory_id
            ORDER BY SUM(m.quantity) DESC
            LIMIT 10
            """, nativeQuery = true)
    List<Object[]> topConsumoInsumo(@Param("from") LocalDateTime from, @Param("toEx") LocalDateTime toExclusive, @Param("tipo") String tipoMovimiento);

    @Query(value = """
            SELECT COALESCE(i.category, ''), COALESCE(SUM(m.quantity * COALESCE(m.unit_cost, 0)), 0)
            FROM inventory_movements m
            JOIN inventory i ON i.id = m.inventory_id
            WHERE m.created_at >= :from AND m.created_at < :toEx
            AND UPPER(m.movement_type) = 'SALIDA'
            AND (:tipo IS NULL OR m.movement_type ILIKE :tipo)
            GROUP BY i.category
            """, nativeQuery = true)
    List<Object[]> sumConsumoPorCategoria(@Param("from") LocalDateTime from, @Param("toEx") LocalDateTime toExclusive, @Param("tipo") String tipoMovimiento);

    @Query(value = """
            SELECT to_char(date_trunc('week', m.created_at), 'YYYY-MM-DD'),
                   COALESCE(SUM(m.quantity * COALESCE(m.unit_cost, 0)), 0)
            FROM inventory_movements m
            WHERE m.created_at >= :from AND m.created_at < :toEx
            AND UPPER(m.movement_type) = 'ABASTECIMIENTO'
            AND (:tipo IS NULL OR m.movement_type ILIKE :tipo)
            GROUP BY date_trunc('week', m.created_at)
            ORDER BY 1
            """, nativeQuery = true)
    List<Object[]> sumAbastecimientoPorSemana(@Param("from") LocalDateTime from, @Param("toEx") LocalDateTime toExclusive, @Param("tipo") String tipoMovimiento);

    @Query(value = """
            SELECT m.inventory_id,
                   CAST(m.created_at AS date) AS d,
                   COALESCE(SUM(CASE WHEN UPPER(TRIM(m.movement_type)) = 'SALIDA' THEN ABS(m.quantity) ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN UPPER(TRIM(m.movement_type)) = 'ABASTECIMIENTO' THEN m.quantity ELSE 0 END), 0)
            FROM inventory_movements m
            WHERE m.created_at >= :from AND m.created_at < :toEx
            GROUP BY m.inventory_id, CAST(m.created_at AS date)
            ORDER BY m.inventory_id, d
            """, nativeQuery = true)
    List<Object[]> aggregateDailyByInventory(@Param("from") LocalDateTime from, @Param("toEx") LocalDateTime toExclusive);

    @Query("SELECT MIN(m.createdAt) FROM InventoryMovement m")
    Optional<LocalDateTime> findMinCreatedAt();
}
