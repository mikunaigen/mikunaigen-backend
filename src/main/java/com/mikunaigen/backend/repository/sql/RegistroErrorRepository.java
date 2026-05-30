package com.mikunaigen.backend.repository.sql;

import com.mikunaigen.backend.model.sql.RegistroError;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface RegistroErrorRepository extends JpaRepository<RegistroError, UUID> {

    @Query("""
            SELECT COUNT(r) FROM RegistroError r
            WHERE r.fechaRegistro >= :desde AND r.fechaRegistro <= :hasta
            """)
    long contarEntre(
            @Param("desde") OffsetDateTime desde,
            @Param("hasta") OffsetDateTime hasta);

    @Query(value = """
            SELECT CAST(r.fecha_registro AS date) AS dia, COUNT(*) AS total
            FROM registro_errores r
            WHERE r.fecha_registro >= :desde AND r.fecha_registro <= :hasta
            GROUP BY CAST(r.fecha_registro AS date)
            ORDER BY dia
            """, nativeQuery = true)
    List<Object[]> contarErroresPorDia(
            @Param("desde") OffsetDateTime desde,
            @Param("hasta") OffsetDateTime hasta);
}
