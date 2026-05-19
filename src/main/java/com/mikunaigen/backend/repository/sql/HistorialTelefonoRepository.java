package com.mikunaigen.backend.repository.sql;

import com.mikunaigen.backend.model.sql.HistorialTelefono;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface HistorialTelefonoRepository extends JpaRepository<HistorialTelefono, Long> {
    long countByUsuarioId(UUID usuarioId);
}
