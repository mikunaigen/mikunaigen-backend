package com.mikunaigen.backend.repository.sql;

import com.mikunaigen.backend.model.sql.ComposicionReceta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ComposicionRecetaRepository extends JpaRepository<ComposicionReceta, Long> {

    List<ComposicionReceta> findByInferenciaIdOrderByPorcentajeDesc(UUID inferenciaId);

    void deleteByInferenciaId(UUID inferenciaId);
}
