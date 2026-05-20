package com.mikunaigen.backend.repository.sql;

import com.mikunaigen.backend.model.sql.AlimentoDataset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AlimentoDatasetRepository extends JpaRepository<AlimentoDataset, Integer> {

    List<AlimentoDataset> findByNombreContainingIgnoreCase(String nombre);

    List<AlimentoDataset> findByCategoriaIgnoreCase(String categoria);

    Optional<AlimentoDataset> findByCodigoMinsa(String codigoMinsa);

    Optional<AlimentoDataset> findByNombreIgnoreCase(String nombre);

    @Query("SELECT a FROM AlimentoDataset a WHERE LOWER(a.nombre) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(a.categoria) LIKE LOWER(CONCAT('%', :q, '%'))")
    List<AlimentoDataset> buscar(@Param("q") String q);
}
