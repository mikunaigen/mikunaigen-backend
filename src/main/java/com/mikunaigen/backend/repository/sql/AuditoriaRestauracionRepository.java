package com.mikunaigen.backend.repository.sql;

import com.mikunaigen.backend.model.sql.AuditoriaRestauracion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AuditoriaRestauracionRepository extends JpaRepository<AuditoriaRestauracion, Integer> {

    Optional<AuditoriaRestauracion> findFirstByEstadoOrderByFechaInicioDesc(String estado);

    @Query(value = """
            SELECT COALESCE(AVG(EXTRACT(EPOCH FROM (fecha_fin - fecha_inicio)) / 60.0), 0)
            FROM auditoria_restauraciones
            WHERE estado = 'exitosa'
            AND fecha_inicio IS NOT NULL AND fecha_fin IS NOT NULL
            AND fecha_fin >= :desde AND fecha_fin <= :hasta
            """, nativeQuery = true)
    Double promedioMttrMinutos(
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta);
}
