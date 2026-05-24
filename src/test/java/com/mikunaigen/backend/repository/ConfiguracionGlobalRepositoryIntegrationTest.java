package com.mikunaigen.backend.repository;

import com.mikunaigen.backend.model.sql.ConfiguracionGlobal;
import com.mikunaigen.backend.repository.sql.ConfiguracionGlobalRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("integration-test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ConfiguracionGlobalRepositoryIntegrationTest {

    @Autowired
    private ConfiguracionGlobalRepository configRepository;

    @Test
    void guardarConfiguracionPlataforma_persisteMediosPago() {
        // HU-34: guardado de configuración con medios de pago válidos
        ConfiguracionGlobal config = new ConfiguracionGlobal();
        config.setId(1);
        config.setNombrePlataforma("Mikunaigen Test");
        config.setTelefonoContacto("987654321");
        config.setNumeroYape("912345678");
        config.setNumeroPlin("923456789");
        config.setBancoNombre("BCP");
        config.setCuentaBancaria("1234567890");
        config.setCci("00112233445566778899");
        config.setTerminosCondiciones("Términos de prueba para integración.");
        config.setLogoBytea(new byte[] { 1, 2, 3 });
        config.setSetupCompletado(true);

        configRepository.save(config);

        ConfiguracionGlobal recuperada = configRepository.findById(1).orElseThrow();
        assertThat(recuperada.getNumeroYape()).isEqualTo("912345678");
        assertThat(recuperada.getNumeroPlin()).isEqualTo("923456789");
        assertThat(recuperada.getCuentaBancaria()).isEqualTo("1234567890");
        assertThat(recuperada.isConfiguracionCompleta()).isTrue();
    }
}
