package com.mikunaigen.backend.repository.sql;

import com.mikunaigen.backend.model.sql.CalificacionReceta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface CalificacionRecetaRepository extends JpaRepository<CalificacionReceta, Long> {

    Optional<CalificacionReceta> findByInferenciaId(UUID inferenciaId);

    boolean existsByInferenciaId(UUID inferenciaId);

    @Query("SELECT COALESCE(AVG(c.estrellas), 0) FROM CalificacionReceta c")
    double promedioEstrellas();

    @Query("""
            SELECT COALESCE(AVG(c.estrellas), 0) FROM CalificacionReceta c
            WHERE c.fechaCalificacion >= :desde AND c.fechaCalificacion <= :hasta
            """)
    double promedioEstrellasEntre(
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta);
}
