package com.mikunaigen.backend.service;

import com.mikunaigen.backend.repository.sql.AlimentoDatasetRepository;
import com.mikunaigen.backend.repository.sql.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAlimentoDatasetServiceTest {

    private static final long MAX_CSV_BYTES = 5L * 1024 * 1024;

    @Mock
    private AlimentoDatasetRepository alimentoRepo;

    @Mock
    private UserRepository userRepo;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private MultipartFile archivo;

    private AdminAlimentoDatasetService service;

    @BeforeEach
    void setUp() {
        service = new AdminAlimentoDatasetService(alimentoRepo, userRepo, jdbcTemplate, transactionManager);
    }

    @Test
    void importarCsv_rechazaArchivoMayorA5Mb() {
        // HU-22
        UUID adminId = UUID.randomUUID();
        when(archivo.isEmpty()).thenReturn(false);
        when(archivo.getSize()).thenReturn(MAX_CSV_BYTES + 1);

        assertThatThrownBy(() -> service.importarCsv(archivo, adminId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("5 MB");
    }

    @Test
    void guardarLote_rechazaValoresNumericosNegativos() {
        // HU-22
        UUID adminId = UUID.randomUUID();
        Map<String, Object> item = Map.of(
                "nombre", "Arroz integral",
                "grupo", "Cereales",
                "energia_kcal", "-1",
                "costo_kg_soles", "3.50"
        );

        assertThatThrownBy(() -> service.guardarLote(List.of(item), adminId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("energia_kcal");
    }

    @Test
    void estado_devuelveVacioTrueCuandoCountEsCero() {
        // HU-22
        when(alimentoRepo.count()).thenReturn(0L);

        Map<String, Object> resultado = service.estado();

        assertThat(resultado.get("vacio")).isEqualTo(true);
        assertThat(resultado.get("total")).isEqualTo(0L);
    }
}
