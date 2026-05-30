package com.mikunaigen.backend.repository.sql;

import com.mikunaigen.backend.model.sql.LimitesNormativos;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LimitesNormativosRepository extends JpaRepository<LimitesNormativos, Integer> {

    List<LimitesNormativos> findByNormativaOrderByNutrienteAsc(String normativa);

    List<LimitesNormativos> findAllByOrderByNormativaAscNutrienteAsc();
}
