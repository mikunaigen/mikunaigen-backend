package com.mikunaigen.backend.repository.sql;

import com.mikunaigen.backend.model.sql.AuditoriaExportacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface AuditoriaExportacionRepository extends JpaRepository<AuditoriaExportacion, Long> {

    @Query("""
            SELECT COUNT(a) FROM AuditoriaExportacion a
            WHERE a.fechaExportacion >= :desde AND a.fechaExportacion <= :hasta
            """)
    long contarEntre(
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta);
}
