package com.mikunaigen.backend.repository.sql;

import com.mikunaigen.backend.model.sql.AuditoriaAcceso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface AuditoriaAccesoRepository extends JpaRepository<AuditoriaAcceso, UUID> {

    @Query(value = """
            SELECT COUNT(DISTINCT a.usuario_id)
            FROM auditoria_accesos a
            INNER JOIN usuarios u ON u.id = a.usuario_id
            INNER JOIN roles r ON r.id = u.rol_id
            WHERE r.nombre IN ('estudiante', 'emprendedor', 'nutricionista')
            AND u.estado <> 'suspendido'
            AND a.fecha_acceso >= :desde
            AND a.fecha_acceso <= :hasta
            """, nativeQuery = true)
    long contarUsuariosClientesDistintos(
            @Param("desde") OffsetDateTime desde,
            @Param("hasta") OffsetDateTime hasta);

    @Query(value = """
            SELECT CAST(a.fecha_acceso AS date) AS dia, COUNT(*) AS total
            FROM auditoria_accesos a
            WHERE a.fecha_acceso >= :desde AND a.fecha_acceso <= :hasta
            GROUP BY CAST(a.fecha_acceso AS date)
            ORDER BY dia
            """, nativeQuery = true)
    List<Object[]> contarAccesosPorDia(
            @Param("desde") OffsetDateTime desde,
            @Param("hasta") OffsetDateTime hasta);
}
