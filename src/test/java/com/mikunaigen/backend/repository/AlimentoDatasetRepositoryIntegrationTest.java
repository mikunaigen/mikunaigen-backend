package com.mikunaigen.backend.repository;

import com.mikunaigen.backend.model.sql.AlimentoDataset;
import com.mikunaigen.backend.repository.sql.AlimentoDatasetRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("integration-test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=none")
@Sql(scripts = "/sql/h2-alimentos-dataset.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class AlimentoDatasetRepositoryIntegrationTest {

    @Autowired
    private AlimentoDatasetRepository alimentoDatasetRepository;

    @Test
    void guardarAlimento_persisteValoresNutricionales() {
        // HU-22: edición y guardado de alimento con valores numéricos no negativos
        AlimentoDataset alimento = new AlimentoDataset();
        alimento.setCodigoMinsa("A001");
        alimento.setNombre("Quinoa integrada");
        alimento.setCategoria("Cereales");
        alimento.setEnergiaKcal(new BigDecimal("368"));
        alimento.setCostoKgSoles(new BigDecimal("12.50"));
        alimento.setMesesDisponibilidad(new Integer[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12 });

        AlimentoDataset guardado = alimentoDatasetRepository.save(alimento);

        assertThat(guardado.getId()).isNotNull();
        assertThat(alimentoDatasetRepository.findByCodigoMinsa("A001")).isPresent();
        assertThat(guardado.getEnergiaKcal()).isEqualByComparingTo("368");
    }

    @Test
    void buscarPorNombre_filtraResultados() {
        // HU-22: búsqueda y filtrado de alimentos por nombre
        AlimentoDataset a = new AlimentoDataset();
        a.setNombre("Papa amarilla");
        a.setCategoria("Tubérculos");
        a.setEnergiaKcal(BigDecimal.TEN);
        a.setCostoKgSoles(BigDecimal.ONE);
        a.setMesesDisponibilidad(new Integer[] { 1, 2, 3 });
        alimentoDatasetRepository.save(a);

        List<AlimentoDataset> resultados = alimentoDatasetRepository.findByNombreContainingIgnoreCase("papa");

        assertThat(resultados).hasSize(1);
        assertThat(resultados.get(0).getNombre()).containsIgnoringCase("papa");
    }
}
