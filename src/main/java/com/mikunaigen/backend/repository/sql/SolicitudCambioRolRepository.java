package com.mikunaigen.backend.repository.sql;

import com.mikunaigen.backend.model.sql.SolicitudCambioRol;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SolicitudCambioRolRepository extends JpaRepository<SolicitudCambioRol, Long> {

    Optional<SolicitudCambioRol> findFirstByUsuarioIdAndEstadoIgnoreCase(UUID usuarioId, String estado);

    List<SolicitudCambioRol> findByEstadoIgnoreCaseOrderByFechaSolicitudDesc(String estado);

    List<SolicitudCambioRol> findAllByOrderByFechaSolicitudDesc();
}
