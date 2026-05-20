package com.mikunaigen.backend.repository.sql;

import com.mikunaigen.backend.model.sql.RestriccionIngrediente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RestriccionIngredienteRepository extends JpaRepository<RestriccionIngrediente, Long> {

    List<RestriccionIngrediente> findByUsuarioIdAndTipoIgnoreCase(UUID usuarioId, String tipo);

    void deleteByUsuarioIdAndTipoIgnoreCase(UUID usuarioId, String tipo);

    void deleteByUsuarioIdAndAlimentoId(UUID usuarioId, Integer alimentoId);
}
