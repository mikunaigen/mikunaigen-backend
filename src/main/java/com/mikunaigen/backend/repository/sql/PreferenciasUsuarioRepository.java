package com.mikunaigen.backend.repository.sql;

import com.mikunaigen.backend.model.sql.PreferenciasUsuario;

import java.util.UUID;

public interface PreferenciasUsuarioRepository extends org.springframework.data.jpa.repository.JpaRepository<PreferenciasUsuario, UUID> {
}
