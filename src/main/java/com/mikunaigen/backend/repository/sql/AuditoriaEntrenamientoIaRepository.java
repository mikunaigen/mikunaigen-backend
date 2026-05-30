package com.mikunaigen.backend.repository.sql;

import com.mikunaigen.backend.model.sql.AuditoriaEntrenamientoIa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface AuditoriaEntrenamientoIaRepository extends JpaRepository<AuditoriaEntrenamientoIa, Integer> {

    @Query("""
            SELECT COUNT(a) FROM AuditoriaEntrenamientoIa a
            WHERE a.desplegadoProduccion = true
            AND a.fechaFin >= :desde AND a.fechaFin <= :hasta
            """)
    long contarDesplieguesProduccion(
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta);

    @Query(value = """
            SELECT COALESCE(AVG(EXTRACT(EPOCH FROM (fecha_fin - fecha_inicio)) / 60.0), 0)
            FROM auditoria_entrenamiento_ia
            WHERE fecha_inicio IS NOT NULL AND fecha_fin IS NOT NULL
            AND fecha_fin >= :desde AND fecha_fin <= :hasta
            """, nativeQuery = true)
    Double promedioDuracionMinutos(
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta);
}
