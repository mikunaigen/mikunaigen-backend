package com.mikunaigen.backend.repository.sql;

import com.mikunaigen.backend.model.sql.RestaurantOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RestaurantOrderRepository extends JpaRepository<RestaurantOrder, UUID>, JpaSpecificationExecutor<RestaurantOrder> {

    List<RestaurantOrder> findByStatusOrderByCreatedAtDesc(String status);

    List<RestaurantOrder> findByStatusInOrderByCreatedAtAsc(Collection<String> statuses);

    List<RestaurantOrder> findByStatusInAndDeliveryPerson_IdOrderByCreatedAtAsc(Collection<String> statuses, UUID deliveryPersonId);

    List<RestaurantOrder> findByClient_IdAndStatusNotInOrderByCreatedAtDesc(UUID clientId, Collection<String> statuses);

    List<RestaurantOrder> findByClient_IdOrderByCreatedAtDesc(UUID clientId);
    boolean existsByClient_IdAndStatusIn(UUID clientId, Collection<String> statuses);

    @Query("SELECT DISTINCT o FROM RestaurantOrder o LEFT JOIN FETCH o.deliveryPerson WHERE o.client.id = :clientId AND o.status NOT IN :excluded ORDER BY o.createdAt DESC")
    List<RestaurantOrder> loadSeguimientoPendientes(
            @Param("clientId") UUID clientId,
            @Param("excluded") Collection<String> excluded);

    @Query("SELECT DISTINCT o FROM RestaurantOrder o LEFT JOIN FETCH o.deliveryPerson WHERE o.client.id = :clientId AND o.status IN :statuses ORDER BY o.createdAt DESC")
    List<RestaurantOrder> loadSeguimientoFinalizados(
            @Param("clientId") UUID clientId,
            @Param("statuses") Collection<String> statuses);

    @Query("SELECT COUNT(o) FROM RestaurantOrder o WHERE o.status = 'ENTREGADO' AND o.createdAt >= :from AND o.createdAt < :toEx")
    long countEntregadosCreatedBetween(@Param("from") LocalDateTime from, @Param("toEx") LocalDateTime toExclusive);

    @Query("SELECT COUNT(o) FROM RestaurantOrder o WHERE o.status = 'ENTREGADO' AND o.isRated = true AND o.createdAt >= :from AND o.createdAt < :toEx")
    long countEntregadosRatedCreatedBetween(@Param("from") LocalDateTime from, @Param("toEx") LocalDateTime toExclusive);

    @Query(value = """
            SELECT o.client_id, COUNT(*) AS cnt, COALESCE(SUM(o.total_price), 0) AS gasto
            FROM orders o
            WHERE o.status = 'ENTREGADO'
            AND o.created_at >= :from
            AND o.created_at < :toEx
            GROUP BY o.client_id
            """, nativeQuery = true)
    List<Object[]> aggregateEntregadosPorCliente(@Param("from") LocalDateTime from, @Param("toEx") LocalDateTime toExclusive);

    @Query(value = """
            SELECT CAST(o.created_at AS date) AS d, COUNT(*)
            FROM orders o
            WHERE o.created_at >= :from AND o.created_at < :toEx
            GROUP BY CAST(o.created_at AS date)
            ORDER BY d
            """, nativeQuery = true)
    List<Object[]> countOrdersPerDay(@Param("from") LocalDateTime from, @Param("toEx") LocalDateTime toExclusive);

    @Query("SELECT MIN(o.createdAt) FROM RestaurantOrder o")
    Optional<LocalDateTime> findMinCreatedAt();
}
