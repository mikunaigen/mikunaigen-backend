package com.mikunaigen.backend.repository.sql;

import com.mikunaigen.backend.model.sql.InferenciaReceta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InferenciaRecetaRepository extends JpaRepository<InferenciaReceta, UUID> {

    @Query(value = """
            SELECT * FROM inferencias_recetas i
            WHERE i.usuario_id = :usuarioId
            AND i.input_parametros ->> 'sesionId' = :sesionId
            AND i.estado IN ('generada', 'guardada_historial')
            ORDER BY i.modo_optimizacion
            """, nativeQuery = true)
    List<InferenciaReceta> listarPorSesion(
            @Param("usuarioId") UUID usuarioId,
            @Param("sesionId") String sesionId
    );

    List<InferenciaReceta> findByUsuarioIdAndEstadoOrderByFechaGeneracionDesc(UUID usuarioId, String estado);

    List<InferenciaReceta> findByUsuarioIdAndEstadoOrderByFechaGeneracionAsc(UUID usuarioId, String estado);

    Optional<InferenciaReceta> findFirstByUsuarioIdAndEstadoOrderByFechaGeneracionAsc(UUID usuarioId, String estado);

    @Query("""
            SELECT COUNT(i) FROM InferenciaReceta i
            WHERE i.usuarioId = :usuarioId
            AND i.estado IN ('generada', 'guardada_historial')
            AND i.fechaGeneracion >= :desde
            """)
    long contarInferenciasMes(@Param("usuarioId") UUID usuarioId, @Param("desde") LocalDateTime desde);

    @Query("""
            SELECT COUNT(i) FROM InferenciaReceta i
            WHERE i.usuarioId = :usuarioId AND i.estado = 'guardada_historial'
            """)
    long contarHistorial(@Param("usuarioId") UUID usuarioId);

    @Query(value = """
            SELECT * FROM inferencias_recetas i
            WHERE i.usuario_id = :usuarioId
            AND i.estado IN ('generada', 'guardada_historial')
            AND i.input_parametros ->> 'hashParametros' = :hash
            ORDER BY i.fecha_generacion DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<InferenciaReceta> buscarCachePorHash(
            @Param("usuarioId") UUID usuarioId,
            @Param("hash") String hash
    );

    List<InferenciaReceta> findByUsuarioIdAndInputParametrosContainingOrderByFechaGeneracionDesc(
            UUID usuarioId, String sesionMarker
    );

    Optional<InferenciaReceta> findFirstByUsuarioIdAndEstadoInOrderByFechaGeneracionDesc(
            UUID usuarioId, List<String> estados);

    @Query(value = """
            SELECT i.* FROM inferencias_recetas i
            INNER JOIN usuarios u ON u.id = i.usuario_id
            WHERE i.estado = 'descartada_seguridad'
            AND (CAST(:desde AS timestamp) IS NULL OR i.fecha_generacion >= :desde)
            AND (CAST(:hasta AS timestamp) IS NULL OR i.fecha_generacion <= :hasta)
            AND (:componente IS NULL OR LOWER(COALESCE(i.componente_infractor, '')) LIKE LOWER(CONCAT('%', :componente, '%')))
            AND (:usuario IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', :usuario, '%'))
                 OR LOWER(COALESCE(u.nombres, '') || ' ' || COALESCE(u.apellidos, '')) LIKE LOWER(CONCAT('%', :usuario, '%')))
            ORDER BY i.fecha_generacion DESC
            """, nativeQuery = true)
    List<InferenciaReceta> listarDescartadasSeguridad(
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta,
            @Param("usuario") String usuario,
            @Param("componente") String componente
    );

    @Query(value = """
            SELECT to_char(fecha_generacion, 'YYYY-MM') AS mes, COUNT(*) AS total
            FROM inferencias_recetas
            WHERE estado = 'descartada_seguridad'
            GROUP BY to_char(fecha_generacion, 'YYYY-MM')
            ORDER BY mes DESC
            LIMIT 12
            """, nativeQuery = true)
    List<Object[]> contarDescartadasPorMesRaw();

    @Query(value = """
            SELECT i.usuario_id, CAST(i.fecha_generacion AS date) AS dia, COUNT(*) AS total
            FROM inferencias_recetas i
            WHERE i.estado = 'descartada_seguridad'
            GROUP BY i.usuario_id, CAST(i.fecha_generacion AS date)
            HAVING COUNT(*) > 5
            """, nativeQuery = true)
    List<Object[]> usuariosConAlertaDescartadasDia();
}
