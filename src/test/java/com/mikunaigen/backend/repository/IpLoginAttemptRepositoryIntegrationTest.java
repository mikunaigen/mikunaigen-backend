package com.mikunaigen.backend.repository;

import com.mikunaigen.backend.model.sql.IpLoginAttempt;
import com.mikunaigen.backend.repository.sql.IpLoginAttemptRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("integration-test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class IpLoginAttemptRepositoryIntegrationTest {

    @Autowired
    private IpLoginAttemptRepository ipLoginAttemptRepository;

    @Test
    void tresIntentosFallidos_registraBloqueoIp() {
        // HU-04: tres intentos fallidos registran IP bloqueada por una hora
        IpLoginAttempt intento = new IpLoginAttempt();
        intento.setIpAddress("192.168.10.20");
        intento.setFailedAttempts(3);
        intento.setBlockedUntil(LocalDateTime.now().plusHours(1));
        ipLoginAttemptRepository.save(intento);

        Optional<IpLoginAttempt> guardado = ipLoginAttemptRepository.findByIpAddress("192.168.10.20");

        assertThat(guardado).isPresent();
        assertThat(guardado.get().getFailedAttempts()).isEqualTo(3);
        assertThat(guardado.get().getBlockedUntil()).isAfter(LocalDateTime.now());
    }

    @Test
    void bloqueoExpirado_reiniciaContador() {
        // HU-04: tras finalizar bloqueo se reinicia contador de intentos
        IpLoginAttempt intento = new IpLoginAttempt();
        intento.setIpAddress("10.0.0.50");
        intento.setFailedAttempts(0);
        intento.setBlockedUntil(null);
        intento.setLastFailedAt(null);
        ipLoginAttemptRepository.save(intento);

        Optional<IpLoginAttempt> guardado = ipLoginAttemptRepository.findByIpAddress("10.0.0.50");

        assertThat(guardado).isPresent();
        assertThat(guardado.get().getFailedAttempts()).isZero();
        assertThat(guardado.get().getBlockedUntil()).isNull();
    }
}
